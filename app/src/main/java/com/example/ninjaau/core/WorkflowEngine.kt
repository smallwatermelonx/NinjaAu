package com.example.ninjaau.core

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 悬赏自动化流水线引擎
 *
 * 基于 GamePhase 状态机的循环编排，每个 phase 是独立的 suspend 函数，
 * 返回下一个要进入的 phase。顶层 runLoop 负责异常兜底与恢复计数。
 *
 * Phase 流转:
 *   IDLE → SCANNING → JOINING → VALIDATING → WAITING → BATTLE → SETTLEMENT → SCANNING(下一轮)
 *                     ↑__________|                |________↑
 *                    失败→IDLE                  超时→IDLE
 */
class WorkflowEngine(
    private val context: Context, private val postLog: ((String) -> Unit)? = null
) {
    private val TAG = "WorkflowEngine"

    private val capture = ScreenCapture.getInstance(context)
    private val detector = SceneDetector(context)
    private val accessibility get() = NinjaAccessibilityService.getInstance()

    /** 同时输出到 logcat 和 UI 日志面板 */
    private fun log(msg: String) {
        LogUtil.i(TAG, msg)
        postLog?.invoke(msg)
    }

    companion object {
        private const val MAX_RECOVERY = 3
        private const val NAVIGATE_RETRIES = 15
        private const val JOIN_RETRIES = 10
        private const val VALIDATE_RETRIES = 10
        private const val SCAN_RETRIES = 20
        private const val BATTLE_CHECK_MS = 1500L
        private const val POST_CLICK_DELAY = 1500L
        private const val WAIT_TIMEOUT_MS = 35_000L
    }

    // ──────────────────────────────────────────────
    //  公开入口
    // ──────────────────────────────────────────────

    /**
     * 运行整个悬赏循环，返回是否所有勾选等级均已完成。
     * @param configs 用户勾选的悬赏配置列表
     */
    suspend fun runLoop(configs: List<BountyConfig>): Boolean {
        val ctx = buildContext(configs)
        var recoveryCount = 0

        while (coroutineContext.isActive && ctx.currentPhase != GamePhase.DONE && recoveryCount < MAX_RECOVERY) {
            try {
                val phaseName = ctx.currentPhase.name
                log("▶ Phase: $phaseName 开始")
                val next = when (ctx.currentPhase) {
                    GamePhase.IDLE, GamePhase.SCANNING -> phaseNavigateAndScan(ctx)
                    GamePhase.JOINING -> phaseJoin(ctx)
                    GamePhase.VALIDATING -> phaseValidate(ctx)
                    GamePhase.WAITING -> phaseWait(ctx)
                    GamePhase.BATTLE -> phaseBattle(ctx)
                    GamePhase.SETTLEMENT -> phaseClaim(ctx)
                    GamePhase.RECOVERY -> {
                        delay(1500); GamePhase.IDLE
                    }

                    GamePhase.DONE -> GamePhase.DONE
                }
                log("▶ Phase $phaseName → $next")
                ctx.currentPhase = next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("❗ Pipeline 异常于 ${ctx.currentPhase}: ${e.message}")
                LogUtil.e(TAG, "Pipeline 异常于 ${ctx.currentPhase}", e)
                recoveryCount++
                ctx.currentPhase = GamePhase.RECOVERY
            }
        }

        LogUtil.i(TAG, "流水线结束: allCompleted=${ctx.allCompleted}, recoveryCount=$recoveryCount")
        log("流水线结束: allCompleted=${ctx.allCompleted}, recoveryCount=$recoveryCount")
        return ctx.allCompleted
    }

    // ──────────────────────────────────────────────
    //  Phase 实现
    // ──────────────────────────────────────────────

    /**
     * Phase 1: 导航 + 扫描
     * 从任意位置导航到招募列表，并扫描勾选的悬赏等级图标。
     * 匹配到等级 → 转入 JOINING；否则保持 SCANNING 重试。
     */
    private suspend fun phaseNavigateAndScan(ctx: GameContext): GamePhase {
        val remaining = remainingGrades(ctx)
        if (remaining.isEmpty()) return GamePhase.DONE

        log("待完成等级: ${remaining.joinToString { it.displayName }}")

        // 1) 先确保处于招募列表界面
        val arrived = ensureRecruitView()
        if (!arrived) {
            log("⚠ 无法到达招募列表，重试")
            return GamePhase.SCANNING
        }

        // 2) 扫描等级
        repeat(SCAN_RETRIES) {
            if (!coroutineContext.isActive) return GamePhase.DONE

            val screen = captureBitmap() ?: return@repeat delay(500)

            try {
                val current = detector.detect(screen)
                log("扫描中... 当前界面=$current")

                if (current == ScreenState.RECRUIT_TAB || current == ScreenState.CHAT_ICON) {
                    log("已离开招募界面($current)，重新导航")
                    return GamePhase.SCANNING
                }

                val match = detector.matchAnyGrade(screen, remaining)
                if (match != null) {
                    val (grade, coord) = match
                    ctx.currentBounty = grade
                    log("✅ 匹配到悬赏 ${grade.displayName}，坐标=${coord}")

                    click(coord)
                    delay(POST_CLICK_DELAY)
                    return GamePhase.JOINING
                }

                // 刷新列表：重新点击招募 tab
                val tabCoord = detector.matchTemplate(screen, ScreenState.RECRUIT_TAB)
                if (tabCoord != null) {
                    click(tabCoord)
                    delay(500)
                }
            } finally {
                screen.recycle()
            }

            delay(1500)
        }

        log("⚠ 扫描重试耗尽，回到 IDLE")
        return GamePhase.IDLE
    }

    /**
     * Phase 2: 加入队伍
     * 点击 JOIN_BUTTON，等待进入队伍房间。
     */
    private suspend fun phaseJoin(ctx: GameContext): GamePhase {
        val targetGrade = ctx.currentBounty
        log("加入队伍 Phase，目标=${targetGrade?.displayName}")

        repeat(JOIN_RETRIES) {
            if (!coroutineContext.isActive) return GamePhase.DONE

            val check = captureBitmap() ?: return@repeat delay(500)
            try {
                val state = detector.detect(check)
                if (state == ScreenState.READY_BUTTON || state == ScreenState.TEAM_ROOM || state == ScreenState.WAITING_SCREEN) {
                    log("✅ 已在队伍房间: $state")
                    return GamePhase.VALIDATING
                }
                if (state == ScreenState.TEAM_FULL) {
                    log("⚠ 队伍已满，下次扫描跳过此队")
                }
            } finally {
                check.recycle()
            }

            val screen = captureBitmap() ?: return@repeat delay(500)
            try {
                val joinCoord = detector.matchTemplate(screen, ScreenState.JOIN_BUTTON)
                if (joinCoord != null) {
                    log("点击加入队伍, 坐标=$joinCoord")
                    click(joinCoord)
                    delay(POST_CLICK_DELAY)
                    repeat(5) {
                        val verify = captureBitmap() ?: return@repeat delay(300)
                        try {
                            val vs = detector.detect(verify)
                            if (vs == ScreenState.READY_BUTTON || vs == ScreenState.TEAM_ROOM || vs == ScreenState.WAITING_SCREEN) {
                                return GamePhase.VALIDATING
                            }
                        } finally {
                            verify.recycle()
                        }
                        delay(500)
                    }
                } else {
                    log("⚠ 未找到 JOIN 按钮，回到扫描")
                    return GamePhase.SCANNING
                }
            } finally {
                screen.recycle()
            }

            delay(800)
        }

        log("⚠ 加入队伍重试耗尽")
        return GamePhase.SCANNING
    }

    /**
     * Phase 3: 队伍校验 + 准备
     * 校验：
     *   a) 是否已完成标记 → 退出
     *   b) 等级是否匹配 → 仅警告，不阻断（用户允许进入任何勾选等级）
     * 通过 → 点击准备
     */
    private suspend fun phaseValidate(ctx: GameContext): GamePhase {
        val targetGrade = ctx.currentBounty ?: return GamePhase.SCANNING
        log("校验 Phase，目标=${targetGrade.displayName}(lv${targetGrade.level})")

        repeat(VALIDATE_RETRIES) {
            if (!coroutineContext.isActive) return GamePhase.DONE

            val screen = captureBitmap() ?: return@repeat delay(500)
            try {
                val state = detector.detect(screen)
                log("校验中... 界面=$state")

                if (state == ScreenState.TEAM_COMPLETED) {
                    log("${targetGrade.displayName} 已完成，退出队伍")
                    exitTeam()
                    ctx.currentBounty = null
                    return GamePhase.IDLE
                }

                if (state == ScreenState.TEAM_FULL) {
                    log("队伍已满，退出重新扫描")
                    exitTeam()
                    ctx.currentBounty = null
                    return GamePhase.IDLE
                }

                // 等级校验（仅警告，不阻断 — 模板可能不精确）
                val levelPath = targetGrade.levelIconPath()
                if (levelPath != null) {
                    val levelVisible = detector.matchLevelIcon(screen, targetGrade) != null
                    if (levelVisible) {
                        log("✅ 建议等级匹配 (lv${targetGrade.level})")
                    } else {
                        log("⚠ 建议等级模板未匹配(lv${targetGrade.level})，继续执行")
                    }
                } else {
                    log("⚠ 暂无 lv${targetGrade.level} 等级模板，跳过等级校验")
                }

                // 检测准备按钮
                if (state == ScreenState.READY_BUTTON) {
                    val readyCoord = detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                    if (readyCoord != null) {
                        click(readyCoord)
                        log("✅ 已点击准备")
                        delay(POST_CLICK_DELAY)
                        return GamePhase.WAITING
                    }
                }

                if (state == ScreenState.TEAM_ROOM) {
                    delay(500); return@repeat
                }
                if (state == ScreenState.WAITING_SCREEN) {
                    return GamePhase.WAITING
                }

                log("⚠ 不在队伍房间 (state=$state)")
                return GamePhase.SCANNING
            } finally {
                screen.recycle()
            }
            delay(800)
        }

        log("⚠ 校验重试耗尽")
        return GamePhase.SCANNING
    }

    /**
     * Phase 4: 等待战斗
     * 准备后等待倒计时结束战斗开始，超时退出。
     */
    private suspend fun phaseWait(ctx: GameContext): GamePhase {
        log("等待战斗 Phase")
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < WAIT_TIMEOUT_MS) {
            if (!coroutineContext.isActive) return GamePhase.DONE

            val screen = captureBitmap()
            if (screen == null) {
                delay(500); continue
            }

            try {
                val state = detector.detect(screen)

                if (state == ScreenState.BATTLE_WARNING) {
                    log("✅ 检测到战斗开始: $state")
                    return GamePhase.BATTLE
                }

                if (state in setOf(
                        ScreenState.BATTLE_ACTIVE,
                        ScreenState.ULTIMATE_SKILL,
                        ScreenState.SETTLEMENT_POPUP
                    )
                ) {
                    log("✅ 已在战斗中: $state")
                    return GamePhase.BATTLE
                }

                if (state == ScreenState.WAITING_SCREEN) {
                    delay(BATTLE_CHECK_MS); continue
                }

                if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                    log("⚠ 等待期间回到大厅，本轮不计入完成")
                    return GamePhase.IDLE
                }

                if (state == ScreenState.UNKNOWN) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    log("等待中... 界面无法识别(${elapsed}s)")
                }
            } finally {
                screen.recycle()
            }
            delay(BATTLE_CHECK_MS)
        }

        log("⚠ 等待战斗超时 ${WAIT_TIMEOUT_MS}ms")
        exitTeam()
        return GamePhase.IDLE
    }

    /**
     * Phase 5: 战斗
     * 检测 Boss 血条/倒计时/大招图标 → 自动释放技能。
     * 持续到结算弹窗出现。
     */
    private suspend fun phaseBattle(ctx: GameContext): GamePhase {
        log("战斗 Phase")
        var lastSkillTime = 0L
        val skillCooldown = 3000L

        while (coroutineContext.isActive) {
            val screen = captureBitmap()
            if (screen == null) {
                delay(500); continue
            }

            try {
                val state = detector.detect(screen)

                if (state == ScreenState.SETTLEMENT_POPUP) {
                    log("✅ 检测到结算弹窗")
                    return GamePhase.SETTLEMENT
                }

                if (state == ScreenState.CONFIRM_BUTTON) {
                    log("✅ 检测到确定按钮")
                    return GamePhase.SETTLEMENT
                }

                if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                    log("⚠ 战斗异常回到大厅")
                    return GamePhase.IDLE
                }

                val now = System.currentTimeMillis()
                if (now - lastSkillTime >= skillCooldown) {
                    val shouldUseSkill =
                        detector.matchTemplate(screen, ScreenState.ULTIMATE_SKILL) != null
                    if (shouldUseSkill) {
                        val didUse = useSkills(screen)
                        if (didUse) lastSkillTime = now
                    }
                }
            } finally {
                screen.recycle()
            }

            delay(BATTLE_CHECK_MS)
        }

        return GamePhase.DONE
    }

    /**
     * Phase 6: 结算领奖
     * 点击弹窗外围 → 点击确定 → 回到大厅 → 累加完成次数。
     */
    private suspend fun phaseClaim(ctx: GameContext): GamePhase {
        val grade = ctx.currentBounty
        log("结算 Phase，悬赏=${grade?.displayName}")

        clickOutside()
        delay(1000)

        for (i in 0 until 10) {
            if (!coroutineContext.isActive) return GamePhase.DONE
            val screen = captureBitmap() ?: continue
            try {
                val state = detector.detect(screen)
                val confirmCoord = detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)

                if (confirmCoord != null) {
                    log("点击确定按钮, 坐标=$confirmCoord")
                    click(confirmCoord)
                    delay(POST_CLICK_DELAY)
                }

                if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                    log("已回到大厅")
                    break
                }
            } finally {
                screen.recycle()
            }
            delay(800)
        }

        if (grade != null) {
            val count = ctx.runCounts[grade] ?: 0
            ctx.runCounts[grade] = count + 1
            ctx.totalCycles++
            log("✅ ${grade.displayName} 完成 ${count + 1}/${grade.defaultRuns}")
        }

        ctx.currentBounty = null

        if (ctx.allCompleted) {
            log("🎉 所有勾选悬赏已完成！")
            return GamePhase.DONE
        }

        return GamePhase.IDLE
    }

    // ──────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────

    /** 从 configs 构建初始 GameContext */
    private fun buildContext(configs: List<BountyConfig>): GameContext {
        val enabled = configs.filter { it.enabled }
        return GameContext(
            currentPhase = GamePhase.IDLE,
            activeGrades = enabled.map { it.grade },
            runCounts = enabled.associate { it.grade to 0 }.toMutableMap()
        )
    }

    /** 剩余待完成的等级列表 */
    private fun remainingGrades(ctx: GameContext): List<BountyGrade> {
        return ctx.activeGrades.filter { g -> (ctx.runCounts[g] ?: 0) < g.defaultRuns }
    }

    /** 确保位于招募列表视图 */
    private suspend fun ensureRecruitView(): Boolean {
        repeat(NAVIGATE_RETRIES) {
            if (!coroutineContext.isActive) return false
            val screen = captureBitmap() ?: return@repeat delay(500)
            try {
                val (state, coord) = detector.detectWithCoord(screen)
                when (state) {
                    ScreenState.UNKNOWN -> {
                        log("导航中... 界面无法识别")
                        delay(800)
                    }

                    ScreenState.CHAT_ICON -> {
                        // 聊天按钮可见说明用户在大厅，点击打开聊天
                        log("检测到聊天按钮，点击打开聊天面板")
                        click(coord!!)
                        delay(POST_CLICK_DELAY)
                    }

                    ScreenState.RECRUIT_TAB -> {
                        log("检测到招募页签，点击进入组队列表")
                        click(coord!!)
                        delay(1000)
                        return true
                    }

                    ScreenState.RECRUIT_LIST, ScreenState.JOIN_BUTTON -> {
                        log("已在招募列表界面")
                        return true
                    }

                    ScreenState.BACK_BUTTON -> {
                        log("检测到返回按钮，点击返回")
                        click(coord!!)
                        delay(800)
                    }

                    else -> {
                        log("导航中... 当前界面=$state")
                        delay(800)
                    }
                }
            } finally {
                screen.recycle()
            }
        }
        log("⚠ 导航重试耗尽，无法到达招募列表")
        return false
    }

    /** 退出队伍（点返回直到回到大厅） */
    private suspend fun exitTeam() {
        log("退出队伍...")
        var confirmAttempts = 0
        repeat(10) {
            val screen = captureBitmap() ?: return@repeat delay(500)
            try {
                val state = detector.detect(screen)
                if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                    log("已回到大厅")
                    return
                }

                // 退出确认弹窗（需要用户提供 EXIT_CONFIRM 模板后生效）
                if (state == ScreenState.EXIT_CONFIRM) {
                    val confirmCoord = detector.matchTemplate(screen, ScreenState.EXIT_CONFIRM)
                    if (confirmCoord != null) {
                        log("检测到退出确认弹窗，点击确认")
                        click(confirmCoord)
                        delay(1000)
                        confirmAttempts++
                        if (confirmAttempts > 3) {
                            clickOutside()
                            delay(800)
                        }
                        return@repeat
                    }
                }

                val backCoord = detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    click(backCoord)
                    delay(1200)
                } else {
                    if (state == ScreenState.UNKNOWN) {
                        // 无返回按钮且界面无法识别 → 可能弹窗遮挡，点屏幕下方
                        clickOutside()
                        delay(800)
                    } else {
                        delay(500)
                    }
                }
            } finally {
                screen.recycle()
            }
        }
        log("退出队伍完成")
    }

    /** 释放技能 */
    private suspend fun useSkills(screen: Bitmap): Boolean {
        val ultimate = detector.matchTemplate(screen, ScreenState.ULTIMATE_SKILL)
        if (ultimate != null) {
            click(ultimate)
            log("释放大招")
            delay(200)
            return true
        }
        return false
    }

    /** 点击屏幕中央偏下位置（关闭弹窗通用） */
    private suspend fun clickOutside() {
        accessibility?.clickAt(540f, 1200f)
        delay(300)
    }

    /** 截图 - 循环直到成功或取消 */
    private suspend fun captureBitmap(): Bitmap? {
        repeat(3) {
            val bmp = capture.capture()
            if (bmp != null) return bmp
            delay(300)
        }
        log("⚠ 截图失败")
        return null
    }

    /** 无障碍点击 */
    private fun click(coord: Pair<Float, Float>) {
        accessibility?.clickAt(coord.first, coord.second)
    }
}

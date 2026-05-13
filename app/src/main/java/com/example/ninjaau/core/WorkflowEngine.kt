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
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * 悬赏自动化流水线引擎 v2
 *
 * 节点流程（对应业务规范）:
 *   节点1(聊天) → 节点2(招募tab) → 节点3(抢悬赏,100ms) → 节点4(队伍→准备→等战斗,15s)
 *   → 节点5(战斗) → 节点6(结算领奖) → 回到节点1
 *
 * 识别周期: 普通 1s / 招募列表 100ms
 * 异常兜底: 3次连续失败 → 整体判定 → 3次整体判定失败 → 停止脚本并写日志
 */
class WorkflowEngine(
    private val context: Context, private val postLog: ((String) -> Unit)? = null
) {
    private val TAG = "WorkflowEngine"

    private val capture = ScreenCapture.getInstance(context)
    private val detector = SceneDetector(context)
    private val accessibility get() = NinjaAccessibilityService.getInstance()
    private var globalFailCount = 0

    private fun log(msg: String) {
        LogUtil.i(TAG, msg)
        postLog?.invoke(msg)
    }

    companion object {
        private const val MAX_GLOBAL_FAIL = 3
        private const val NAVIGATE_RETRIES = 15
        private const val POST_CLICK_DELAY = 1000L
        private const val NORMAL_INTERVAL_MS = 1000L
        private const val FAST_INTERVAL_MS = 100L
        private const val LINEAR_MAX_MISS = 3
        private const val LINEAR_MAX_LOOP = 300
        private const val WAIT_BATTLE_TIMEOUT_MS = 15_000L
        private const val SCAN_REFRESH_CYCLES = 3
        private const val SCAN_MAX_REFRESH = 5
    }

    // ══════════════════════════════════════════
    //  公开入口
    // ══════════════════════════════════════════

    suspend fun runLoop(configs: List<BountyConfig>): Boolean {
        val ctx = buildContext(configs)
        globalFailCount = 0

        while (coroutineContext.isActive &&
            ctx.currentPhase != GamePhase.DONE &&
            globalFailCount < MAX_GLOBAL_FAIL
        ) {
            try {
                val phaseName = ctx.currentPhase.name
                log("▶ Phase: $phaseName 开始")
                val next = when (ctx.currentPhase) {
                    GamePhase.IDLE, GamePhase.SCANNING -> phaseNavigateAndScan(ctx)
                    GamePhase.JOINING -> phaseJoin(ctx)
                    GamePhase.VALIDATING, GamePhase.WAITING -> phaseValidate(ctx)
                    GamePhase.BATTLE -> phaseBattle(ctx)
                    GamePhase.SETTLEMENT -> phaseClaim(ctx)
                    GamePhase.RECOVERY -> { delay(1500); GamePhase.IDLE }
                    GamePhase.DONE -> GamePhase.DONE
                }
                log("▶ Phase $phaseName → $next")
                ctx.currentPhase = next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("❗ Pipeline 异常于 ${ctx.currentPhase}: ${e.message}")
                LogUtil.e(TAG, "Pipeline 异常", e)
                globalFailCount++
                log("⚠ 整体判定失败 ($globalFailCount/$MAX_GLOBAL_FAIL)")
                ctx.currentPhase = GamePhase.RECOVERY
            }
        }

        val allDone = ctx.allCompleted
        if (globalFailCount >= MAX_GLOBAL_FAIL) {
            val msg = "❌ 整体判定3次失败，脚本停止"
            log(msg)
            LogUtil.e(TAG, msg)
            writeCrashLog(ctx)
        }
        LogUtil.i(TAG, "流水线结束: allCompleted=$allDone, globalFailCount=$globalFailCount")
        log("流水线结束: allCompleted=$allDone, recoveryCount=$globalFailCount")
        return allDone
    }

    // ══════════════════════════════════════════
    //  节点 1~4：扫描 → 加入 → 准备（单循环）
    // ══════════════════════════════════════════

    private suspend fun phaseNavigateAndScan(ctx: GameContext): GamePhase {
        val remaining = remainingGrades(ctx)
        if (remaining.isEmpty()) return GamePhase.DONE
        log("待完成等级: ${remaining.joinToString { it.displayName }}")

        // ── 节点1+2：导航到招募列表 ──
        val arrived = ensureRecruitView()
        if (!arrived) {
            log("⚠ 无法到达招募列表")
            return GamePhase.SCANNING
        }

        // ── 节点3：快速扫描（100ms），单循环处理 匹配→加入→准备 ──
        log("🔍 进入100ms快速扫描模式")
        var refreshCount = 0
        var noMatchCycles = 0
        ctx.currentBounty = null  // 确保以干净状态进入扫描

        while (coroutineContext.isActive) {
            val screen = captureBitmap()
            if (screen == null) { delay(FAST_INTERVAL_MS); continue }
            try {
                // ═══ 异常检测（最高优先级） ═══
                if (detector.matchTemplate(screen, ScreenState.RECRUIT_EXCEPTION) != null) {
                    if (ctx.currentBounty == null) {
                        log("⚠ 招募列表异常，页签刷新")
                        performTabRefresh(screen)
                        noMatchCycles = 0
                        continue
                    }
                }

                // ═══ 已选择悬赏 → 正在加入 / 已在队伍 ═══
                if (ctx.currentBounty != null) {
                    val joinCoord = detector.matchTemplate(screen, ScreenState.JOIN_BUTTON)
                    if (joinCoord != null) {
                        click(joinCoord)
                        log("🔄 再次点击加入 ${ctx.currentBounty?.displayName}")
                        delay(POST_CLICK_DELAY)
                        continue
                    }

                    // JOIN_BUTTON 消失 → 进入队伍房间
                    val readyCoord = detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                    if (readyCoord != null) {
                        click(readyCoord)
                        log("✅ 已在队伍房间，点击准备")
                        delay(POST_CLICK_DELAY)
                        return GamePhase.VALIDATING
                    }

                    // 异常弹窗检查
                    if (detector.matchTemplate(screen, ScreenState.DAILY_LIMIT) != null ||
                        detector.matchTemplate(screen, ScreenState.TEAM_COMPLETED) != null ||
                        detector.matchTemplate(screen, ScreenState.TEAM_FULL) != null
                    ) {
                        log("⚠ 加入失败，退出队伍")
                        exitTeam()
                        ctx.currentBounty = null
                        refreshCount = 0
                        noMatchCycles = 0
                        continue
                    }

                    // 过渡状态 → 慢速等待房间加载
                    delay(NORMAL_INTERVAL_MS)
                    continue
                }

                // ═══ 普通扫描模式 ═══

                // READY_BUTTON（被邀请直达队伍房间）
                val readyCoord = detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                if (readyCoord != null) {
                    click(readyCoord)
                    log("✅ 已在队伍房间（被邀请），点击准备")
                    delay(POST_CLICK_DELAY)
                    return GamePhase.VALIDATING
                }

                // SCOPE_RECRUIT 检查
                val (currentState, _) = detector.detectForPhase(screen, SceneDetector.SCOPE_RECRUIT)
                when (currentState) {
                    ScreenState.CHAT_ICON, ScreenState.RECRUIT_TAB -> {
                        log("已离开招募界面($currentState)，重新导航")
                        return GamePhase.SCANNING
                    }
                    ScreenState.UNKNOWN -> {
                        log("⚠ 招募界面无法识别，页签刷新")
                        performTabRefresh(screen)
                        noMatchCycles = 0
                        continue
                    }
                    else -> {}
                }

                // 等级匹配
                // TODO NSS+.png / NS.png / NA.png — 三种活动悬赏等级图标模板缺失
                val match = detector.matchAnyGrade(screen, remaining)
                if (match != null) {
                    val (grade, coord) = match
                    ctx.currentBounty = grade
                    log("✅ 匹配到悬赏 ${grade.displayName}，坐标=${coord}")

                    val joinCoord = detector.matchTemplate(screen, ScreenState.JOIN_BUTTON)
                    if (joinCoord != null) {
                        click(joinCoord)
                        log("✅ 点击加入队伍")
                        delay(POST_CLICK_DELAY)
                    } else {
                        click(coord)
                        delay(POST_CLICK_DELAY)
                    }
                    continue
                }

                // 未匹配到 → 计数，准备刷新
                noMatchCycles++
                if (noMatchCycles >= SCAN_REFRESH_CYCLES) {
                    if (refreshCount >= SCAN_MAX_REFRESH) {
                        log("⚠ 刷新${SCAN_MAX_REFRESH}次仍无匹配，整体判定")
                        ctx.currentBounty = null
                        return GamePhase.SCANNING
                    }
                    refreshCount++
                    log("🔄 未匹配(${noMatchCycles}周期)，页签刷新 第${refreshCount}次")
                    noMatchCycles = 0
                    performTabRefresh(screen)
                }
            } finally {
                screen.recycle()
            }
            delay(FAST_INTERVAL_MS)
        }

        return GamePhase.DONE
    }

    // ══════════════════════════════════════════
    //  节点3→4：点击加入 → 进入队伍房间
    // ══════════════════════════════════════════

    private suspend fun phaseJoin(ctx: GameContext): GamePhase {
        log("加入队伍 Phase")
        var loopCount = 0

        while (coroutineContext.isActive && loopCount < LINEAR_MAX_LOOP) {
            loopCount++
            val screen = captureBitmap()
            if (screen == null) { delay(FAST_INTERVAL_MS); continue }
            try {
                // 优先级1: 异常 → 页签刷新
                if (detector.matchTemplate(screen, ScreenState.RECRUIT_EXCEPTION) != null) {
                    log("⚠ 加入阶段招募列表异常，页签刷新")
                    performTabRefresh(screen)
                    continue
                }

                // 优先级2: 已在队伍房间 → 直接准备
                val readyCoord = detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                if (readyCoord != null) {
                    log("✅ 已在队伍房间，点击准备")
                    click(readyCoord)
                    delay(POST_CLICK_DELAY)
                    return GamePhase.VALIDATING
                }

                // 优先级3: 点击加入队伍
                val joinCoord = detector.matchTemplate(screen, ScreenState.JOIN_BUTTON)
                if (joinCoord != null) {
                    log("点击加入队伍")
                    click(joinCoord)
                    delay(POST_CLICK_DELAY)
                    // 点击加入后下一轮循环会检测到 READY_BUTTON
                }
            } finally {
                screen.recycle()
            }
            delay(FAST_INTERVAL_MS)
        }

        log("⚠ 加入队伍超时")
        return GamePhase.SCANNING
    }

    // ══════════════════════════════════════════
    //  节点4：队伍房间 → 准备 → 等待战斗（15s超时）
    // ══════════════════════════════════════════

    private suspend fun phaseValidate(ctx: GameContext): GamePhase {
        val targetGrade = ctx.currentBounty ?: return GamePhase.SCANNING
        log("校验 Phase，目标=${targetGrade.displayName}(lv${targetGrade.level})")

        // ── 阶段1: 准备（1s普通模式） ──
        var readyFound = false
        for (i in 0 until 15) {
            if (!coroutineContext.isActive) return GamePhase.DONE
            val screen = captureBitmap()
            if (screen == null) { delay(NORMAL_INTERVAL_MS); continue }
            try {
                // 检测准备按钮
                val readyCoord = detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                if (readyCoord != null) {
                    // 等级校验（仅日志）
                    val levelVisible = detector.matchLevelIcon(screen, targetGrade) != null
                    if (levelVisible) log("✅ 建议等级匹配 (lv${targetGrade.level})")
                    else log("⚠ 建议等级未匹配，继续执行")

                    click(readyCoord)
                    log("✅ 点击准备")
                    delay(POST_CLICK_DELAY)
                    readyFound = true
                    break
                }

                // 已达上限 → 退出
                if (detector.matchTemplate(screen, ScreenState.DAILY_LIMIT) != null) {
                    log("⚠ ${targetGrade.displayName} 已达上限，退出队伍")
                    exitTeam()
                    ctx.currentBounty = null
                    return GamePhase.IDLE
                }

                // 已完成标记 → 退出
                if (detector.matchTemplate(screen, ScreenState.TEAM_COMPLETED) != null) {
                    log("⚠ 该悬赏已完成，退出队伍")
                    exitTeam()
                    ctx.currentBounty = null
                    return GamePhase.IDLE
                }

                // 队伍已满 → 退出
                if (detector.matchTemplate(screen, ScreenState.TEAM_FULL) != null) {
                    log("⚠ 队伍已满，退出")
                    exitTeam()
                    ctx.currentBounty = null
                    return GamePhase.IDLE
                }
            } finally {
                screen.recycle()
            }
            delay(NORMAL_INTERVAL_MS)
        }

        if (!readyFound) {
            log("⚠ 未找到准备按钮，退出队伍")
            exitTeam()
            return GamePhase.SCANNING
        }

        // ── 阶段2: 等待战斗开始（15s超时） ──
        log("等待战斗开始(15s超时)")
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < WAIT_BATTLE_TIMEOUT_MS) {
            if (!coroutineContext.isActive) return GamePhase.DONE
            val screen = captureBitmap()
            if (screen == null) { delay(FAST_INTERVAL_MS); continue }
            try {
                // 检测下滑警告 → 点击进入战斗
                val warningCoord = detector.matchTemplate(screen, ScreenState.BATTLE_WARNING)
                if (warningCoord != null) {
                    log("✅ 战斗开始，点击滑屏")
                    click(warningCoord)
                    delay(1000)
                    return GamePhase.BATTLE
                }

                // 正常等待中
                if (detector.matchTemplate(screen, ScreenState.WAITING_SCREEN) != null) continue

                // 已到结算（战斗极快结束）
                if (detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP) != null ||
                    detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON) != null
                ) {
                    log("✅ 战斗已结束")
                    return GamePhase.SETTLEMENT
                }

                // 回到大厅
                if (detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null ||
                    detector.matchTemplate(screen, ScreenState.RECRUIT_TAB) != null
                ) {
                    log("⚠ 等待期间回到大厅")
                    return GamePhase.IDLE
                }

                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                if (elapsed > 0 && elapsed % 5 == 0L) log("等待中... (${elapsed}s)")
            } finally {
                screen.recycle()
            }
            delay(FAST_INTERVAL_MS)
        }

        log("⚠ 等待战斗超时 ${WAIT_BATTLE_TIMEOUT_MS}ms")
        exitTeam()
        return GamePhase.IDLE
    }

    // ══════════════════════════════════════════
    //  节点5：战斗
    // ══════════════════════════════════════════

    private suspend fun phaseBattle(ctx: GameContext): GamePhase {
        log("战斗 Phase")
        var lastSkillTime = 0L
        val skillCooldown = 2000L
        var missCount = 0

        while (coroutineContext.isActive) {
            val screen = captureBitmap()
            if (screen == null) { delay(FAST_INTERVAL_MS); continue }
            try {
                // 结算/胜利弹窗
                if (detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP) != null) {
                    log("✅ 结算弹窗")
                    return GamePhase.SETTLEMENT
                }

                // 确定按钮（胜利）
                if (detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON) != null) {
                    log("✅ 胜利确定按钮")
                    return GamePhase.SETTLEMENT
                }

                // 失败弹窗
                if (detector.matchTemplate(screen, ScreenState.DEFEAT_POPUP) != null) {
                    log("⚠ 战斗失败")
                    return GamePhase.SETTLEMENT
                }

                // 释放技能（500ms检测一次，不参与线性链）
                val now = System.currentTimeMillis()
                if (now - lastSkillTime >= 500L) {
                    if (useSkills(screen)) lastSkillTime = now
                }

                missCount++
                if (missCount >= 10) { // 10次(1s)无匹配 → 整体判定
                    val (state, _) = detector.detectForPhase(screen, SceneDetector.SCOPE_BATTLE)
                    if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                        log("⚠ 战斗异常回到大厅")
                        return GamePhase.IDLE
                    }
                    missCount = 0
                }
            } finally {
                screen.recycle()
            }
            delay(FAST_INTERVAL_MS)
        }
        return GamePhase.DONE
    }

    // ══════════════════════════════════════════
    //  节点6：结算领奖
    // ══════════════════════════════════════════

    private suspend fun phaseClaim(ctx: GameContext): GamePhase {
        val grade = ctx.currentBounty
        log("结算 Phase，悬赏=${grade?.displayName}")
        var missCount = 0

        var loopCount = 0
        while (coroutineContext.isActive && loopCount < LINEAR_MAX_LOOP) {
            loopCount++
            val screen = captureBitmap()
            if (screen == null) { delay(NORMAL_INTERVAL_MS); continue }
            try {
                // 先点空白关闭弹窗
                clickOutside()
                delay(300)

                // 点击确定按钮
                val confirmCoord = detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    log("点击确定按钮")
                    click(confirmCoord)
                    delay(POST_CLICK_DELAY)
                }

                // 回到大厅？
                if (detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null) {
                    log("已回到大厅")
                    break
                }

                missCount++
                if (missCount >= LINEAR_MAX_MISS) {
                    val (state, _) = detector.detectForPhase(screen, SceneDetector.SCOPE_CLAIM)
                    if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) break
                    missCount = 0
                }
            } finally {
                screen.recycle()
            }
            delay(NORMAL_INTERVAL_MS)
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

    // ══════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════

    private fun buildContext(configs: List<BountyConfig>): GameContext {
        val enabled = configs.filter { it.enabled }
        return GameContext(
            currentPhase = GamePhase.IDLE,
            activeGrades = enabled.map { it.grade },
            runCounts = enabled.associate { it.grade to 0 }.toMutableMap()
        )
    }

    private fun remainingGrades(ctx: GameContext): List<BountyGrade> {
        return ctx.activeGrades.filter { g -> (ctx.runCounts[g] ?: 0) < g.defaultRuns }
    }

    /** 节点1+2：从任意位置导航到招募列表 */
    private suspend fun ensureRecruitView(): Boolean {
        repeat(NAVIGATE_RETRIES) {
            if (!coroutineContext.isActive) return false
            val screen = captureBitmap() ?: return@repeat delay(NORMAL_INTERVAL_MS)
            try {
                val (state, coord) = detector.detectForPhase(screen, SceneDetector.SCOPE_NAVIGATE)
                when (state) {
                    ScreenState.UNKNOWN -> {
                        log("节点1: 界面无法识别")
                        delay(NORMAL_INTERVAL_MS)
                    }
                    ScreenState.CHAT_ICON -> {
                        log("节点1: 检测到聊天按钮，点击")
                        click(coord!!)
                        delay(POST_CLICK_DELAY)
                    }
                    ScreenState.RECRUIT_TAB -> {
                        log("节点2: 检测到招募页签，点击")
                        click(coord!!)
                        delay(1000)
                        return true
                    }
                    ScreenState.RECRUIT_LIST, ScreenState.JOIN_BUTTON -> {
                        log("节点3: 已在招募列表")
                        return true
                    }
                    ScreenState.SETTLEMENT_POPUP, ScreenState.CONFIRM_BUTTON -> {
                        log("检测到弹窗，点击关闭")
                        click(coord!!)
                        delay(POST_CLICK_DELAY)
                    }
                    ScreenState.BACK_BUTTON -> {
                        log("检测到返回，点击")
                        click(coord!!)
                        delay(800)
                    }
                    else -> {
                        log("导航中... 界面=$state")
                        delay(NORMAL_INTERVAL_MS)
                    }
                }
            } finally {
                screen.recycle()
            }
        }
        log("⚠ 导航重试耗尽")
        return false
    }

    /** 退出队伍直到回到大厅 */
    private suspend fun exitTeam() {
        log("退出队伍...")
        var confirmAttempts = 0
        repeat(10) {
            val screen = captureBitmap() ?: return@repeat delay(500)
            try {
                val (state, _) = detector.detectForPhase(screen, SceneDetector.SCOPE_EXIT)
                if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                    log("已回到大厅")
                    return
                }
                if (state == ScreenState.EXIT_CONFIRM) {
                    val confirmCoord = detector.matchTemplate(screen, ScreenState.EXIT_CONFIRM)
                    if (confirmCoord != null) {
                        click(confirmCoord)
                        delay(1000)
                        confirmAttempts++
                        if (confirmAttempts > 3) { clickOutside(); delay(800) }
                        return@repeat
                    }
                }
                val backCoord = detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    click(backCoord)
                    delay(1200)
                } else {
                    if (state == ScreenState.UNKNOWN) { clickOutside(); delay(800) }
                    else delay(500)
                }
            } finally {
                screen.recycle()
            }
        }
    }

    /** TAB刷新: 切到私聊页签 → 切回组队招募页签 */
    private suspend fun performTabRefresh(screen: Bitmap) {
        val chatTabCoord = detector.matchTemplate(screen, ScreenState.CHAT_TAB)
        if (chatTabCoord != null) {
            click(chatTabCoord)
            delay(200)
        } else {
            log("⚠ 未识别私聊页签，使用固定坐标")
            clickScreen(0.5f, 0.12f)
            delay(200)
        }
        val tabCoord = detector.matchTemplate(screen, ScreenState.RECRUIT_TAB)
        if (tabCoord != null) {
            click(tabCoord)
            delay(300)
        }
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
        // 武器技能（固定位置预留）
        val weapon = detector.matchTemplate(screen, ScreenState.WEAPON_SKILL)
        if (weapon != null) {
            click(weapon)
            log("释放武器技能")
            delay(200)
            return true
        }
        return false
    }

    /** 点击屏幕比例位置 */
    private suspend fun clickScreen(xRatio: Float, yRatio: Float) {
        val display = context.resources.displayMetrics
        accessibility?.clickAt(display.widthPixels * xRatio, display.heightPixels * yRatio)
        delay(300)
    }

    private suspend fun clickOutside() {
        accessibility?.clickAt(540f, 1200f)
        delay(300)
    }

    private suspend fun captureBitmap(): Bitmap? {
        repeat(3) {
            val bmp = capture.capture()
            if (bmp != null) return bmp
            delay(300)
        }
        log("⚠ 截图失败")
        return null
    }

    private fun click(coord: Pair<Float, Float>) {
        accessibility?.clickAt(coord.first, coord.second)
    }

    /** 整体判定3次失败 → 写崩溃日志到文件 */
    private fun writeCrashLog(ctx: GameContext) {
        try {
            val dir = File(context.filesDir, "crash_logs")
            dir.mkdirs()
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "crash_$time.log")
            FileWriter(file).use { w ->
                w.write("=== NinjaAu Crash Log ===\n")
                w.write("Time: $time\n")
                w.write("Phase: ${ctx.currentPhase}\n")
                w.write("CurrentBounty: ${ctx.currentBounty}\n")
                w.write("RunCounts: ${ctx.runCounts}\n")
                w.write("TotalCycles: ${ctx.totalCycles}\n")
                w.write("GlobalFailCount: $globalFailCount\n")
                w.write("================================\n")
            }
            log("📄 崩溃日志已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "写崩溃日志失败", e)
        }
    }
}

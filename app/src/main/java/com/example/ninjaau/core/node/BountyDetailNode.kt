package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.opencv.core.Mat

/**
 * 悬赏详情节点 — 队伍房间内的所有操作。
 *
 * 对应页面：悬赏详情/队伍房间（显示队伍成员、准备按钮、等待中界面）。
 *
 * 职责：
 * - 等级校验（检查队伍级别是否符合勾选范围）
 * - 点击准备按钮
 * - 等待战斗开始（30s 超时）
 * - 退出队伍（点击返回→确认→回到大厅，含确认弹窗检测）
 * - 已达上限 / 加入失败 / 速通结算 等异常处理
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class BountyDetailNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val POST_CLICK_DELAY = 500L
        private const val WAIT_BATTLE_TIMEOUT_MS = 30_000L
        /** 在队伍中无操作超时（人机队长检测） */
        private const val AI_CAPTAIN_TIMEOUT_MS = 30_000L
    }

    /**
     * 裁剪区域匹配 + 偏移点击。
     * 裁剪区域的匹配坐标是相对于裁剪起点的，此方法自动加上偏移返回全屏坐标。
     */
    private fun matchAndClick(cropMat: Mat, offsetX: Float, offsetY: Float, state: ScreenState): Boolean {
        val coord = this.ctx.detector.matchTemplateMat(cropMat, state) ?: return false
        this.ctx.click(Pair(coord.first + offsetX, coord.second + offsetY))
        return true
    }

    private inline fun <T> timed(name: String, block: () -> T): Pair<T, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        return Pair(result, elapsed)
    }

    /**
     * 悬赏详情循环：
     *
     * ① LV 图标检测 → 确定实际等级（唯一等级来源）
     * ② LV 不在 activeGrades → 退出队伍（列表点错）
     * ③ LV 确认后：chaseDream 跳过上限检测，普通等级检测上限
     * ④ 准备按钮 → 点击准备
     * ⑤ 战斗等待 → BATTLE_LOADING / 超时
     * ⑥ 回到大厅
     * ⑦ 无匹配 → 超时检测
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {

        this.ctx.log("准备按钮出现，当前 activeGrades 数量: ${ctx.activeGrades.size}")
        this.ctx.log("activeGrades 内容: ${ctx.activeGrades.joinToString { it.displayName }}")

        var battleWaitStart = 0L
        var lastMatchMs = System.currentTimeMillis()
        var teamEnterTime = 0L

        while (currentCoroutineContext().isActive) {
            val iterationStart = System.currentTimeMillis()

            val (screen, captureMs) = timed("截图") { this.ctx.captureBitmap() }
            if (screen == null) {
                this.ctx.delay(POST_CLICK_DELAY); continue
            }
            var screenMat: Mat? = null
            try {
                val (mat, matMs) = timed("转Mat") { this.ctx.detector.screenToMat(screen) }
                screenMat = mat

                // ═══ 上方 1/10 高度、中间 1/3 宽度：等级图标检测 ═══
                val (topMiddleTenth, cropMidMs) = timed("crop上方中1/10") { this.ctx.detector.cropTopMiddleTenth(screenMat) }

                try {
                    // ═══════════════════════════════════════════════════
                    //  ① LV 图标检测 — 只匹配已勾选等级，全部失败则退出
                    // ═══════════════════════════════════════════════════
                    var actualGrade = ctx.actualGrade
                    var lvMs = 0L
                    if (actualGrade == null) {
                        val (levelMatch, ms) = timed("LV检测") {
                            this.ctx.detector.matchAnyLevelIconMat(
                                topMiddleTenth, ctx.activeGrades
                            )
                        }
                        lvMs = ms
                        if (levelMatch == null) {
                            this.ctx.log("LV检测失败：所有已勾选等级均未匹配，退出队伍")
                            topMiddleTenth.release()
                            screenMat?.release()
                            screen.recycle()
                            ctx.currentBounty = null
                            ctx.actualGrade = null
                            return exitTeam()
                        }
                        actualGrade = levelMatch.grade
                        ctx.actualGrade = actualGrade
                        this.ctx.log("LV检测确认: ${actualGrade.displayName} (lv${actualGrade.level})，相似度=${String.format("%.3f", levelMatch.similarity)}")
                    }
                    // LV 存在 → 刷新超时计时器（只要 LV 在就持续续期）
                    lastMatchMs = System.currentTimeMillis()
                    if (teamEnterTime == 0L) teamEnterTime = System.currentTimeMillis()

                    // ═══════════════════════════════════════════════════
                    //  ② LV 不在 activeGrades → 列表点错，退出队伍
                    // ═══════════════════════════════════════════════════
                    if (actualGrade !in ctx.activeGrades) {
                        this.ctx.log("⚠ LV ${actualGrade.displayName}(lv${actualGrade.level}) 不在勾选范围 ${ctx.activeGrades.joinToString { it.displayName }}，退出队伍")
                        ctx.currentBounty = null
                        ctx.actualGrade = null
                        return exitTeam()
                    }

                    // ═══════════════════════════════════════════════════
                    //  ③ LV 确认后：根据实际等级决定是否检测上限
                    //  chaseDream → 跳过上限检测
                    //  普通等级 → 检测上限
                    // ═══════════════════════════════════════════════════
                    val isChaseDream = ctx.chaseDreamGrades.contains(actualGrade)
                    var limitMatchMs = 0L
                    if (!isChaseDream) {
                        val (topFifth, cropTopMs) = timed("crop上方1/5") { this.ctx.detector.cropTopFifth(screenMat) }
                        try {
                            val (dailyLimitResult, limitMs) = timed("上限匹配") {
                                this.ctx.detector.matchTemplateMat(topFifth, ScreenState.DAILY_LIMIT)
                            }
                            limitMatchMs = limitMs

                            if (dailyLimitResult != null) {
                                this.ctx.log("检测到今日已达上限标识")
                                val group = actualGrade.group
                                for (member in group.members()) {
                                    ctx.runCounts[member] = group.defaultRuns
                                }
                                ctx.activeGrades = ctx.activeGrades.filter { it.group != group || it in ctx.chaseDreamGrades }
                                this.ctx.log("${actualGrade.displayName} ${group.name} 标记为完成")
                                ctx.currentBounty = null
                                ctx.actualGrade = null
                                return exitTeam()
                            }
                        } finally {
                            topFifth.release()
                        }
                    }

                    // ═══════════════════════════════════════════════════
                    //  ④ 准备按钮 → 点击准备
                    // ═══════════════════════════════════════════════════
                    if (battleWaitStart == 0L) {
                        val (bottomRight, cropBrMs) = timed("crop右下1/4") { this.ctx.detector.cropBottomRightQuarter(screenMat) }
                        try {
                            var readyMatchMs = 0L
                            val (readyCoord, rmMs) = timed("准备匹配") {
                                this.ctx.detector.matchTemplateMat(bottomRight, ScreenState.READY_BUTTON)
                            }
                            readyMatchMs = rmMs
                            if (readyCoord != null) {
                                this.ctx.log("等级确认 ${actualGrade.displayName} (lv${actualGrade.level})，点击准备")
                                if (actualGrade.level in 105..130) {
                                    this.ctx.log("SS+ 悬赏！播放提醒铃声")
                                    this.ctx.playAlarm()
                                }
                                val halfW = screen.width / 2f
                                val offsetH = screen.height * 3f / 4f
                                this.ctx.click(Pair(readyCoord.first + halfW, readyCoord.second + offsetH))
                                battleWaitStart = System.currentTimeMillis()
                                this.ctx.log("战斗等待计时启动")
                                lastMatchMs = System.currentTimeMillis()
                                val totalMs = System.currentTimeMillis() - iterationStart
                                this.ctx.log("[详情耗时] 截图${captureMs}ms | 转Mat${matMs}ms | crop${cropMidMs}+${cropBrMs}ms | LV${lvMs}ms | 上限${limitMatchMs}ms | 准备${readyMatchMs}ms | 合计${totalMs}ms")
                                continue
                            }
                        } finally {
                            bottomRight.release()
                        }
                    }

                    // ═══════════════════════════════════════════════════
                    //  ⑤ 战斗等待中 → 检测加载界面
                    // ═══════════════════════════════════════════════════
                    if (battleWaitStart > 0) {
                        var battleMatchMs = 0L
                        val (bottomLeft, cropBlMs) = timed("crop左下1/3") { this.ctx.detector.cropBottomLeft(screenMat) }
                        try {
                            val (matched, bmMs) = timed("战斗匹配") {
                                this.ctx.detector.matchTemplateMat(bottomLeft, ScreenState.BATTLE_LOADING) != null
                            }
                            battleMatchMs = bmMs
                            if (matched) {
                                this.ctx.log("检测到战斗加载界面，切换至加载节点")
                                val totalMs = System.currentTimeMillis() - iterationStart
                                this.ctx.log("[详情耗时] 截图${captureMs}ms | 转Mat${matMs}ms | crop${cropMidMs}+${cropBlMs}ms | 战斗匹配${battleMatchMs}ms | 合计${totalMs}ms")
                                return GamePhase.BATTLE_LOADING
                            }
                        } finally {
                            bottomLeft.release()
                        }

                        val elapsed = System.currentTimeMillis() - battleWaitStart
                        if (elapsed >= WAIT_BATTLE_TIMEOUT_MS) {
                            this.ctx.log("等待战斗超时 ${WAIT_BATTLE_TIMEOUT_MS}ms，尝试退出队伍")
                            ctx.currentBounty = null
                            ctx.actualGrade = null
                            val exitResult = exitTeam()
                            return exitResult
                        }
                    }

                    // ═══════════════════════════════════════════════════
                    //  ⑥ 回到大厅
                    // ═══════════════════════════════════════════════════
                    var chatMatchMs = 0L
                    val (chatHit, cmMs) = timed("大厅匹配") {
                        this.ctx.detector.matchTemplateMat(screenMat, ScreenState.CHAT_ICON) != null
                    }
                    chatMatchMs = cmMs
                    if (chatHit) {
                        this.ctx.log("已回到大厅")
                        ctx.currentBounty = null
                        ctx.actualGrade = null
                        val totalMs = System.currentTimeMillis() - iterationStart
                        this.ctx.log("[详情耗时] 截图${captureMs}ms | 转Mat${matMs}ms | crop${cropMidMs}ms | 大厅匹配${chatMatchMs}ms | 合计${totalMs}ms")
                        return GamePhase.LOBBY
                    }

                    // ═══════════════════════════════════════════════════
                    //  ⑦ 无匹配 → 超时检测
                    // ═══════════════════════════════════════════════════
                    // 人机队长检测：在队伍中超过30s且未点击准备 → 退出
                    if (teamEnterTime > 0 && battleWaitStart == 0L
                        && System.currentTimeMillis() - teamEnterTime > AI_CAPTAIN_TIMEOUT_MS) {
                        this.ctx.log("队伍中等待超时${AI_CAPTAIN_TIMEOUT_MS}ms，疑似人机队长，退出队伍")
                        ctx.currentBounty = null
                        ctx.actualGrade = null
                        return exitTeam()
                    }
                    checkNodeTimeout(lastMatchMs)
                    val totalMs = System.currentTimeMillis() - iterationStart
                    this.ctx.log("[详情耗时] 截图${captureMs}ms | 转Mat${matMs}ms | crop${cropMidMs}ms | 合计${totalMs}ms")
                } finally {
                    topMiddleTenth.release()
                }
            } finally {
                screenMat?.release()
                screen.recycle()
            }
        }
        return GamePhase.DONE
    }

    /**
     * 退出队伍 — 顺序执行：① 点返回 → 等待 → ② 点确认弹窗
     * 退出后检测实际到达的节点（可能队长在退出期间开始了战斗）。
     * @return LOBBY 或 BATTLE_LOADING
     */
    private suspend fun exitTeam(): GamePhase {
        this.ctx.log("退出队伍...")

        // ═══ 阶段①：点击返回按钮 ═══
        val screen1 = this.ctx.captureBitmap() ?: return GamePhase.LOBBY
        var screenMat1: Mat? = null
        try {
            screenMat1 = this.ctx.detector.screenToMat(screen1)
            val topLeft = this.ctx.detector.cropTopLeftEighth(screenMat1)
            try {
                val backCoord = this.ctx.detector.matchTemplateMat(topLeft, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    this.ctx.click(backCoord)
                    this.ctx.log("点击返回")
                }
            } finally {
                topLeft.release()
            }
        } finally {
            screenMat1?.release()
            screen1.recycle()
        }
        this.ctx.delay(1000)

        // ═══ 阶段②：点击确认退出弹窗（右半+下半） ═══
        val screen2 = this.ctx.captureBitmap() ?: return GamePhase.LOBBY
        var screenMat2: Mat? = null
        try {
            screenMat2 = this.ctx.detector.screenToMat(screen2)
            val bottomRight = this.ctx.detector.cropBottomRightHalf(screenMat2)
            try {
                val halfW = screen2.width / 2f
                val halfH = screen2.height / 2f
                if (matchAndClick(bottomRight, halfW, halfH, ScreenState.EXIT_CONFIRM)) {
                    this.ctx.log("点击确认退出")
                }
            } finally {
                bottomRight.release()
            }
        } finally {
            screenMat2?.release()
            screen2.recycle()
        }

        // ═══ 阶段③：退出后检测实际到达的节点 ═══
        this.ctx.delay(500)
        val screen3 = this.ctx.captureBitmap() ?: return GamePhase.LOBBY
        var screenMat3: Mat? = null
        try {
            screenMat3 = this.ctx.detector.screenToMat(screen3)
            // 检测大厅标识（左侧1/10）
            val chatCrop = this.ctx.detector.cropLeftTenth(screenMat3)
            try {
                val chatTemplate = this.ctx.detector.getTemplate(ScreenState.CHAT_ICON)
                if (chatTemplate != null && com.example.ninjaau.core.recognition.TemplateMatcher.matchWithMat(chatCrop, chatTemplate, 0.75f).isMatched) {
                    this.ctx.log("退出成功，已回到大厅")
                    return GamePhase.LOBBY
                }
            } finally {
                chatCrop.release()
            }
            // 检测战斗加载标识（上方1/4）
            val loadingCrop = this.ctx.detector.cropTopQuarter(screenMat3)
            try {
                val loadingTemplate = this.ctx.detector.getTemplate(ScreenState.BATTLE_LOADING)
                if (loadingTemplate != null && com.example.ninjaau.core.recognition.TemplateMatcher.matchWithMat(loadingCrop, loadingTemplate, 0.8f).isMatched) {
                    this.ctx.log("退出期间战斗已开始，进入战斗加载")
                    return GamePhase.BATTLE_LOADING
                }
            } finally {
                loadingCrop.release()
            }
            this.ctx.log("退出后未识别页面，默认返回大厅")
            return GamePhase.LOBBY
        } finally {
            screenMat3?.release()
            screen3.recycle()
        }
    }
}

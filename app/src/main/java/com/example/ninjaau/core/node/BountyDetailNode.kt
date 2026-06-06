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
        private const val POST_CLICK_DELAY = 1000L
        private const val WAIT_BATTLE_TIMEOUT_MS = 30_000L
    }

    /**
     * 悬赏详情循环：
     *
     * ① 退出确认弹窗（点击确认）
     * ② 已达上限 → exitTeam，回到大厅
     * ③ 准备按钮 + 等级校验（点击准备，启动战斗等待计时）
     * ④ 战斗等待中 → BATTLE_LOADING / 超时
     * ⑤ 回到大厅（CHAT_ICON / RECRUIT_TAB）
     * ⑥ 无匹配 → checkNodeTimeout 超时检测
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {

        this.ctx.log("准备按钮出现，当前 activeGrades 数量: ${ctx.activeGrades.size}")
        this.ctx.log("activeGrades 内容: ${ctx.activeGrades.joinToString { it.displayName }}")

        val targetGrade = ctx.currentBounty ?: return GamePhase.LOBBY
        this.ctx.log("悬赏详情 Phase，目标=${targetGrade.displayName}(lv${targetGrade.level})")

        var battleWaitStart = 0L
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(POST_CLICK_DELAY); continue
            }
            var screenMat: Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ═══ 上方 1/5 区域：上限检测 ═══
                val topFifth = this.ctx.detector.cropTopFifth(screenMat)
                // ═══ 上方 1/10 高度、中间 1/3 宽度：等级图标检测 ═══
                val topMiddleTenth = this.ctx.detector.cropTopMiddleTenth(screenMat)
                // ═══ 上方 1/4 区域：加载/大厅检测 ═══
                val topQuarter = this.ctx.detector.cropTopQuarter(screenMat)
                // ═══ 右下 1/4 区域：准备按钮检测 ═══
                val bottomRight = this.ctx.detector.cropBottomRightQuarter(screenMat)

                try {
                    // ═══ ② 已达上限 → 二次确认后标记对应组完成 ═══
                    val isChaseDream = ctx.chaseDreamGrades.contains(targetGrade)
                    if (!isChaseDream && this.ctx.detector.matchTemplateMat(
                            topFifth,
                            ScreenState.DAILY_LIMIT
                        ) != null
                    ) {
                        this.ctx.delay(500)
                        val recheckScreen = this.ctx.captureBitmap()
                        if (recheckScreen != null) {
                            try {
                                val recheckMat = this.ctx.detector.screenToMat(recheckScreen)
                                try {
                                    val recheckTopFifth = this.ctx.detector.cropTopFifth(recheckMat)
                                    try {
                                        if (this.ctx.detector.matchTemplateMat(
                                                recheckTopFifth,
                                                ScreenState.DAILY_LIMIT
                                            ) == null
                                        ) {
                                            this.ctx.log("DAILY_LIMIT 二次确认未命中，忽略")
                                            lastMatchMs = System.currentTimeMillis()
                                            continue
                                        }
                                    } finally {
                                        recheckTopFifth.release()
                                    }
                                } finally {
                                    recheckMat.release()
                                }
                            } finally {
                                recheckScreen.recycle()
                            }
                        }

                        val levelMatch = this.ctx.detector.matchAnyLevelIconMat(
                            topMiddleTenth,
                            com.example.ninjaau.model.BountyGrade.entries
                        )
                        if (levelMatch != null) {
                            val limitGrade = levelMatch.grade
                            this.ctx.log(
                                "上限检测等级匹配相似度: ${
                                    String.format(
                                        "%.3f",
                                        levelMatch.similarity
                                    )
                                }"
                            )
                            val group = limitGrade.group
                            for (member in group.members()) {
                                ctx.runCounts[member] = group.defaultRuns
                            }
                            ctx.activeGrades = ctx.activeGrades.filter { it.group != group }
                            this.ctx.log("${group.name}已达今日上限，标记为完成")
                        } else {
                            this.ctx.log("已达上限，等级匹配失败(activeGrades=${ctx.activeGrades.joinToString { it.displayName }})，使用 currentBounty 兜底")
                            val fallbackGroup = ctx.currentBounty?.group
                            if (fallbackGroup != null) {
                                for (member in fallbackGroup.members()) {
                                    ctx.runCounts[member] = fallbackGroup.defaultRuns
                                }
                                ctx.activeGrades =
                                    ctx.activeGrades.filter { it.group != fallbackGroup }
                                this.ctx.log("${fallbackGroup.name} 根据 currentBounty 标记为完成")
                            }
                        }
                        exitTeam()
                        ctx.currentBounty = null
                        ctx.actualGrade = null
                        return GamePhase.LOBBY
                    }

                    // ═══ ③ 准备按钮 + 等级校验（右下1/4匹配准备，上方1/4匹配等级） ═══
                    if (battleWaitStart == 0L) {
                        val readyCoord = this.ctx.detector.matchTemplateMat(
                            bottomRight,
                            ScreenState.READY_BUTTON
                        )
                        if (readyCoord != null) {
                            val levelMatch = this.ctx.detector.matchAnyLevelIconMat(
                                topMiddleTenth,
                                ctx.activeGrades
                            )
                            if (levelMatch == null) {
                                this.ctx.log("⚠ 等级识别失败(activeGrades=${ctx.activeGrades.joinToString { it.displayName }})，队伍级别不在勾选范围内，退出队伍")
                                exitTeam()
                                ctx.currentBounty = null
                                ctx.actualGrade = null
                                return GamePhase.LOBBY
                            }
                            val actualGrade = levelMatch.grade
                            this.ctx.log(
                                "等级匹配相似度: ${
                                    String.format(
                                        "%.3f",
                                        levelMatch.similarity
                                    )
                                }"
                            )
                            ctx.actualGrade = actualGrade
                            this.ctx.log("等级匹配 ${actualGrade.displayName} (lv${actualGrade.level})，点击准备")
                            if (actualGrade.level in 105..130) {
                                this.ctx.log("SS+ 悬赏！播放提醒铃声")
                                this.ctx.playAlarm()
                            }
                            // 坐标偏移：bottomRight 裁剪起点为 (halfWidth, 3/4 height)
                            val halfW = screen.width / 2f
                            val offsetH = screen.height * 3f / 4f
                            this.ctx.click(
                                Pair(
                                    readyCoord.first + halfW,
                                    readyCoord.second + offsetH
                                )
                            )
                            this.ctx.delay(POST_CLICK_DELAY)
                            battleWaitStart = System.currentTimeMillis()
                            this.ctx.log("战斗等待计时启动")
                            lastMatchMs = System.currentTimeMillis()
                            continue
                        }
                    }

                    // ═══ ④ 战斗等待中（左下角匹配加载笑脸） ═══
                    if (battleWaitStart > 0) {
                        val bottomLeft = this.ctx.detector.cropBottomLeft(screenMat)
                        try {
                            if (this.ctx.detector.matchTemplateMat(
                                    bottomLeft,
                                    ScreenState.BATTLE_LOADING
                                ) != null
                            ) {
                                this.ctx.log("检测到战斗加载界面，切换至加载节点")
                                return GamePhase.BATTLE_LOADING
                            }
                        } finally {
                            bottomLeft.release()
                        }

                        val elapsed = System.currentTimeMillis() - battleWaitStart
                        if (elapsed >= WAIT_BATTLE_TIMEOUT_MS) {
                            this.ctx.log("等待战斗超时 ${WAIT_BATTLE_TIMEOUT_MS}ms")
                            exitTeam()
                            ctx.currentBounty = null
                            ctx.actualGrade = null
                            return GamePhase.LOBBY
                        }
                    }

                    // ═══ ⑤ 回到大厅 ═══
                    if (this.ctx.detector.matchTemplateMat(
                            screenMat,
                            ScreenState.CHAT_ICON
                        ) != null
                    ) {
                        this.ctx.log("已回到大厅")
                        ctx.currentBounty = null
                        ctx.actualGrade = null
                        return GamePhase.LOBBY
                    }

                    // ═══ ⑥ 无匹配 → 超时检测 ═══
                    checkNodeTimeout(lastMatchMs)
                } finally {
                    bottomRight.release()
                    topQuarter.release()
                    topMiddleTenth.release()
                    topFifth.release()
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
     */
    private suspend fun exitTeam() {
        this.ctx.log("退出队伍...")

        // ═══ 阶段①：点击返回按钮 ═══
        val screen1 = this.ctx.captureBitmap() ?: return
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

        // ═══ 阶段②：点击确认退出弹窗（右半侧下半侧） ═══
        val screen2 = this.ctx.captureBitmap() ?: return
        var screenMat2: Mat? = null
        try {
            screenMat2 = this.ctx.detector.screenToMat(screen2)
            val x = screenMat2.cols() / 2
            val y = screenMat2.rows() / 2
            val w = screenMat2.cols() / 2
            val h = screenMat2.rows() / 2
            val rightBottom = Mat(screenMat2, org.opencv.core.Rect(x, y, w, h))
            try {
                val localCoord = this.ctx.detector.matchTemplateMat(rightBottom, ScreenState.EXIT_CONFIRM)
                if (localCoord != null) {
                    val clickCoord = Pair(localCoord.first + x.toFloat(), localCoord.second + y.toFloat())
                    this.ctx.click(clickCoord)
                    this.ctx.log("点击确认退出")
                }
            } finally {
                rightBottom.release()
            }
        } finally {
            screenMat2?.release()
            screen2.recycle()
        }
    }
}

package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.core.config.ScriptConfigRepository
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 招募列表节点 — 识别悬赏等级并点击加入队伍。
 *
 * 对应页面：招募列表（悬赏大厅），显示所有可加入的悬赏条目。
 *
 * 职责：
 * - 100ms 全屏扫描，匹配已勾选的等级图标
 * - 匹配后固定偏移点击（右430px, 下300px）代替按钮识别
 * - 刷新检测（超出范围、列表过期）
 * - 点击加入后检测页面切换 → 进入 [BountyDetailNode]
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class BountyListNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val FAST_INTERVAL_MS = 1L
        private const val RAPID_CLICK_COUNT = 3
    }

    /**
     * 扫描循环逻辑：
     *
     * ① 刷新检测 → OUT_OF_RANGE_RECRUIT
     * ② 等级匹配 → 固定偏移点击
     * ③ 页面切换检测 → READY_BUTTON → BOUNTY_DETAIL
     * ④ 无匹配 → checkNodeTimeout 超时检测
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        val remaining = remainingGrades(ctx)
        if (remaining.isEmpty()) return GamePhase.DONE

        // ═══ 抢悬赏快速点击模式 ═══
        val fastClick = ScriptConfigRepository.fastClickEnabled.value
        this.ctx.log("快速点击模式: $fastClick, XY=(${ScriptConfigRepository.fastClickX.value}, ${ScriptConfigRepository.fastClickY.value})")
        if (fastClick) {
            return fastClickLoop(ctx)
        }

        this.ctx.log("待完成等级: ${remaining.joinToString { it.displayName }}")
        this.ctx.delay(500)
        this.ctx.log("招募列表 — 进入扫描循环")

        var lastMatchMs = System.currentTimeMillis()
        var loopCount = 0

        while (coroutineContext.isActive) {
            loopCount++
            val loopStart = System.currentTimeMillis()

            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(FAST_INTERVAL_MS);
                continue
            }
            val tCapture = System.currentTimeMillis()

            // screen Bitmap → Mat（一次转换，所有匹配复用）
            var screenMat: org.opencv.core.Mat? = null
            var rangeMat: org.opencv.core.Mat? = null
            var gradeMat: org.opencv.core.Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)
                val tMat = System.currentTimeMillis()

                // ═══ 0. 组队邀请拦截（全屏检测，弹窗可能出现在任意位置） ═══
                if (ScriptConfigRepository.inviteCheckEnabled.value) {
                    val inviteCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.TEAM_INVITATION)
                    if (inviteCoord != null) {
                        this.ctx.log("检测到组队邀请弹窗，拒绝")
                        val rejectCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.INVITE_REJECT)
                        if (rejectCoord != null) {
                            this.ctx.click(rejectCoord)
                            this.ctx.delay(500)
                        }
                        lastMatchMs = System.currentTimeMillis()
                        continue
                    }
                }
                val tInvite = System.currentTimeMillis()

                // ═══ ① 刷新检测（左半边下方 1/5 ROI） ═══
                rangeMat = this.ctx.detector.cropBottomLeftFifth(screenMat)
                val rangeCoord =
                    this.ctx.detector.matchTemplateMat(rangeMat, ScreenState.OUT_OF_RANGE_RECRUIT)
                if (rangeCoord != null) {
                    val roiY = screenMat.rows() * 4 / 5
                    val fullCoord = Pair(rangeCoord.first, rangeCoord.second + roiY)
                    this.ctx.log("超出范围悬赏，点击刷新列表 (全屏: $fullCoord)")
                    this.ctx.click(fullCoord)
                    this.ctx.delay(1000)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                val tRange = System.currentTimeMillis()

                // ═══ ② 等级匹配 → 偏移点击（左侧第3个1/10 ROI，x=20%~30%） ═══
                gradeMat = this.ctx.detector.cropLeftMidTenth(screenMat)
                val tGradeCrop = System.currentTimeMillis()
                val match = this.ctx.detector.matchAnyGradeMat(gradeMat, remaining)
                val tGradeMatch = System.currentTimeMillis()
                if (match != null) {
                    val (grade, coord) = match
                    ctx.currentBounty = grade
                    val cropOffsetX = screenMat.cols() * 2f / 10
                    val clickX = coord.first + cropOffsetX + 430f
                    val clickY = coord.second + 300f
                    this.ctx.click(Pair(clickX, clickY))
                    this.ctx.log("${grade.displayName}悬赏，点击加入 ($clickX, $clickY)")
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ ③ 页面切换检测（右下角 700x300） ═══
                if (ctx.currentBounty != null) {
                    val cols = screenMat.cols()
                    val rows = screenMat.rows()
                    val cropW = (cols * 0.273).toInt()
                    val cropH = (rows * 0.208).toInt()
                    val cropX = cols - cropW
                    val cropY = rows - cropH
                    val readyCrop = org.opencv.core.Mat(screenMat, org.opencv.core.Rect(cropX, cropY, cropW, cropH))
                    try {
                        val readyCoord =
                            this.ctx.detector.matchTemplateMat(readyCrop, ScreenState.READY_BUTTON)
                        if (readyCoord != null) {
                            this.ctx.log("检测到准备按钮，切换至悬赏详情")
                            return GamePhase.BOUNTY_DETAIL
                        }
                    } finally {
                        readyCrop.release()
                    }
                }

                val tReady = System.currentTimeMillis()

                // ═══ ④ 无匹配 → 超时检测（在组队招募页签内不抛超时，持续等待） ═══
                val tabCrop = this.ctx.detector.cropTopCenterQuarter(screenMat)
                try {
                    val onRecruitTab = this.ctx.detector.matchTemplateMat(tabCrop, ScreenState.RECRUIT_LIST_SCREEN) != null
                    if (onRecruitTab) {
                        lastMatchMs = System.currentTimeMillis()
                    } else {
                        checkNodeTimeout(lastMatchMs)
                    }
                } finally {
                    tabCrop.release()
                }

                // 每 10 轮输出一次耗时统计
                if (loopCount % 10 == 0) {
                    val total = tReady - loopStart
                    this.ctx.log("[性能] 第${loopCount}轮 | 截图${tCapture - loopStart}ms | 转Mat${tMat - tCapture}ms | 邀请${tInvite - tMat}ms | 刷新${tRange - tInvite}ms | 等级裁剪${tGradeCrop - tRange}ms | 等级匹配${tGradeMatch - tGradeCrop}ms | 准备${tReady - tGradeMatch}ms | 合计${total}ms")
                }
            } finally {
                gradeMat?.release()
                rangeMat?.release()
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(FAST_INTERVAL_MS)
        }
        return GamePhase.DONE
    }

    /**
     * 抢悬赏快速点击模式 — 跳过等级匹配，直接高速点击加入队伍按钮。
     * 进入详情后由 BountyDetailNode 进行 LV 级别校验。
     */
    private suspend fun fastClickLoop(ctx: GameContext): GamePhase? {
        val clickX = ScriptConfigRepository.fastClickX.value.toFloat()
        val clickY = ScriptConfigRepository.fastClickY.value.toFloat()
        this.ctx.log("抢悬赏模式 — 直接点击 ($clickX, $clickY)")
        this.ctx.delay(500)

        var lastMatchMs = System.currentTimeMillis()
        var loopCount = 0

        while (coroutineContext.isActive) {
            loopCount++

            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(10L)
                continue
            }
            var screenMat: org.opencv.core.Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ═══ 组队邀请拦截 ═══
                if (ScriptConfigRepository.inviteCheckEnabled.value) {
                    val inviteCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.TEAM_INVITATION)
                    if (inviteCoord != null) {
                        this.ctx.log("检测到组队邀请弹窗，拒绝")
                        val rejectCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.INVITE_REJECT)
                        if (rejectCoord != null) {
                            this.ctx.click(rejectCoord)
                            this.ctx.delay(500)
                        }
                        lastMatchMs = System.currentTimeMillis()
                        continue
                    }
                }

                // ═══ 页面切换检测（进入详情） ═══
                val cols = screenMat.cols()
                val rows = screenMat.rows()
                val cropW = (cols * 0.273).toInt()
                val cropH = (rows * 0.208).toInt()
                val readyCrop = org.opencv.core.Mat(screenMat, org.opencv.core.Rect(cols - cropW, rows - cropH, cropW, cropH))
                try {
                    val readyCoord = this.ctx.detector.matchTemplateMat(readyCrop, ScreenState.READY_BUTTON)
                    if (readyCoord != null) {
                        this.ctx.log("检测到准备按钮，切换至悬赏详情")
                        return GamePhase.BOUNTY_DETAIL
                    }
                } finally {
                    readyCrop.release()
                }

                // ═══ 高速连点加入队伍 ═══
                repeat(RAPID_CLICK_COUNT) { this.ctx.click(Pair(clickX, clickY)) }
                lastMatchMs = System.currentTimeMillis()

                if (loopCount % 50 == 0) {
                    this.ctx.log("[抢悬赏] 已点击 ${loopCount * RAPID_CLICK_COUNT} 次")
                }

                // ═══ 页签校验（不在招募列表则异常） ═══
                val tabCrop = this.ctx.detector.cropTopCenterQuarter(screenMat)
                try {
                    val onRecruitTab = this.ctx.detector.matchTemplateMat(tabCrop, ScreenState.RECRUIT_LIST_SCREEN) != null
                    if (!onRecruitTab) {
                        checkNodeTimeout(lastMatchMs)
                    }
                } finally {
                    tabCrop.release()
                }
            } finally {
                screenMat?.release()
                screen.recycle()
            }
        }
        return GamePhase.DONE
    }

    private fun remainingGrades(ctx: GameContext): List<BountyGrade> {
        return ctx.activeGrades.filter { g ->
            // 追梦等级：始终保留，即使已完成目标次数也继续扫描
            ctx.chaseDreamGrades.contains(g) ||
            (ctx.runCounts[g] ?: 0) < (ctx.targetRuns[g] ?: g.defaultRuns)
        }
    }
}

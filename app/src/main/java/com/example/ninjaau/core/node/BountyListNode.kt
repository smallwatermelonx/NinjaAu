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

        this.ctx.log("待完成等级: ${remaining.joinToString { it.displayName }}")
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
                val match = this.ctx.detector.matchAnyGradeMat(gradeMat, remaining)
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

                val tGrade = System.currentTimeMillis()

                // ═══ ③ 页面切换检测 ═══
                if (ctx.currentBounty != null) {
                    val readyCoord =
                        this.ctx.detector.matchTemplateMat(screenMat, ScreenState.READY_BUTTON)
                    if (readyCoord != null) {
                        this.ctx.log("检测到准备按钮，切换至悬赏详情")
                        return GamePhase.BOUNTY_DETAIL
                    }
                }

                val tReady = System.currentTimeMillis()

                // ═══ ④ 无匹配 → 超时检测 ═══
                checkNodeTimeout(lastMatchMs)

                // 每 10 轮输出一次耗时统计
                if (loopCount % 10 == 0) {
                    val total = tReady - loopStart
                    this.ctx.log("[性能] 第${loopCount}轮 | 截图${tCapture - loopStart}ms | 转Mat${tMat - tCapture}ms | 邀请${tInvite - tMat}ms | 刷新${tRange - tInvite}ms | 等级${tGrade - tRange}ms | 合计${total}ms")
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

    private fun remainingGrades(ctx: GameContext): List<BountyGrade> {
        return ctx.activeGrades.filter { g ->
            // 追梦等级：始终保留，即使已完成目标次数也继续扫描
            ctx.chaseDreamGrades.contains(g) ||
            (ctx.runCounts[g] ?: 0) < (ctx.targetRuns[g] ?: g.defaultRuns)
        }
    }
}

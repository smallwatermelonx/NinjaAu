package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
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

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val recruitTab = ctx.detector.matchTemplate(screen, ScreenState.RECRUIT_TAB)
        return RecognizeResult(recruitTab != null, recruitTab)
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

        while (coroutineContext.isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(FAST_INTERVAL_MS);
                continue
            }
            try {
                // ═══ 0. 组队邀请拦截 ═══
                val inviteCoord = this.ctx.detector.matchTemplate(screen, ScreenState.TEAM_INVITATION)
                if (inviteCoord != null) {
                    this.ctx.log("检测到组队邀请弹窗，拒绝")
                    val rejectCoord = this.ctx.detector.matchTemplate(screen, ScreenState.INVITE_REJECT)
                    if (rejectCoord != null) {
                        this.ctx.click(rejectCoord)
                        this.ctx.delay(500)
                    }
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ ① 刷新检测 ═══
                val rangeCoord =
                    this.ctx.detector.matchTemplate(screen, ScreenState.OUT_OF_RANGE_RECRUIT)
                if (rangeCoord != null) {
                    this.ctx.log("超出范围悬赏，点击刷新列表")
                    this.ctx.click(rangeCoord)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }
                // ═══ ② 等级匹配 → 固定偏移点击 ═══
                val match = this.ctx.detector.matchAnyGrade(screen, remaining)
                if (match != null) {
                    val (grade, coord) = match
                    ctx.currentBounty = grade
                    val clickX = coord.first + 430f
                    val clickY = coord.second + 300f
                    this.ctx.click(Pair(clickX, clickY))
                    this.ctx.log("${grade.displayName}悬赏，点击加入 ($clickX, $clickY)")
                    this.ctx.onPageEvent?.invoke("匹配到 ${grade.displayName} 悬赏，加入队伍")
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ ③ 页面切换检测 ═══
                if (ctx.currentBounty != null) {
                    val readyCoord =
                        this.ctx.detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                    if (readyCoord != null) {
                        this.ctx.log("检测到准备按钮，切换至悬赏详情")
                        return GamePhase.BOUNTY_DETAIL
                    }
                }

                // ═══ ④ 无匹配 → 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
            } finally {
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

package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 结算节点 — 领取奖励并回到大厅。
 *
 * 职责：
 * - 点击结算弹窗、确认按钮
 * - 更新 runCounts 和 activeGrades
 * - 判断全部完成 → DONE
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class SettlementNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
        private const val POST_CLICK_DELAY = 1000L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val coord = ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP)
        if (coord != null) return RecognizeResult(true, coord)
        val confirm = ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
        return RecognizeResult(confirm != null, confirm)
    }

    /**
     * 结算领奖循环。
     *
     * 结算弹窗 → 点击关闭
     * 确认按钮 → 点击领奖
     * 回到大厅 → 更新完成计数并退出
     * 无匹配 → checkNodeTimeout 超时检测
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        val grade = ctx.actualGrade ?: ctx.currentBounty
        this.ctx.log("结算 Phase，悬赏=${grade?.displayName}")
        var lastMatchMs = System.currentTimeMillis()

        while (coroutineContext.isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                var settlementCoord =
                    this.ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP)
                if (settlementCoord != null) {
                    this.ctx.click(settlementCoord)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(POST_CLICK_DELAY)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                if (this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null) {
                    this.ctx.log("已回到大厅")
                    break
                }

                // 无匹配 → 超时检测
                checkNodeTimeout(lastMatchMs)
            } finally {
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }

        // 更新完成计数（仅递增实际完成的等级）
        if (grade != null) {
            val count = ctx.runCounts[grade] ?: 0
            ctx.runCounts[grade] = count + 1
            ctx.totalCycles++

            val group = grade.group
            val groupTotal = group.totalRuns(ctx.runCounts)
            this.ctx.log("${grade.displayName} 完成 ${groupTotal}/${group.defaultRuns}")

            if (group.isComplete(ctx.runCounts)) {
                ctx.activeGrades = ctx.activeGrades.filter { it.group != group }
                this.ctx.log("${group.name} 全部完成，从集合移除")
            }
        }
        ctx.currentBounty = null
        ctx.actualGrade = null

        if (ctx.activeGrades.isEmpty()) {
            this.ctx.log("所有悬赏已完成！")
            return GamePhase.DONE
        }
        return GamePhase.IDLE
    }
}

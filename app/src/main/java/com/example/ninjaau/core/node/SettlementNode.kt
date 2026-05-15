package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.core.recognition.SceneDetector
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
 */
class                                                                                                                                   SettlementNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
        private const val POST_CLICK_DELAY = 1000L
        private const val LINEAR_MAX_MISS = 3
        private const val LINEAR_MAX_LOOP = 300
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val coord = ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP)
        if (coord != null) return RecognizeResult(true, coord)
        val confirm = ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
        return RecognizeResult(confirm != null, confirm)
    }

    /**
     * 结算领奖循环，最多 300 周期。
     *
     * 结算弹窗 → clickOutside 关闭
     * 确认按钮 → 点击领奖
     * 回到大厅 → 更新完成计数并退出
     * 3 周期无匹配 → SCOPE_CLAIM 检测
     * 3 次 SCOPE_CLAIM 失败 → detectCurrentPage 兜底
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        val grade = ctx.currentBounty
        this.ctx.log("结算 Phase，悬赏=${grade?.displayName}")
        var missCount = 0
        var claimFallbackCount = 0

        var loopCount = 0
        while (coroutineContext.isActive && loopCount < LINEAR_MAX_LOOP) {
            loopCount++
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                if (this.ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP) != null) {
                    this.ctx.clickOutside(screen)
                    this.ctx.delay(800)
                    continue
                }

                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(POST_CLICK_DELAY)
                    continue
                }

                if (this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null) {
                    this.ctx.log("已回到大厅")
                    break
                }

                missCount++
                if (missCount >= LINEAR_MAX_MISS) {
                    val (state, _) = this.ctx.detector.detectForPhase(screen, SceneDetector.SCOPE_CLAIM)
                    if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) break
                    if (state == ScreenState.UNKNOWN) {
                        claimFallbackCount++
                        this.ctx.log("结算状态无法识别 ($claimFallbackCount/3)")
                        if (claimFallbackCount >= 3) {
                            this.ctx.log("连续3次结算无法识别，尝试全量页面检测")
                            val detectedPhase = this.ctx.detectCurrentPage(screen)
                            if (detectedPhase != null) {
                                this.ctx.log("检测到当前页面: $detectedPhase，跳转")
                                return detectedPhase
                            }
                            this.ctx.log("页面完全无法识别，停止脚本")
                            throw RuntimeException("结算阶段页面无法识别")
                        }
                    }
                    missCount = 0
                }
            } finally {
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }

        // 更新完成计数
        if (grade != null) {
            val count = ctx.runCounts[grade] ?: 0
            ctx.runCounts[grade] = count + 1
            ctx.totalCycles++
            this.ctx.log("${grade.displayName} 完成 ${count + 1}/${ctx.targetRuns[grade] ?: grade.defaultRuns}")
            if ((ctx.runCounts[grade] ?: 0) >= (ctx.targetRuns[grade] ?: grade.defaultRuns)) {
                ctx.activeGrades = ctx.activeGrades - grade
                this.ctx.log("${grade.displayName} 全部完成，从集合移除")
            }
        }
        ctx.currentBounty = null

        if (ctx.activeGrades.isEmpty()) {
            this.ctx.log("所有悬赏已完成！")
            return GamePhase.DONE
        }
        return GamePhase.IDLE
    }
}

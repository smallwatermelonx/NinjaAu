package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 失败节点 — 处理战斗失败后的流程。
 *
 * 两种屏幕：
 * - full_two：角色死亡，等待队友战斗（底部有"返回"按钮）
 *   - 队友成功 → SettlementNode
 *   - 队友也失败 / 最后一个死亡 → full（最终失败界面）
 * - full：最终失败界面（"失败"字样 + "确定"按钮）
 *   - 点击确定 → LOBBY
 */
class DefeatNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val FAST_INTERVAL_MS = 100L
        private const val NORMAL_INTERVAL_MS = 300L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("失败 Phase — 等待队友战斗结果")
        var lastMatchMs = System.currentTimeMillis()

        while (coroutineContext.isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(FAST_INTERVAL_MS)
                continue
            }
            var screenMat: org.opencv.core.Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ═══ 1. 最终失败界面（"失败"字样 + 确定按钮） ═══
                val defeatCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.DEFEAT_SCREEN)
                if (defeatCoord != null) {
                    this.ctx.log("检测到最终失败界面，点击确定")
                    val confirmCrop = this.ctx.detector.cropCenterHalf(screenMat)
                    try {
                        val confirmCoord = this.ctx.detector.matchTemplateMat(confirmCrop, ScreenState.CONFIRM_BUTTON)
                        if (confirmCoord != null) {
                            val fullX = confirmCoord.first + screenMat.cols() * 0.25f
                            val fullY = confirmCoord.second + screenMat.rows() * 0.35f
                            this.ctx.click(Pair(fullX, fullY))
                            this.ctx.log("点击确定返回大厅")
                            lastMatchMs = System.currentTimeMillis()
                            this.ctx.delay(500)
                            return GamePhase.LOBBY
                        }
                    } finally {
                        confirmCrop.release()
                    }
                }

                // ═══ 2. 等待队友界面（full_two — 底部返回按钮） ═══
                val backCrop = this.ctx.detector.cropBottomCenterNinth(screenMat)
                try {
                    val backCoord = this.ctx.detector.matchTemplateMat(backCrop, ScreenState.DEFEAT_BACK_BUTTON)
                    if (backCoord != null) {
                        val fullX = backCoord.first + screenMat.cols() / 3f
                        val fullY = backCoord.second + screenMat.rows() * 8f / 9f
                        this.ctx.click(Pair(fullX, fullY))
                        this.ctx.log("点击返回，退出失败等待界面")
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(500)
                        return GamePhase.LOBBY
                    }
                } finally {
                    backCrop.release()
                }

                // ═══ 3. 结算弹窗（队友战斗成功 → 进入结算） ═══
                val settlementCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.SETTLEMENT_POPUP)
                if (settlementCoord != null) {
                    this.ctx.log("队友战斗成功，进入结算")
                    return GamePhase.SETTLEMENT
                }

                // ═══ 4. 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
            } finally {
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }
        return GamePhase.LOBBY
    }
}

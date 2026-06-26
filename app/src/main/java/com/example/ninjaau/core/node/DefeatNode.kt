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
                    // 确定按钮在下半侧
                    val bottomHalf = org.opencv.core.Mat(screenMat, org.opencv.core.Rect(0, screenMat.rows() / 2, screenMat.cols(), screenMat.rows() / 2))
                    try {
                        val confirmCoord = this.ctx.detector.matchTemplateMat(bottomHalf, ScreenState.DEFEAT_CONFIRM)
                        if (confirmCoord != null) {
                            val fullX = confirmCoord.first.toFloat()
                            val fullY = confirmCoord.second + screenMat.rows() / 2f
                            this.ctx.click(Pair(fullX, fullY))
                            this.ctx.log("点击确定")
                            lastMatchMs = System.currentTimeMillis()
                            this.ctx.delay(800)
                            // 检测点击确定后进入的页面
                            return detectPostDefeat()
                        }
                    } finally {
                        bottomHalf.release()
                    }
                }

                // ═══ 2. 等待队友界面（full_two — 三分之一中间区域上半侧返回按钮） ═══
                val backCrop = org.opencv.core.Mat(screenMat, org.opencv.core.Rect(screenMat.cols() / 3, 0, screenMat.cols() / 3, screenMat.rows() / 2))
                try {
                    val backCoord = this.ctx.detector.matchTemplateMat(backCrop, ScreenState.DEFEAT_BACK_BUTTON)
                    if (backCoord != null) {
                        val fullX = backCoord.first + screenMat.cols() / 3f
                        val fullY = backCoord.second.toFloat()
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

    /**
     * 点击确定后截图检测当前页面：
     * - CHAT_ICON → 队长解散，回到大厅
     * - 其他 → 未解散，仍在悬赏详情，继续准备
     */
    private suspend fun detectPostDefeat(): GamePhase {
        val screen = this.ctx.captureBitmap()
        if (screen == null) return GamePhase.LOBBY
        var screenMat: org.opencv.core.Mat? = null
        try {
            screenMat = this.ctx.detector.screenToMat(screen)
            val chatCrop = this.ctx.detector.cropLeftTenth(screenMat)
            try {
                val chatTemplate = this.ctx.detector.getTemplate(ScreenState.CHAT_ICON)
                if (chatTemplate != null && com.example.ninjaau.core.recognition.TemplateMatcher.matchWithMat(chatCrop, chatTemplate, 0.75f).isMatched) {
                    this.ctx.log("队长已解散，返回大厅")
                    return GamePhase.LOBBY
                }
            } finally {
                chatCrop.release()
            }
            this.ctx.log("未解散，仍在悬赏详情，继续准备")
            return GamePhase.BOUNTY_DETAIL
        } finally {
            screenMat?.release()
            screen.recycle()
        }
    }
}

package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState

/**
 * 恢复节点 — 截屏识别当前页面，直接切入对应节点。
 *
 * 职责：
 * - 截屏识别当前所在页面
 * - 招募列表 → 直接返回 RECRUIT_LIST（跳过 IDLE→HallNode 导航）
 * - 其他页面 → 返回 IDLE 走正常导航流程
 */
class RecoveryNode(private val ctx: NodeContext? = null) : GameNode {

    companion object {
        private const val RECOVERY_DELAY_MS = 1500L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        val nodeCtx = this.ctx
        if (nodeCtx != null) {
            val screen = nodeCtx.captureBitmap()
            if (screen != null) {
                try {
                    val recruitCoord = nodeCtx.detector.matchTemplate(screen, ScreenState.RECRUIT_LIST_SCREEN)
                    if (recruitCoord != null) {
                        nodeCtx.log("恢复: 检测到招募列表页面，直接切入")
                        return GamePhase.RECRUIT_LIST
                    }
                } finally {
                    screen.recycle()
                }
            }
        }

        kotlinx.coroutines.delay(RECOVERY_DELAY_MS)
        return GamePhase.IDLE
    }
}

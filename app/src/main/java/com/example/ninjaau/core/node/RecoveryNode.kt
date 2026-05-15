package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase

/**
 * 恢复节点 — 兜底等待后回到 IDLE 重新导航。
 *
 * 职责：
 * - 等待 1.5s 让界面稳定
 * - 返回 IDLE 触发重新导航
 */
class RecoveryNode : GameNode {

    companion object {
        private const val RECOVERY_DELAY_MS = 1500L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        // RecoveryNode 始终匹配（兜底节点）
        return RecognizeResult(true)
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        kotlinx.coroutines.delay(RECOVERY_DELAY_MS)
        return GamePhase.IDLE
    }
}

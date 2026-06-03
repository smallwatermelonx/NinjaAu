package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 战斗加载节点 — 等待战斗加载完成。
 *
 * 对应页面：战斗加载界面（全员准备后→加载完成前，显示表情和等待动画）。
 *
 * 职责：
 * - 检测 BATTLE_LOADING（smile.png）是否还在显示
 * - 加载完成后切换至 FIGHT
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class BattleLoadingNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val INTERVAL_MS = 1000L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("战斗加载 Phase")
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(INTERVAL_MS); continue }
            try {
                // 加载完成 → 战斗开始
                if (this.ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING) == null) {
                    this.ctx.log("战斗加载完成，进入战斗")
                    return GamePhase.FIGHT
                }
                lastMatchMs = System.currentTimeMillis()
            } finally {
                screen.recycle()
            }
            // 无匹配 → 超时检测
            checkNodeTimeout(lastMatchMs)
            this.ctx.delay(INTERVAL_MS)
        }
        return GamePhase.DONE
    }
}

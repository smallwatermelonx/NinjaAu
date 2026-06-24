package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * 战斗加载节点 — 等待战斗加载完成。
 *
 * 对应页面：战斗加载界面（全员准备后→加载完成前，显示表情和等待动画）。
 *
 * 职责：
 * - 入口验证：确认确实在战斗加载页面，否则回退到前一个阶段
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

        // ═══ 入口验证：等待3秒后确认是否在战斗加载页面 ═══
        this.ctx.delay(3000)
        val screen = this.ctx.captureBitmap()
        if (screen != null) {
            try {
                if (this.ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING) == null) {
                    this.ctx.log("入口验证失败：未检测到战斗加载，回退")
                    return when (ctx.businessLine) {
                        com.example.ninjaau.model.BusinessLine.PERSONAL -> GamePhase.PERSONAL_BOUNTY_DETAIL
                        else -> GamePhase.BOUNTY_DETAIL
                    }
                }
                lastMatchMs = System.currentTimeMillis()
            } finally {
                screen.recycle()
            }
        }

        while (currentCoroutineContext().isActive) {
            val loopScreen = this.ctx.captureBitmap()
            if (loopScreen == null) { this.ctx.delay(INTERVAL_MS); continue }
            try {
                // 加载完成 → 战斗开始
                if (this.ctx.detector.matchTemplate(loopScreen, ScreenState.BATTLE_LOADING) == null) {
                    this.ctx.log("战斗加载完成，进入战斗")
                    return GamePhase.FIGHT
                }
                lastMatchMs = System.currentTimeMillis()
            } finally {
                loopScreen.recycle()
            }
            // 无匹配 → 超时检测
            checkNodeTimeout(lastMatchMs)
            this.ctx.delay(INTERVAL_MS)
        }
        return GamePhase.DONE
    }
}

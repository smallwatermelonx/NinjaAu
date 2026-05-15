package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 战斗加载节点 — 等待战斗加载完成。
 *
 * 对应页面：战斗加载界面（全员准备后→加载完成前，显示表情和等待动画）。
 *
 * 职责：
 * - 检测 BATTLE_LOADING（smile.png）是否还在显示
 * - 加载完成后检测 WARNING → 切换至 FIGHT
 * - 超时兜底
 */
class BattleLoadingNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val FAST_INTERVAL_MS = 100L
        private const val LOADING_TIMEOUT_MS = 30_000L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val coord = ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING)
        return RecognizeResult(coord != null, coord)
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("战斗加载 Phase")
        val startTime = System.currentTimeMillis()

        while (coroutineContext.isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= LOADING_TIMEOUT_MS) {
                this.ctx.log("战斗加载超时")
                return GamePhase.LOBBY
            }

            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(FAST_INTERVAL_MS); continue }
            try {
                // 加载完成 → 战斗开始
                val warningCoord = this.ctx.detector.matchTemplate(screen, ScreenState.WARNING)
                if (warningCoord != null) {
                    this.ctx.log("战斗加载完成，进入战斗")
                    this.ctx.click(warningCoord)
                    this.ctx.delay(1000)
                    return GamePhase.FIGHT
                }

                // 还在加载中
                if (this.ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING) != null) {
                    this.ctx.delay(FAST_INTERVAL_MS)
                    continue
                }

                // 异常回到大厅
                if (this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null) {
                    this.ctx.log("加载期间回到大厅")
                    return GamePhase.LOBBY
                }
            } finally {
                screen.recycle()
            }
            this.ctx.delay(FAST_INTERVAL_MS)
        }
        return GamePhase.DONE
    }
}

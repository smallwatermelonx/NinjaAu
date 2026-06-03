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
 * 个人悬赏详情节点 — 个人悬赏详情页操作。
 *
 * 入口：PERSONAL_BOUNTY_DETAIL phase
 * 出口：PERSONAL_BOUNTY_PUBLISH — 点击发布按钮后
 */
class PersonalBountyDetailNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("个人悬赏详情 Phase")
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                // ═══ 发布按钮 ═══
                val publishCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_PUBLISH_BTN)
                if (publishCoord != null) {
                    this.ctx.click(publishCoord)
                    this.ctx.log("点击发布按钮")
                    this.ctx.delay(1000)
                    return GamePhase.PERSONAL_BOUNTY_PUBLISH
                }

                // ═══ 返回按钮（回到个人悬赏中心） ═══
                val backCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    this.ctx.click(backCoord)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ 大厅（异常退出） ═══
                val chatIcon = this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON)
                if (chatIcon != null) {
                    this.ctx.log("异常回到大厅")
                    return GamePhase.PERSONAL_BOUNTY_CENTER
                }

                // ═══ 无匹配 → 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
            } finally {
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }
        return null
    }
}

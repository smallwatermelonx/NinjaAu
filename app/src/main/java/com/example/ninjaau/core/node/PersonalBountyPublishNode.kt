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
 * 个人悬赏发布节点 — 发布/接受个人悬赏，等待战斗开始。
 *
 * 入口：PERSONAL_BOUNTY_PUBLISH phase
 * 出口：BATTLE_LOADING — 等待战斗加载出现
 */
class PersonalBountyPublishNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("个人悬赏发布 Phase")
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                // ═══ 战斗加载（发布成功，进入战斗） ═══
                val loadingCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING)
                if (loadingCoord != null) {
                    this.ctx.log("检测到战斗加载界面")
                    return GamePhase.BATTLE_LOADING
                }

                // ═══ 发布按钮（仍在发布页面，再次点击） ═══
                val publishCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_PUBLISH_BTN)
                if (publishCoord != null) {
                    this.ctx.click(publishCoord)
                    this.ctx.log("点击发布按钮")
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

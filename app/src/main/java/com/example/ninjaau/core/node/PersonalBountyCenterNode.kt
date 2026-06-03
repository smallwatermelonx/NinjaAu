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
 * 个人悬赏中心节点 — 从大厅导航到个人悬赏中心，识别并点击悬赏条目。
 *
 * 入口：PERSONAL_BOUNTY_CENTER phase
 * 出口：PERSONAL_BOUNTY_DETAIL — 点击悬赏条目后页面切换
 */
class PersonalBountyCenterNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("个人悬赏中心 Phase")
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                // ═══ 弹窗关闭 ═══
                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ 已在个人悬赏中心 ═══
                val centerCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_CENTER_SCREEN)
                if (centerCoord != null) {
                    // TODO: 点击悬赏条目（细节后续处理）
                    this.ctx.log("个人悬赏中心已识别，等待点击条目")
                    lastMatchMs = System.currentTimeMillis()
                }

                // ═══ 返回按钮（回退到大厅） ═══
                val backCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    this.ctx.click(backCoord)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ 大厅入口（从大厅进入个人悬赏中心） ═══
                val chatIcon = this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON)
                if (chatIcon != null) {
                    // TODO: 导航到个人悬赏中心入口（细节后续处理）
                    this.ctx.log("检测到大厅，等待导航到个人悬赏中心")
                    lastMatchMs = System.currentTimeMillis()
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

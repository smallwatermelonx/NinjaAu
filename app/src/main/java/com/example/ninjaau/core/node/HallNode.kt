package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.NodeTimeoutException
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 大厅节点 — 从任意位置导航到招募列表。
 *
 * 职责：
 * - 识别当前界面元素并执行对应导航操作
 * - 按优先级依次检测弹窗、导航按钮、返回按钮
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class HallNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val POST_CLICK_DELAY = 1000L
        private const val NORMAL_INTERVAL_MS = 1000L
    }

    /**
     * 按优先级依次检测当前界面元素并执行对应操作。
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(NORMAL_INTERVAL_MS)
                continue
            }
            try {
                // ═══ 弹窗关闭 ═══
                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    this.ctx.log("导航: 确定按钮，点击关闭")
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(POST_CLICK_DELAY)
                    lastMatchMs = System.currentTimeMillis(); continue
                }

                val settlementCoord = this.ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP)
                if (settlementCoord != null) {
                    this.ctx.log("导航: 结算弹窗，点击关闭")
                    this.ctx.click(settlementCoord)
                    this.ctx.delay(POST_CLICK_DELAY)
                    lastMatchMs = System.currentTimeMillis(); continue
                }

                // ═══ 退出确认 ═══
                val exitConfirm = this.ctx.detector.matchTemplate(screen, ScreenState.EXIT_CONFIRM)
                if (exitConfirm != null) {
                    this.ctx.log("导航: 退出确认，点击")
                    this.ctx.click(exitConfirm)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis(); continue
                }

                // ═══ 已在大厅 ═══
                val chatIcon = this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON)
                if (chatIcon != null) {
                    this.ctx.log("导航: 聊天按钮，点击进入招募")
                    this.ctx.click(chatIcon)
                    this.ctx.delay(POST_CLICK_DELAY)
                    lastMatchMs = System.currentTimeMillis(); continue
                }

                // ═══ 招募页签 ═══
                val recruitTab = this.ctx.detector.matchTemplate(screen, ScreenState.RECRUIT_TAB)
                if (recruitTab != null) {
                    this.ctx.log("导航: 招募页签，点击")
                    this.ctx.click(recruitTab)
                    this.ctx.delay(1000)
                    return GamePhase.RECRUIT_LIST
                }

                // ═══ 返回按钮 ═══
                val backBtn = this.ctx.detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backBtn != null) {
                    this.ctx.log("导航: 返回按钮，点击")
                    this.ctx.click(backBtn)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis(); continue
                }

                // ═══ 无匹配 → 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
                this.ctx.delay(NORMAL_INTERVAL_MS)
            } finally {
                screen.recycle()
            }
        }
        return null
    }
}

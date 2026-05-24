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
 * 大厅节点 — 从任意位置导航到招募列表。
 *
 * 职责：
 * - 识别当前界面元素并执行对应导航操作
 * - 按优先级依次检测弹窗、导航按钮、返回按钮
 * - 连续 3 次无匹配 → detectCurrentPage 兜底
 */
class HallNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NAVIGATE_RETRIES = 6
        private const val POST_CLICK_DELAY = 1000L
        private const val NORMAL_INTERVAL_MS = 1000L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val chatIcon = ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON)
        if (chatIcon != null) return RecognizeResult(true, chatIcon)
        val recruitTab = ctx.detector.matchTemplate(screen, ScreenState.RECRUIT_TAB)
        return RecognizeResult(recruitTab != null, recruitTab)
    }

    /**
     * 按优先级依次检测当前界面元素并执行对应操作。
     *
     * 优先级顺序（高 → 低）：
     * 弹窗关闭（CONFIRM_BUTTON / SETTLEMENT_POPUP / DAILY_LIMIT / DEFEAT_POPUP）
     * → 退出确认（EXIT_CONFIRM）
     * → 导航操作（CHAT_ICON / RECRUIT_TAB）
     * → 返回（BACK_BUTTON）
     * → 无匹配兜底
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        var unknownCount = 0
        repeat(NAVIGATE_RETRIES) {
            if (!coroutineContext.isActive) return null
            val screen = this.ctx.captureBitmap() ?: return@repeat this.ctx.delay(NORMAL_INTERVAL_MS)
            try {
                // ═══ 弹窗关闭 ═══
                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    this.ctx.log("导航: 确定按钮，点击关闭")
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(POST_CLICK_DELAY)
                    unknownCount = 0; return@repeat
                }

                val settlementCoord = this.ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP)
                if (settlementCoord != null) {
                    this.ctx.log("导航: 结算弹窗，点击关闭")
                    this.ctx.click(settlementCoord)
                    this.ctx.delay(POST_CLICK_DELAY)
                    unknownCount = 0; return@repeat
                }

                // ═══ 障碍弹窗 ═══
                if (this.ctx.detector.matchTemplate(screen, ScreenState.DAILY_LIMIT) != null ||
                    this.ctx.detector.matchTemplate(screen, ScreenState.DEFEAT_POPUP) != null
                ) {
                    this.ctx.log("导航: 障碍弹窗，点击空白关闭")
                    this.ctx.clickOutside(screen)
                    this.ctx.delay(POST_CLICK_DELAY)
                    unknownCount = 0; return@repeat
                }

                // ═══ 退出确认 ═══
                val exitConfirm = this.ctx.detector.matchTemplate(screen, ScreenState.EXIT_CONFIRM)
                if (exitConfirm != null) {
                    this.ctx.log("导航: 退出确认，点击")
                    this.ctx.click(exitConfirm)
                    this.ctx.delay(800)
                    unknownCount = 0; return@repeat
                }

                // ═══ 已在大厅 ═══
                val chatIcon = this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON)
                if (chatIcon != null) {
                    this.ctx.log("导航: 聊天按钮，点击进入招募")
                    this.ctx.click(chatIcon)
                    this.ctx.delay(POST_CLICK_DELAY)
                    unknownCount = 0; return@repeat
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
                    unknownCount = 0; return@repeat
                }

                // ═══ 无匹配 ═══
                unknownCount++
                this.ctx.log("导航: 界面无法识别 ($unknownCount/3)")
                if (unknownCount >= 3) {
                    this.ctx.log("连续3次无法识别，尝试全量页面检测")
                    val detectedPhase = this.ctx.detectCurrentPage(screen)
                    if (detectedPhase != null) {
                        this.ctx.log("导航失败，检测到当前页面: $detectedPhase")
                        return detectedPhase
                    }
                    this.ctx.log("页面完全无法识别，停止脚本")
                    throw RuntimeException("无法识别当前页面")
                }
                this.ctx.delay(NORMAL_INTERVAL_MS)
            } finally {
                screen.recycle()
            }
        }
        this.ctx.log("导航重试耗尽")
        throw RuntimeException("导航重试耗尽")
    }
}

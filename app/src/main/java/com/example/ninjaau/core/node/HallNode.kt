package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 大厅节点 — 从任意位置导航到招募列表。
 *
 * 职责：
 * - 识别当前是否可导航（CHAT_ICON、弹窗等）
 * - 执行导航操作直到到达招募列表
 * - 使用 SCOPE_NAVIGATE 分步导航
 */
class HallNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NAVIGATE_RETRIES = 6
        private const val POST_CLICK_DELAY = 1000L
        private const val NORMAL_INTERVAL_MS = 1000L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        // 检查屏幕中是否包含可直接操作的导航元素
        val match = ctx.detector.detectForPhase(screen, SceneDetector.SCOPE_NAVIGATE)
        return RecognizeResult(match.first != ScreenState.UNKNOWN, match.second)
    }

    /**
     * 导航到招募列表。
     *
     * 执行步骤：
     * 1. 截图 → SCOPE_NAVIGATE 检测
     * 2. 根据状态执行对应操作（点击聊天按钮、招募页签、关弹窗等）
     * 3. 最多重试 NAVIGATE_RETRIES 次
     * 4. 连续 3 次 UNKNOWN → detectCurrentPage 兜底
     *
     * @return RECRUIT_LIST（导航成功）, 或其他兜底 phase
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        var unknownCount = 0
        repeat(NAVIGATE_RETRIES) {
            if (!coroutineContext.isActive) return null
            val screen = this.ctx.captureBitmap() ?: return@repeat this.ctx.delay(NORMAL_INTERVAL_MS)
            try {
                val (state, coord) = this.ctx.detector.detectForPhase(screen, SceneDetector.SCOPE_NAVIGATE)
                when (state) {
                    ScreenState.UNKNOWN -> {
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
                    }
                    ScreenState.CHAT_ICON -> {
                        this.ctx.log("导航: 检测到聊天按钮，点击")
                        this.ctx.click(coord!!)
                        this.ctx.delay(POST_CLICK_DELAY)
                    }
                    ScreenState.RECRUIT_TAB -> {
                        this.ctx.log("导航: 检测到招募页签，点击")
                        this.ctx.click(coord!!)
                        this.ctx.delay(1000)
                        return GamePhase.RECRUIT_LIST
                    }
                    ScreenState.RECRUIT_LIST,
                    ScreenState.RECRUIT_INVITE -> {
                        this.ctx.log("导航: 已在招募列表")
                        return GamePhase.RECRUIT_LIST
                    }
                    ScreenState.SETTLEMENT_POPUP,
                    ScreenState.CONFIRM_BUTTON -> {
                        this.ctx.log("导航: 检测到弹窗，点击关闭")
                        this.ctx.click(coord!!)
                        this.ctx.delay(POST_CLICK_DELAY)
                    }
                    ScreenState.DAILY_LIMIT,
                    ScreenState.DEFEAT_POPUP -> {
                        this.ctx.log("导航: 检测到弹窗($state)，继续导航")
                        this.ctx.clickOutside(screen)
                        this.ctx.delay(POST_CLICK_DELAY)
                    }
                    ScreenState.BACK_BUTTON -> {
                        this.ctx.log("导航: 检测到返回，点击")
                        this.ctx.click(coord!!)
                        this.ctx.delay(800)
                    }
                    ScreenState.EXIT_CONFIRM -> {
                        this.ctx.log("导航: 检测到退出确认，点击")
                        this.ctx.click(coord!!)
                        this.ctx.delay(800)
                    }
                    else -> {
                        this.ctx.log("导航中... 界面=$state")
                        this.ctx.delay(NORMAL_INTERVAL_MS)
                    }
                }
            } finally {
                screen.recycle()
            }
        }
        this.ctx.log("导航重试耗尽")
        throw RuntimeException("导航重试耗尽")
    }
}

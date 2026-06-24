package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.core.recognition.TemplateMatcher
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.opencv.core.Mat

/**
 * 大厅节点 — 从任意位置导航到目标页面。
 *
 * 职责：
 * - 识别当前界面元素并执行对应导航操作
 * - 日常悬赏：点击聊天按钮 → 招募页签 → 进入招募列表
 * - 个人悬赏：点击个人悬赏入口 → 进入个人悬赏列表
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class LobbyNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 500L
    }

    /**
     * 按优先级依次检测当前界面元素并执行对应操作。
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        if (ctx.roundStartTime == 0L) {
            ctx.roundStartTime = System.currentTimeMillis()
        }
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) {
                this.ctx.delay(NORMAL_INTERVAL_MS)
                continue
            }
            var screenMat: Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ═══ 日常悬赏导航（仅在启用日常时才走聊天→招募流程） ═══
                if (ctx.dailyEnabled) {
                    // 位于大厅（左侧 1/10 区域匹配聊天按钮）
                    val leftTenth = this.ctx.detector.cropLeftTenth(screenMat)
                    try {
                        val chatIcon = this.ctx.detector.matchTemplateMat(leftTenth, ScreenState.CHAT_ICON)
                        if (chatIcon != null) {
                            this.ctx.log("导航: 聊天按钮，点击进入招募")
                            this.ctx.click(chatIcon)
                            this.ctx.delay(NORMAL_INTERVAL_MS)
                            lastMatchMs = System.currentTimeMillis(); continue
                        }
                    } finally {
                        leftTenth.release()
                    }

                    // 招募页签（上方 1/10 区域匹配）
                    val topTenth = this.ctx.detector.cropTopTenth(screenMat)
                    try {
                        val recruitTab = this.ctx.detector.matchTemplateMat(topTenth, ScreenState.RECRUIT_TAB)
                        if (recruitTab != null) {
                            this.ctx.log("导航: 招募页签，点击")
                            this.ctx.click(recruitTab)
                            return GamePhase.RECRUIT_LIST
                        }
                    } finally {
                        topTenth.release()
                    }
                }

                // ═══ 个人悬赏入口（裁剪右侧区域匹配，灰度模式消除背景色差异） ═══
                if (ctx.personalBountyEnabled && ctx.personalActiveGrades.isNotEmpty()) {
                    var entryAreaMat: Mat? = null
                    try {
                        entryAreaMat = this.ctx.detector.cropLobbyPersonalBountyEntry(screenMat)
                        val cropX = (screenMat!!.cols() * 0.50).toFloat()
                        val cropY = (screenMat.rows() * 0.40).toFloat()
                        val templateMat = this.ctx.detector.getTemplateMat(ScreenState.PERSONAL_BOUNTY_ENTRY)
                        if (templateMat != null) {
                            val result = TemplateMatcher.matchMatWithMatGrayscale(entryAreaMat, templateMat, 0.78f, templateMat.cols(), templateMat.rows())
                            this.ctx.log("个人悬赏入口(灰度): 相似度=${String.format("%.3f", result.similarity)} 阈值=0.780 ${if (result.isMatched) "✓" else "✗"}")
                            if (result.isMatched) {
                                this.ctx.log("导航: 个人悬赏入口，点击进入")
                                this.ctx.click(Pair(result.centerX + cropX, result.centerY + cropY))
                                this.ctx.delay(1500)
                                return GamePhase.PERSONAL_BOUNTY_CENTER
                            }
                        } else {
                            this.ctx.log("个人悬赏入口: 模板加载失败")
                        }
                    } finally {
                        entryAreaMat?.release()
                    }
                }

                // ═══ 无匹配 → 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
                this.ctx.delay(NORMAL_INTERVAL_MS)
            } finally {
                screenMat?.release()
                screen.recycle()
            }
        }
        return null
    }
}

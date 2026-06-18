package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.opencv.core.Mat

/**
 * 大厅节点 — 从任意位置导航到招募列表。
 *
 * 职责：
 * - 识别当前界面元素并执行对应导航操作
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class HallNode(private val ctx: NodeContext) : GameNode {

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

                // ═══ 位于大厅（左侧 1/10 区域匹配聊天按钮） ═══
                val leftTenth = this.ctx.detector.cropLeftTenth(screenMat)
                try {
                    val chatIcon = this.ctx.detector.matchTemplateMat(leftTenth, ScreenState.CHAT_ICON)
                    if (chatIcon != null) {
                        this.ctx.log("导航: 聊天按钮，点击进入招募")
                        this.ctx.click(chatIcon)
                        // 点击chat图标会有侧边框弹出动作，需要一定延迟后再识别点击
                        this.ctx.delay(NORMAL_INTERVAL_MS)
                        lastMatchMs = System.currentTimeMillis(); continue
                    }
                } finally {
                    leftTenth.release()
                }

                // ═══ 招募页签（上方 1/10 区域匹配） ═══
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

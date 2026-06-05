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
 * 个人悬赏详情节点 — 从列表进入详情后的一系列操作：
 *
 * 1. 识别详情页面标识 (team_invitation.png)
 * 2. 点击页面 → 弹出对话框
 * 3. 识别 send_message.png → 点击
 * 4. 延迟 3 秒 → 点击右下角确认
 * 5. 识别 go.png → 点击出发 → 进入战斗加载
 *
 * 入口：PERSONAL_BOUNTY_DETAIL phase
 * 出口：BATTLE_LOADING
 */
class PersonalBountyDetailNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
        private const val BOTTOM_RIGHT_CONFIRM_X = 0.85f
        private const val BOTTOM_RIGHT_CONFIRM_Y = 0.92f
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("个人悬赏详情 Phase, bounty=${ctx.currentBounty?.displayName}")
        var lastMatchMs = System.currentTimeMillis()
        var clickedEntry = false

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                // ═══ 战斗加载（出发成功） ═══
                val loadingCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING)
                if (loadingCoord != null) {
                    this.ctx.log("检测到战斗加载，进入战斗")
                    return GamePhase.BATTLE_LOADING
                }

                // ═══ go.png → 点击出发 ═══
                val goCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_GO)
                if (goCoord != null) {
                    this.ctx.click(goCoord)
                    this.ctx.log("点击出发按钮")
                    this.ctx.delay(2000)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ send_message.png → 点击发送 ═══
                val sendMsgCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_SEND_MSG)
                if (sendMsgCoord != null) {
                    this.ctx.click(sendMsgCoord)
                    this.ctx.log("点击发送消息")
                    // 发送后延迟 3 秒，然后点击右下角
                    this.ctx.delay(3000)
                    val w = screen.width.toFloat()
                    val h = screen.height.toFloat()
                    this.ctx.click(Pair(w * BOTTOM_RIGHT_CONFIRM_X, h * BOTTOM_RIGHT_CONFIRM_Y))
                    this.ctx.log("点击右下角确认")
                    this.ctx.delay(1500)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ 详情页面标识 → 点击进入 ═══
                val detailCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_DETAIL_SCREEN)
                if (detailCoord != null && !clickedEntry) {
                    this.ctx.click(detailCoord)
                    this.ctx.log("点击个人悬赏条目")
                    clickedEntry = true
                    this.ctx.delay(1500)
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ 返回按钮（回到列表） ═══
                val backCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    this.ctx.click(backCoord)
                    this.ctx.delay(800)
                    this.ctx.log("返回个人悬赏列表")
                    return GamePhase.PERSONAL_BOUNTY_CENTER
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

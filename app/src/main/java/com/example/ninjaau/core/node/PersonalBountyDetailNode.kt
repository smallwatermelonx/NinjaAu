package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.recognition.TemplateMatcher
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.opencv.core.Mat

/**
 * 个人悬赏详情节点 — 从列表进入详情后的一系列操作：
 *
 * 1. 识别详情页面标识 (team_invitation.png) → 点击进入
 * 2. 识别 send_message.png → 点击发送消息（弹窗关闭后循环继续）
 * 3. 识别 go.png → 点击出发 → 返回 BATTLE_LOADING
 *
 * 入口：PERSONAL_BOUNTY_DETAIL phase
 * 出口：BATTLE_LOADING
 */
class PersonalBountyDetailNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 500L
        private const val TIMEOUT_MS = 30_000L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("个人悬赏详情 Phase, bounty=${ctx.currentBounty?.displayName}")
        var lastMatchMs = System.currentTimeMillis()
        var clickedEntry = false
        var msgSent = false

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            var screenMat: Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ═══ 1. 出发按钮（最高优先级，一出现就点） ═══
                var goAreaMat: Mat? = null
                try {
                    goAreaMat = this.ctx.detector.cropPersonalBountyGo(screenMat)
                    val goCropX = (screenMat!!.cols() * 0.74).toFloat()
                    val goCropY = (screenMat.rows() * 0.82).toFloat()
                    val goTemplate = this.ctx.detector.getTemplate(ScreenState.PERSONAL_BOUNTY_GO)
                    if (goTemplate != null) {
                        val goResult = TemplateMatcher.matchWithMat(goAreaMat, goTemplate, 0.85f)
                        if (goResult.isMatched) {
                            this.ctx.click(Pair(goResult.centerX + goCropX, goResult.centerY + goCropY))
                            this.ctx.log("点击出发按钮")
                            this.ctx.delay(1500)
                            return GamePhase.BATTLE_LOADING
                        }
                    }
                } finally {
                    goAreaMat?.release()
                }

                // ═══ 2. 详情页面标识（组队邀请按钮）→ 点击进入 ═══
                val detailCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.PERSONAL_BOUNTY_DETAIL_SCREEN)
                if (detailCoord != null && !clickedEntry) {
                    this.ctx.click(detailCoord)
                    this.ctx.log("点击组队邀请按钮")
                    clickedEntry = true
                    lastMatchMs = System.currentTimeMillis()
                    continue
                }

                // ═══ 3. 发送消息按钮（裁剪中间弹窗区域） ═══
                if (!msgSent) {
                    var sendAreaMat: Mat? = null
                    try {
                        sendAreaMat = this.ctx.detector.cropPersonalBountySendMessage(screenMat)
                        val sendCropX = (screenMat!!.cols() * 0.30).toFloat()
                        val sendCropY = (screenMat.rows() * 0.72).toFloat()
                        val sendTemplate = this.ctx.detector.getTemplate(ScreenState.PERSONAL_BOUNTY_SEND_MSG)
                        if (sendTemplate != null) {
                            val sendResult = TemplateMatcher.matchWithMat(sendAreaMat, sendTemplate, 0.85f)
                            if (sendResult.isMatched) {
                                this.ctx.click(Pair(sendResult.centerX + sendCropX, sendResult.centerY + sendCropY))
                                this.ctx.log("点击发送消息")
                                msgSent = true
                                this.ctx.delay(500)
                                // 点击弹窗右侧空白处关闭弹窗
                                val w = screen.width.toFloat()
                                this.ctx.click(Pair(w * 0.85f, screen.height.toFloat() * 0.5f))
                                this.ctx.log("点击空白处关闭弹窗")
                                lastMatchMs = System.currentTimeMillis()
                                continue
                            }
                        }
                    } finally {
                        sendAreaMat?.release()
                    }
                }

                // ═══ 无匹配 → 超时检测（正常业务逻辑退出） ═══
                if (lastMatchMs > 0L && System.currentTimeMillis() - lastMatchMs >= TIMEOUT_MS) {
                    this.ctx.log("详情页超时，执行退出流程")
                    val timeoutScreen = this.ctx.captureBitmap()
                    if (timeoutScreen != null) {
                        try {
                            // 点击左上角返回按钮
                            val backCoord = this.ctx.detector.matchTemplate(timeoutScreen, ScreenState.BACK_BUTTON)
                            if (backCoord != null) {
                                this.ctx.click(backCoord)
                                this.ctx.log("点击返回按钮")
                                this.ctx.delay(1000)
                                // 在右下半部分查找确认按钮
                                var confirmMat: Mat? = null
                                try {
                                    confirmMat = this.ctx.detector.screenToMat(timeoutScreen)
                                    val confirmAreaMat = this.ctx.detector.cropBottomRightQuarter(confirmMat)
                                    try {
                                        val confirmTemplate = this.ctx.detector.getTemplate(ScreenState.EXIT_CONFIRM)
                                        if (confirmTemplate != null) {
                                            val confirmResult = TemplateMatcher.matchWithMat(confirmAreaMat, confirmTemplate, 0.65f)
                                            if (confirmResult.isMatched) {
                                                val confirmCropX = (confirmMat!!.cols() * 0.50).toFloat()
                                                val confirmCropY = (confirmMat.rows() * 0.75).toFloat()
                                                this.ctx.click(Pair(confirmResult.centerX + confirmCropX, confirmResult.centerY + confirmCropY))
                                                this.ctx.log("点击确认按钮")
                                                this.ctx.delay(500)
                                            }
                                        }
                                    } finally {
                                        confirmAreaMat.release()
                                    }
                                } finally {
                                    confirmMat?.release()
                                }
                            }
                        } finally {
                            timeoutScreen.recycle()
                        }
                    }
                    return GamePhase.PERSONAL_BOUNTY_CENTER
                }
            } finally {
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }
        return null
    }
}

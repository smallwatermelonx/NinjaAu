package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.core.recognition.TemplateMatcher
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import org.opencv.core.Mat

/**
 * 恢复节点 — 多步恢复策略。
 *
 * 当节点超时或异常时，引擎切换到 RECOVERY 阶段。
 * 按优先级尝试恢复：
 *   1. 识别当前页面 → 路由到正确节点
 *   2. 尝试关闭弹窗（确认/返回按钮）
 *   3. 返回 IDLE 重新导航
 *
 * 连续恢复失败会递增 recoveryAttempt，
 * 超过上限由 WorkflowEngine 的 globalFailCount 处理。
 */
class RecoveryNode(private val ctx: NodeContext? = null) : GameNode {

    companion object {
        private const val RECOVERY_DELAY_MS = 1500L
        private const val MAX_RECOVERY_ATTEMPTS = 5
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        val nodeCtx = this.ctx
        if (nodeCtx == null) {
            kotlinx.coroutines.delay(RECOVERY_DELAY_MS)
            return GamePhase.IDLE
        }

        ctx.recoveryAttempt++
        nodeCtx.log("恢复: 第 ${ctx.recoveryAttempt} 次尝试")

        if (ctx.recoveryAttempt > MAX_RECOVERY_ATTEMPTS) {
            nodeCtx.log("恢复: 连续 ${ctx.recoveryAttempt} 次失败，强制回到大厅重新导航")
            ctx.recoveryAttempt = 0
            return GamePhase.IDLE
        }

        val screen = nodeCtx.captureBitmap()
        if (screen == null) {
            nodeCtx.log("恢复: 截图失败，等待重试")
            kotlinx.coroutines.delay(RECOVERY_DELAY_MS)
            return null
        }

        var screenMat: Mat? = null
        try {
            screenMat = nodeCtx.detector.screenToMat(screen)

            // ═══ 步骤 1: 识别当前页面，路由到正确节点 ═══

            val routed = identifyAndRoute(nodeCtx, screenMat, ctx)
            if (routed != null) {
                ctx.recoveryAttempt = 0
                return routed
            }

            // ═══ 步骤 2: 无法识别页面，尝试关闭弹窗 ═══

            val dismissed = tryDismissDialogs(nodeCtx, screenMat)
            if (dismissed) {
                nodeCtx.log("恢复: 尝试关闭弹窗，等待下一步")
                kotlinx.coroutines.delay(RECOVERY_DELAY_MS)
                return null // 继续下一轮恢复
            }

            // ═══ 步骤 3: 点击返回按钮 ═══

            val backCoord = nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.BACK_BUTTON)
            if (backCoord != null) {
                nodeCtx.log("恢复: 点击返回按钮")
                nodeCtx.click(backCoord)
                kotlinx.coroutines.delay(1000)
                return null // 等待页面切换后再识别
            }

            // ═══ 步骤 4: 所有尝试失败，回到 IDLE 重新导航 ═══

            nodeCtx.log("恢复: 所有恢复策略失败，回到大厅重新导航")
            return GamePhase.IDLE

        } finally {
            screenMat?.release()
            screen.recycle()
        }
    }

    /**
     * 识别当前页面并返回对应的 GamePhase。
     * 返回 null 表示无法识别。
     * 使用裁剪匹配参考各节点已有实现。
     */
    private fun identifyAndRoute(
        nodeCtx: NodeContext,
        screenMat: Mat,
        ctx: GameContext
    ): GamePhase? {
        // 1. 结算弹窗（全屏匹配）
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.SETTLEMENT_POPUP) != null) {
            nodeCtx.log("恢复: 检测到结算弹窗 → SETTLEMENT")
            return GamePhase.SETTLEMENT
        }

        // 2. 确认按钮（底部中间区域，参考 BountyDetailNode cropBottomMiddleFifth）
        val confirmCrop = nodeCtx.detector.cropBottomMiddleFifth(screenMat)
        try {
            val confirmTemplate = nodeCtx.detector.getTemplate(ScreenState.CONFIRM_BUTTON)
            if (confirmTemplate != null && TemplateMatcher.matchWithMat(confirmCrop, confirmTemplate, 0.8f).isMatched) {
                nodeCtx.log("恢复: 检测到确认按钮 → SETTLEMENT")
                return GamePhase.SETTLEMENT
            }
        } finally { confirmCrop.release() }

        // 3. 战斗加载（上方 1/4 区域，参考 BattleLoadingNode cropTopQuarter）
        val loadingCrop = nodeCtx.detector.cropTopQuarter(screenMat)
        try {
            val loadingTemplate = nodeCtx.detector.getTemplate(ScreenState.BATTLE_LOADING)
            if (loadingTemplate != null && TemplateMatcher.matchWithMat(loadingCrop, loadingTemplate, 0.8f).isMatched) {
                nodeCtx.log("恢复: 检测到战斗加载 → BATTLE_LOADING")
                return GamePhase.BATTLE_LOADING
            }
        } finally { loadingCrop.release() }

        // 4. 准备按钮（上方 1/8 区域，参考 BountyDetailNode 裁剪）
        val readyH = screenMat.rows() / 8
        val readyCrop = Mat(screenMat, org.opencv.core.Rect(0, 0, screenMat.cols(), readyH))
        try {
            val readyTemplate = nodeCtx.detector.getTemplate(ScreenState.READY_BUTTON)
            if (readyTemplate != null && TemplateMatcher.matchWithMat(readyCrop, readyTemplate, 0.8f).isMatched) {
                nodeCtx.log("恢复: 检测到准备按钮 → BOUNTY_DETAIL")
                return GamePhase.BOUNTY_DETAIL
            }
        } finally { readyCrop.release() }

        // 5. 战斗中 — 滑铲（左半边下方 1/4，参考 FightNode cropBottomLeftQuarter）
        val slideCrop = nodeCtx.detector.cropBottomLeftQuarter(screenMat)
        try {
            val slideTemplate = nodeCtx.detector.getTemplate(ScreenState.SLIDE_BUTTON)
            if (slideTemplate != null && TemplateMatcher.matchWithMat(slideCrop, slideTemplate, 0.8f).isMatched) {
                nodeCtx.log("恢复: 检测到战斗界面 → FIGHT")
                return GamePhase.FIGHT
            }
        } finally { slideCrop.release() }

        // 6. 战斗中 — 跳跃（右半边下方 1/4，参考 FightNode cropBottomRightQuarter）
        val jumpCrop = nodeCtx.detector.cropBottomRightQuarter(screenMat)
        try {
            val jumpTemplate = nodeCtx.detector.getTemplate(ScreenState.JUMP_BUTTON)
            if (jumpTemplate != null && TemplateMatcher.matchWithMat(jumpCrop, jumpTemplate, 0.8f).isMatched) {
                nodeCtx.log("恢复: 检测到战斗界面 → FIGHT")
                return GamePhase.FIGHT
            }
        } finally { jumpCrop.release() }

        // 7. 个人悬赏列表
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.PERSONAL_BOUNTY_LIST_SCREEN) != null) {
            nodeCtx.log("恢复: 检测到个人悬赏列表 → PERSONAL_BOUNTY_CENTER")
            return GamePhase.PERSONAL_BOUNTY_CENTER
        }

        // 8. 个人悬赏详情（右侧 55%~82%、下方 82%~98%，参考 PersonalBountyDetailNode cropPersonalBountyTeamInvite）
        val teamInviteCrop = nodeCtx.detector.cropPersonalBountyTeamInvite(screenMat)
        try {
            val teamInviteTemplate = nodeCtx.detector.getTemplate(ScreenState.PERSONAL_BOUNTY_DETAIL_SCREEN)
            if (teamInviteTemplate != null && TemplateMatcher.matchWithMat(teamInviteCrop, teamInviteTemplate, 0.85f).isMatched) {
                nodeCtx.log("恢复: 检测到个人悬赏详情 → PERSONAL_BOUNTY_DETAIL")
                return GamePhase.PERSONAL_BOUNTY_DETAIL
            }
        } finally { teamInviteCrop.release() }

        // 9. 个人悬赏出发按钮（右侧 74%~98%、下方 82%~98%，参考 PersonalBountyDetailNode cropPersonalBountyGo）
        val goCrop = nodeCtx.detector.cropPersonalBountyGo(screenMat)
        try {
            val goTemplate = nodeCtx.detector.getTemplate(ScreenState.PERSONAL_BOUNTY_GO)
            if (goTemplate != null && TemplateMatcher.matchWithMat(goCrop, goTemplate, 0.85f).isMatched) {
                nodeCtx.log("恢复: 检测到个人悬赏出发按钮 → PERSONAL_BOUNTY_DETAIL")
                return GamePhase.PERSONAL_BOUNTY_DETAIL
            }
        } finally { goCrop.release() }

        // 10. 招募列表
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.RECRUIT_LIST_SCREEN) != null) {
            nodeCtx.log("恢复: 检测到招募列表 → RECRUIT_LIST")
            return GamePhase.RECRUIT_LIST
        }

        // 11. 大厅（左侧 1/10 区域，参考 LobbyNode cropLeftTenth）
        val chatCrop = nodeCtx.detector.cropLeftTenth(screenMat)
        try {
            val chatTemplate = nodeCtx.detector.getTemplate(ScreenState.CHAT_ICON)
            if (chatTemplate != null && TemplateMatcher.matchWithMat(chatCrop, chatTemplate, 0.75f).isMatched) {
                nodeCtx.log("恢复: 检测到大厅 → LOBBY")
                return GamePhase.LOBBY
            }
        } finally { chatCrop.release() }

        return null
    }

    /**
     * 尝试关闭各种弹窗（确认按钮、退出确认等）。
     * 返回 true 表示成功点击了某个弹窗按钮。
     */
    private suspend fun tryDismissDialogs(nodeCtx: NodeContext, screenMat: Mat): Boolean {
        // 尝试点击确认按钮（底部中间区域）
        val confirmCrop = nodeCtx.detector.cropBottomMiddleFifth(screenMat)
        try {
            val confirmTemplate = nodeCtx.detector.getTemplate(ScreenState.CONFIRM_BUTTON)
            if (confirmTemplate != null) {
                val result = TemplateMatcher.matchWithMat(confirmCrop, confirmTemplate, 0.8f)
                if (result.isMatched) {
                    val cropX = (screenMat.cols() / 3).toFloat()
                    val cropY = (screenMat.rows() * 0.80).toFloat()
                    nodeCtx.click(Pair(result.centerX + cropX, result.centerY + cropY))
                    nodeCtx.log("恢复: 点击确认按钮关闭弹窗")
                    kotlinx.coroutines.delay(800)
                    return true
                }
            }
        } finally { confirmCrop.release() }

        // 尝试点击退出确认（右下半部分）
        val exitCrop = nodeCtx.detector.cropBottomRightHalf(screenMat)
        try {
            val exitTemplate = nodeCtx.detector.getTemplate(ScreenState.EXIT_CONFIRM)
            if (exitTemplate != null) {
                val result = TemplateMatcher.matchWithMat(exitCrop, exitTemplate, 0.65f)
                if (result.isMatched) {
                    val cropX = (screenMat.cols() * 0.50).toFloat()
                    val cropY = (screenMat.rows() * 0.50).toFloat()
                    nodeCtx.click(Pair(result.centerX + cropX, result.centerY + cropY))
                    nodeCtx.log("恢复: 点击退出确认")
                    kotlinx.coroutines.delay(800)
                    return true
                }
            }
        } finally { exitCrop.release() }

        return false
    }
}

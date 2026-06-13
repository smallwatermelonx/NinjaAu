package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.model.GameContext
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
     */
    private fun identifyAndRoute(
        nodeCtx: NodeContext,
        screenMat: Mat,
        ctx: GameContext
    ): GamePhase? {
        // 1. 结算弹窗
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.SETTLEMENT_POPUP) != null) {
            nodeCtx.log("恢复: 检测到结算弹窗 → SETTLEMENT")
            return GamePhase.SETTLEMENT
        }

        // 2. 确认按钮
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.CONFIRM_BUTTON) != null) {
            nodeCtx.log("恢复: 检测到确认按钮 → SETTLEMENT")
            return GamePhase.SETTLEMENT
        }

        // 3. 准备按钮（队伍房间）
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.READY_BUTTON) != null) {
            nodeCtx.log("恢复: 检测到准备按钮 → BOUNTY_DETAIL")
            return GamePhase.BOUNTY_DETAIL
        }

        // 4. 战斗加载
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.BATTLE_LOADING) != null) {
            nodeCtx.log("恢复: 检测到战斗加载 → BATTLE_LOADING")
            return GamePhase.BATTLE_LOADING
        }

        // 5. 战斗中 — 滑铲
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.SLIDE_BUTTON) != null) {
            nodeCtx.log("恢复: 检测到战斗界面 → FIGHT")
            return GamePhase.FIGHT
        }

        // 6. 战斗中 — 跳跃
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.JUMP_BUTTON) != null) {
            nodeCtx.log("恢复: 检测到战斗界面 → FIGHT")
            return GamePhase.FIGHT
        }

        // 7. 个人悬赏列表
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.PERSONAL_BOUNTY_LIST_SCREEN) != null) {
            nodeCtx.log("恢复: 检测到个人悬赏列表 → PERSONAL_BOUNTY_CENTER")
            return GamePhase.PERSONAL_BOUNTY_CENTER
        }

        // 8. 招募列表
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.RECRUIT_LIST_SCREEN) != null) {
            nodeCtx.log("恢复: 检测到招募列表 → RECRUIT_LIST")
            return GamePhase.RECRUIT_LIST
        }

        // 9. 大厅
        if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.CHAT_ICON) != null) {
            nodeCtx.log("恢复: 检测到大厅 → LOBBY")
            return GamePhase.LOBBY
        }

        return null
    }

    /**
     * 尝试关闭各种弹窗（确认按钮、退出确认等）。
     * 返回 true 表示成功点击了某个弹窗按钮。
     */
    private suspend fun tryDismissDialogs(nodeCtx: NodeContext, screenMat: Mat): Boolean {
        // 尝试点击确认按钮
        val confirmCoord = nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.CONFIRM_BUTTON)
        if (confirmCoord != null) {
            nodeCtx.log("恢复: 点击确认按钮关闭弹窗")
            nodeCtx.click(confirmCoord)
            kotlinx.coroutines.delay(800)
            return true
        }

        // 尝试点击退出确认
        val exitCoord = nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.EXIT_CONFIRM)
        if (exitCoord != null) {
            nodeCtx.log("恢复: 点击退出确认")
            nodeCtx.click(exitCoord)
            kotlinx.coroutines.delay(800)
            return true
        }

        return false
    }
}

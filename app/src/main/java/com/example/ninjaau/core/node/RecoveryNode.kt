package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import org.opencv.core.Mat

/**
 * 恢复节点 — 截屏识别当前页面，直接切入对应节点。
 *
 * 当节点超时或异常时，引擎切换到 RECOVERY 阶段。
 * 识别当前所在的游戏页面，路由到正确的节点继续执行。
 *
 * 检测优先级：具体页面优先于通用页面，避免误判。
 */
class RecoveryNode(private val ctx: NodeContext? = null) : GameNode {

    companion object {
        private const val RECOVERY_DELAY_MS = 1500L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        val nodeCtx = this.ctx
        if (nodeCtx == null) {
            kotlinx.coroutines.delay(RECOVERY_DELAY_MS)
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

            // ═══ 按优先级检测当前页面（一次 Mat 转换，所有匹配复用） ═══

            // 1. 结算弹窗 — 黑色遮罩 + 弹窗
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.SETTLEMENT_POPUP) != null) {
                nodeCtx.log("恢复: 检测到结算弹窗，切入结算节点")
                return GamePhase.SETTLEMENT
            }

            // 2. 结算确认按钮
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.CONFIRM_BUTTON) != null) {
                nodeCtx.log("恢复: 检测到确认按钮，切入结算节点")
                return GamePhase.SETTLEMENT
            }

            // 3. 队伍房间 — 准备按钮
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.READY_BUTTON) != null) {
                nodeCtx.log("恢复: 检测到准备按钮，切入悬赏详情")
                return GamePhase.BOUNTY_DETAIL
            }

            // 4. 战斗加载界面
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.BATTLE_LOADING) != null) {
                nodeCtx.log("恢复: 检测到战斗加载，切入加载节点")
                return GamePhase.BATTLE_LOADING
            }

            // 5. 战斗中 — 滑铲按钮
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.SLIDE_BUTTON) != null) {
                nodeCtx.log("恢复: 检测到战斗界面，切入战斗节点")
                return GamePhase.FIGHT
            }

            // 6. 战斗中 — 跳跃按钮
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.JUMP_BUTTON) != null) {
                nodeCtx.log("恢复: 检测到战斗界面，切入战斗节点")
                return GamePhase.FIGHT
            }

            // 7. 个人悬赏列表
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.PERSONAL_BOUNTY_LIST_SCREEN) != null) {
                nodeCtx.log("恢复: 检测到个人悬赏列表，切入个人悬赏中心")
                return GamePhase.PERSONAL_BOUNTY_CENTER
            }

            // 8. 招募列表 — 团队招募标签
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.RECRUIT_LIST_SCREEN) != null) {
                nodeCtx.log("恢复: 检测到招募列表页面，直接切入")
                return GamePhase.RECRUIT_LIST
            }

            // 9. 大厅 — 聊天按钮（左上角）
            if (nodeCtx.detector.matchTemplateMat(screenMat, ScreenState.CHAT_ICON) != null) {
                nodeCtx.log("恢复: 检测到大厅页面，切入大厅节点")
                return GamePhase.LOBBY
            }

            // 10. 无法识别 — 返回 IDLE 走默认导航
            nodeCtx.log("恢复: 无法识别当前页面，返回 IDLE 重新导航")
            return GamePhase.IDLE
        } finally {
            screenMat?.release()
            screen.recycle()
        }
    }
}

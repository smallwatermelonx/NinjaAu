package com.example.ninjaau.core

import com.example.ninjaau.core.recognition.TemplateMatcher
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import org.opencv.core.Mat

/**
 * 异常恢复工具 — 截图识别当前页面，返回应跳转的 GamePhase。
 *
 * 不是节点，不是阶段。WorkflowEngine 在节点超时或异常时调用，
 * 直接识别页面并路由，不需要经过中间状态。
 */
class RecoveryHandler(
    private val detector: com.example.ninjaau.core.recognition.SceneDetector,
    private val captureBitmap: suspend () -> android.graphics.Bitmap?,
    private val log: (String) -> Unit
) {
    /**
     * 截图识别当前页面，返回应跳转的 GamePhase。
     * 识别失败返回 IDLE（回大厅重新导航）。
     */
    suspend fun tryRecover(): GamePhase {
        val screen = captureBitmap()
        if (screen == null) {
            log("恢复: 截图失败，回大厅")
            return GamePhase.IDLE
        }

        var screenMat: Mat? = null
        try {
            screenMat = detector.screenToMat(screen)
            return identifyPage(screenMat)
        } finally {
            screenMat?.release()
            screen.recycle()
        }
    }

    private fun identifyPage(mat: Mat): GamePhase {
        // 1. 结算弹窗
        if (detector.matchTemplateMat(mat, ScreenState.SETTLEMENT_POPUP) != null) {
            log("恢复: 检测到结算弹窗 → SETTLEMENT")
            return GamePhase.SETTLEMENT
        }

        // 2. 失败界面
        if (detector.matchTemplateMat(mat, ScreenState.DEFEAT_SCREEN) != null) {
            log("恢复: 检测到失败界面 → DEFEAT")
            return GamePhase.DEFEAT
        }

        // 2.5 失败等待界面返回按钮（底部中心1/9）
        val defeatBackCrop = detector.cropBottomCenterNinth(mat)
        try {
            if (detector.matchTemplateMat(defeatBackCrop, ScreenState.DEFEAT_BACK_BUTTON) != null) {
                log("恢复: 检测到失败等待界面 → DEFEAT")
                return GamePhase.DEFEAT
            }
        } finally { defeatBackCrop.release() }

        // 2.6 助战按钮 → 观战面板（左下1/3）
        val assistCrop = detector.cropBottomLeftThird(mat)
        try {
            if (detector.matchTemplateMat(assistCrop, ScreenState.ASSIST_BUTTON) != null) {
                log("恢复: 检测到助战按钮（观战面板） → DEFEAT")
                return GamePhase.DEFEAT
            }
        } finally { assistCrop.release() }

        // 3. 确认按钮（底部中间1/5）
        val confirmCrop = detector.cropBottomMiddleFifth(mat)
        try {
            val confirmTemplate = detector.getTemplate(ScreenState.CONFIRM_BUTTON)
            if (confirmTemplate != null && TemplateMatcher.matchWithMat(confirmCrop, confirmTemplate, 0.8f).isMatched) {
                log("恢复: 检测到确认按钮 → SETTLEMENT")
                return GamePhase.SETTLEMENT
            }
        } finally { confirmCrop.release() }

        // 4. 战斗加载（上方1/4）
        val loadingCrop = detector.cropTopQuarter(mat)
        try {
            val loadingTemplate = detector.getTemplate(ScreenState.BATTLE_LOADING)
            if (loadingTemplate != null && TemplateMatcher.matchWithMat(loadingCrop, loadingTemplate, 0.8f).isMatched) {
                log("恢复: 检测到战斗加载 → BATTLE_LOADING")
                return GamePhase.BATTLE_LOADING
            }
        } finally { loadingCrop.release() }

        // 5. 准备按钮（上方1/8）
        val readyH = mat.rows() / 8
        val readyCrop = Mat(mat, org.opencv.core.Rect(0, 0, mat.cols(), readyH))
        try {
            val readyTemplate = detector.getTemplate(ScreenState.READY_BUTTON)
            if (readyTemplate != null && TemplateMatcher.matchWithMat(readyCrop, readyTemplate, 0.8f).isMatched) {
                log("恢复: 检测到准备按钮 → BOUNTY_DETAIL")
                return GamePhase.BOUNTY_DETAIL
            }
        } finally { readyCrop.release() }

        // 6. 战斗中 — 滑铲（左下1/4）
        val slideCrop = detector.cropBottomLeftQuarter(mat)
        try {
            val slideTemplate = detector.getTemplate(ScreenState.SLIDE_BUTTON)
            if (slideTemplate != null && TemplateMatcher.matchWithMat(slideCrop, slideTemplate, 0.8f).isMatched) {
                log("恢复: 检测到战斗界面 → FIGHT")
                return GamePhase.FIGHT
            }
        } finally { slideCrop.release() }

        // 7. 战斗中 — 跳跃（右下1/4）
        val jumpCrop = detector.cropBottomRightQuarter(mat)
        try {
            val jumpTemplate = detector.getTemplate(ScreenState.JUMP_BUTTON)
            if (jumpTemplate != null && TemplateMatcher.matchWithMat(jumpCrop, jumpTemplate, 0.8f).isMatched) {
                log("恢复: 检测到战斗界面 → FIGHT")
                return GamePhase.FIGHT
            }
        } finally { jumpCrop.release() }

        // 8. 个人悬赏列表
        if (detector.matchTemplateMat(mat, ScreenState.PERSONAL_BOUNTY_LIST_SCREEN) != null) {
            log("恢复: 检测到个人悬赏列表 → PERSONAL_BOUNTY_CENTER")
            return GamePhase.PERSONAL_BOUNTY_CENTER
        }

        // 9. 个人悬赏详情（团队邀请按钮区域）
        val teamInviteCrop = detector.cropPersonalBountyTeamInvite(mat)
        try {
            val teamInviteTemplate = detector.getTemplate(ScreenState.PERSONAL_BOUNTY_DETAIL_SCREEN)
            if (teamInviteTemplate != null && TemplateMatcher.matchWithMat(teamInviteCrop, teamInviteTemplate, 0.85f).isMatched) {
                log("恢复: 检测到个人悬赏详情 → PERSONAL_BOUNTY_DETAIL")
                return GamePhase.PERSONAL_BOUNTY_DETAIL
            }
        } finally { teamInviteCrop.release() }

        // 10. 个人悬赏出发按钮
        val goCrop = detector.cropPersonalBountyGo(mat)
        try {
            val goTemplate = detector.getTemplate(ScreenState.PERSONAL_BOUNTY_GO)
            if (goTemplate != null && TemplateMatcher.matchWithMat(goCrop, goTemplate, 0.85f).isMatched) {
                log("恢复: 检测到个人悬赏出发按钮 → PERSONAL_BOUNTY_DETAIL")
                return GamePhase.PERSONAL_BOUNTY_DETAIL
            }
        } finally { goCrop.release() }

        // 11. 招募列表
        if (detector.matchTemplateMat(mat, ScreenState.RECRUIT_LIST_SCREEN) != null) {
            log("恢复: 检测到招募列表 → RECRUIT_LIST")
            return GamePhase.RECRUIT_LIST
        }

        // 12. 大厅（左侧1/10）
        val chatCrop = detector.cropLeftTenth(mat)
        try {
            val chatTemplate = detector.getTemplate(ScreenState.CHAT_ICON)
            if (chatTemplate != null && TemplateMatcher.matchWithMat(chatCrop, chatTemplate, 0.75f).isMatched) {
                log("恢复: 检测到大厅 → LOBBY")
                return GamePhase.LOBBY
            }
        } finally { chatCrop.release() }

        // 无法识别
        log("恢复: 无法识别当前页面，回大厅重新导航")
        return GamePhase.IDLE
    }
}

package com.example.ninjaau.core.recognition

import android.content.Context
import android.view.WindowManager
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.uimap.UIMap
import com.example.ninjaau.core.util.*
import kotlinx.coroutines.delay

/**
 * 悬赏任务流程识别与执行器
 * 核心流程：大厅 → Chat图标 → 招募页签 → 加入队伍 → 准备按钮 → 下滑按钮 → 战斗WARNING → 奖励弹窗 → 确定按钮
 * 支持异常恢复：全局节点检测 + 最大3次恢复重试
 */
enum class BountyNode {
    HALL,          // 1. 大厅（起点）
    CHAT_ICON,     // 2. 大厅→Chat图标
    RECRUIT_TAB,   // 3. Chat→组队招募页签
    JOIN_TEAM,     // 4. 招募页→加入队伍按钮
    READY_BTN,     // 5. 组队页→准备按钮
    SLIDE_BUTTON,  // 6. 准备后→下滑按钮
    BATTLE_WARNING,// 7. 下滑后→战斗WARNING界面
    REWARD_POPUP,  // 8. 战斗后→奖励弹窗
    CONFIRM_BTN,   // 9. 奖励弹窗→确定按钮
    UNKNOWN        // 未知节点（无法识别）
}

class BountyRecognizer(private val context: Context) {
    // ===================== 常量定义（集中管理） =====================
    private val TAG = "BountyRecognizer"
    private val MAX_RECOVERY_COUNT = 3        // 最大异常恢复次数
    private val SLIDE_MAX_CHECK_RETRY = 30    // 下滑按钮最大检测次数（30次=30秒）
    private val SLIDE_CLICK_INTERVAL = 200L   // 下滑按钮点击间隔（0.2秒/次）
    private val SLIDE_TOTAL_DURATION = 5000L  // 下滑按钮持续点击时长（5秒）

    // ===================== 通用工具方法 =====================
    /**
     * 通用循环检测方法
     * @param checkLogic 检测逻辑（挂起函数）
     * @param checkInterval 检测间隔（默认1秒）
     * @param maxRetry 最大重试次数（默认不限）
     * @return 是否检测成功
     */
    private suspend fun loopCheck(
        checkLogic: suspend () -> Boolean,
        checkInterval: Long = 1000L,
        maxRetry: Int = Int.MAX_VALUE
    ): Boolean {
        var retryCount = 0
        while (retryCount < maxRetry) {
            retryCount++
            val isFound = checkLogic()
            if (isFound) {
                LogUtil.d(TAG, "循环检测成功（重试次数：$retryCount，间隔：$checkInterval ms）")
                return true
            }
            delay(checkInterval)
            LogUtil.d(TAG, "循环检测未找到目标（重试次数：$retryCount，间隔：$checkInterval ms）")
        }
        LogUtil.w(TAG, "循环检测超时（最大重试：$maxRetry，间隔：$checkInterval ms）")
        return false
    }

    // ===================== 全局节点检测 =====================
    /**
     * 全局检测当前界面匹配的流程节点（异常恢复核心）
     * 检测顺序：逆序检测（从最后一步→第一步），避免误判
     */
    private suspend fun detectCurrentNode(): BountyNode {
        LogUtil.i(TAG, "=== 触发全局节点检测，确认当前流程节点 ===")

        return when {
            checkConfirmButton().first -> {
                LogUtil.i(TAG, "全局检测结果：当前在【确定按钮】节点")
                BountyNode.CONFIRM_BTN
            }
            checkRewardPopup() -> {
                LogUtil.i(TAG, "全局检测结果：当前在【奖励弹窗】节点")
                BountyNode.REWARD_POPUP
            }
            checkWarningScreen() -> {
                LogUtil.i(TAG, "全局检测结果：当前在【战斗WARNING】节点")
                BountyNode.BATTLE_WARNING
            }
            checkSlideButton().first -> {
                LogUtil.i(TAG, "全局检测结果：当前在【下滑按钮】节点")
                BountyNode.SLIDE_BUTTON
            }
            checkReadyButton().first -> {
                LogUtil.i(TAG, "全局检测结果：当前在【准备按钮】节点")
                BountyNode.READY_BTN
            }
            checkJoinTeamButton().first -> {
                LogUtil.i(TAG, "全局检测结果：当前在【加入队伍】节点")
                BountyNode.JOIN_TEAM
            }
            checkTeamRecruitTab().first -> {
                LogUtil.i(TAG, "全局检测结果：当前在【招募页签】节点")
                BountyNode.RECRUIT_TAB
            }
            checkChatIconInHall().first -> {
                LogUtil.i(TAG, "全局检测结果：当前在【Chat图标】节点")
                BountyNode.CHAT_ICON
            }
            checkHallPage() -> {
                LogUtil.i(TAG, "全局检测结果：当前在【大厅】节点")
                BountyNode.HALL
            }
            else -> {
                LogUtil.w(TAG, "全局检测结果：未识别到任何节点，判定为【未知】")
                BountyNode.UNKNOWN
            }
        }
    }

    // ===================== 核心流程执行 =====================
    /**
     * 执行完整的悬赏任务流程
     * 支持异常自动恢复，最大恢复3次
     * @return 流程是否执行成功
     */
    suspend fun executeBountyProcess(): Boolean {
        var currentNode = BountyNode.HALL
        var recoveryCount = 0

        while (recoveryCount <= MAX_RECOVERY_COUNT) {
            try {
                currentNode = executeNodeStep(currentNode)
                // 流程执行到最后一步（确定按钮）则返回成功
                if (currentNode == BountyNode.CONFIRM_BTN && executeConfirmStep()) {
                    LogUtil.i(TAG, "🎉 所有步骤执行成功，流程结束")
                    return true
                }
            } catch (e: RuntimeException) {
                LogUtil.e(TAG, "❌ 步骤执行异常：${e.message}，触发第${recoveryCount+1}次恢复")
                currentNode = detectCurrentNode()
                recoveryCount++

                if (recoveryCount > MAX_RECOVERY_COUNT) {
                    LogUtil.e(TAG, "❌ 达到最大恢复次数（$MAX_RECOVERY_COUNT），流程终止")
                    return false
                }
                LogUtil.i(TAG, "📌 恢复后将从【$currentNode】节点重新执行")
                delay(1000)
            }
        }
        LogUtil.e(TAG, "❌ 流程执行失败，已达最大恢复次数")
        return false
    }

    /**
     * 执行单个节点的步骤逻辑
     * @param currentNode 当前节点
     * @return 下一个节点
     */
    private suspend fun executeNodeStep(currentNode: BountyNode): BountyNode {
        return when (currentNode) {
            BountyNode.HALL -> executeHallStep()
            BountyNode.CHAT_ICON -> executeChatIconStep()
            BountyNode.RECRUIT_TAB -> executeRecruitTabStep()
            BountyNode.JOIN_TEAM -> executeJoinTeamStep()
            BountyNode.READY_BTN -> executeReadyBtnStep()
            BountyNode.SLIDE_BUTTON -> executeSlideButtonStep()
            BountyNode.BATTLE_WARNING -> executeBattleWarningStep()
            BountyNode.REWARD_POPUP -> executeRewardPopupStep()
            BountyNode.CONFIRM_BTN -> {
                executeConfirmStep()
                BountyNode.CONFIRM_BTN // 标记为最后一步
            }
            BountyNode.UNKNOWN -> {
                LogUtil.w(TAG, "当前节点未知，重新全局检测...")
                val newNode = detectCurrentNode()
                if (newNode == BountyNode.UNKNOWN) {
                    throw RuntimeException("全局检测仍未识别节点，恢复失败")
                }
                newNode
            }
        }
    }

    // ===================== 各节点具体执行步骤 =====================
    /** 步骤1：检测大厅界面 */
    private suspend fun executeHallStep(): BountyNode {
        val hallResult = checkHallStep()
        if (hallResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤1（大厅）执行成功，进入下一个节点")
            delay(500)
            return BountyNode.CHAT_ICON
        } else {
            throw RuntimeException("步骤1失败：${hallResult.errorMsg}")
        }
    }

    /** 步骤2：点击Chat图标 */
    private suspend fun executeChatIconStep(): BountyNode {
        val chatResult = clickChatIconStep()
        if (chatResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤2（Chat图标）执行成功，进入下一个节点")
            delay(2000)
            return BountyNode.RECRUIT_TAB
        } else {
            throw RuntimeException("步骤2失败：${chatResult.errorMsg}")
        }
    }

    /** 步骤3：点击招募页签 */
    private suspend fun executeRecruitTabStep(): BountyNode {
        val recruitResult = clickRecruitTabStep()
        if (recruitResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤3（招募页签）执行成功，进入下一个节点")
            delay(1000)
            return BountyNode.JOIN_TEAM
        } else {
            throw RuntimeException("步骤3失败：${recruitResult.errorMsg}")
        }
    }

    /** 步骤4：点击加入队伍按钮 */
    private suspend fun executeJoinTeamStep(): BountyNode {
        val joinResult = clickJoinTeamBtnStep()
        if (joinResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤4（加入队伍）执行成功，进入下一个节点")
            delay(1500)
            return BountyNode.READY_BTN
        } else {
            throw RuntimeException("步骤4失败：${joinResult.errorMsg}")
        }
    }

    /** 步骤5：点击准备按钮 */
    private suspend fun executeReadyBtnStep(): BountyNode {
        val readyResult = clickReadyBtnStep()
        if (readyResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤5（准备按钮）执行成功，进入下一个节点")
            delay(1000)
            return BountyNode.SLIDE_BUTTON
        } else {
            throw RuntimeException("步骤5失败：${readyResult.errorMsg}")
        }
    }

    /** 步骤6：检测并点击下滑按钮 */
    private suspend fun executeSlideButtonStep(): BountyNode {
        val slideResult = clickSlideButtonStep()
        if (slideResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤6（下滑按钮）执行成功，进入下一个节点")
            return BountyNode.BATTLE_WARNING
        } else {
            throw RuntimeException("步骤6失败：${slideResult.errorMsg}")
        }
    }

    /** 步骤7：处理战斗WARNING界面 */
    private suspend fun executeBattleWarningStep(): BountyNode {
        val battleResult = handleBattleScreenStep()
        if (battleResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤7（战斗WARNING）执行成功，进入下一个节点")
            delay(1000)
            return BountyNode.REWARD_POPUP
        } else {
            throw RuntimeException("步骤7失败：${battleResult.errorMsg}")
        }
    }

    /** 步骤8：关闭奖励弹窗 */
    private suspend fun executeRewardPopupStep(): BountyNode {
        val rewardResult = clickBlankToCloseRewardStep()
        if (rewardResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤8（奖励弹窗）执行成功，进入下一个节点")
            delay(500)
            return BountyNode.CONFIRM_BTN
        } else {
            throw RuntimeException("步骤8失败：${rewardResult.errorMsg}")
        }
    }

    /** 步骤9：点击确定按钮（最后一步） */
    private suspend fun executeConfirmStep(): Boolean {
        val confirmResult = clickConfirmButtonStep()
        if (confirmResult.isSuccess) {
            LogUtil.i(TAG, "✅ 步骤9（确定按钮）执行成功")
            return true
        } else {
            throw RuntimeException("步骤9失败：${confirmResult.errorMsg}")
        }
    }

    // ===================== 基础检测与操作方法 =====================
    private fun checkHallStep(): StepResult<Boolean> {
        val isHall = checkHallPage()
        return if (isHall) {
            StepResult(isSuccess = true, data = true, errorMsg = null)
        } else {
            StepResult(isSuccess = false, data = false, errorMsg = "未检测到大厅界面")
        }
    }

    private suspend fun clickChatIconStep(): StepResult<Pair<Float, Float>> {
        val (isFound, coord) = checkChatIconInHall()
        return if (isFound && coord != null) {
            NinjaAccessibilityService.getInstance()?.clickAt(coord.first, coord.second)
            StepResult(isSuccess = true, data = coord, errorMsg = null)
        } else {
            StepResult(isSuccess = false, data = null, errorMsg = "未检测到Chat图标或坐标为空")
        }
    }

    private suspend fun clickRecruitTabStep(): StepResult<Pair<Float, Float>> {
        val (isFound, coord) = checkTeamRecruitTab()
        return if (isFound && coord != null) {
            NinjaAccessibilityService.getInstance()?.clickAt(coord.first, coord.second)
            StepResult(isSuccess = true, data = coord, errorMsg = null)
        } else {
            StepResult(isSuccess = false, data = null, errorMsg = "未检测到组队招募页签")
        }
    }

    private suspend fun clickJoinTeamBtnStep(): StepResult<Pair<Float, Float>> {
        val maxRetry = 24
        var retryCount = 0 // 用于日志记录重试次数
        var resultCoord: Pair<Float, Float>? = null // 存储成功点击的坐标

        // 使用通用循环检测方法替换自定义while循环
        val isSuccess = loopCheck(
            checkLogic = {
                retryCount++
                LogUtil.d(TAG, "第${retryCount}次检测加入队伍按钮...")

                // 1. 检测加入队伍按钮
                val (isJoinFound, joinCoord) = checkJoinTeamButton()
                if (isJoinFound && joinCoord != null) {
                    LogUtil.i(TAG, "第${retryCount}次检测到加入队伍按钮，点击坐标：$joinCoord")
                    NinjaAccessibilityService.getInstance()?.clickAt(joinCoord.first, joinCoord.second)

                    // 2. 点击后检测准备按钮
                    val (isReadyFound, _) = checkReadyButton()
                    if (isReadyFound) {
                        LogUtil.i(TAG, "点击加入队伍后检测到准备按钮，成功进入组队页")
                        resultCoord = joinCoord // 记录成功点击的坐标
                        return@loopCheck true // 检测成功，终止循环
                    } else {
                        LogUtil.w(TAG, "点击加入队伍后未检测到准备按钮，继续重试...")
                        // 原有逻辑的800ms延迟，这里通过loopCheck的checkInterval实现（见下方参数）
                        return@loopCheck false // 继续循环
                    }
                } else {
                    // 3. 未检测到加入队伍按钮，判定已进入组队页
                    LogUtil.i(TAG, "第${retryCount}次未检测到加入队伍按钮，判定已进入组队页")
                    return@loopCheck true // 检测成功，终止循环
                }
            },
            checkInterval = 200L, // 对应原有逻辑的delay(800)
            maxRetry = maxRetry
        )

        // 根据循环结果返回对应StepResult
        return if (isSuccess) {
            StepResult(isSuccess = true, data = resultCoord, errorMsg = null)
        } else {
            val errorMsg = "循环${maxRetry}次：既未稳定点击到加入队伍按钮，也未检测到准备按钮"
            LogUtil.e(TAG, errorMsg)
            StepResult(isSuccess = false, data = null, errorMsg = errorMsg)
        }
    }

    private suspend fun clickReadyBtnStep(): StepResult<Pair<Float, Float>> {
        var readyCoord: Pair<Float, Float>? = null

        val isReadyFound = loopCheck(
            checkLogic = {
                val (found, coord) = checkReadyButton()
                if (found) readyCoord = coord
                found
            },
            maxRetry = 5
        )

        return if (isReadyFound && readyCoord != null) {
            NinjaAccessibilityService.getInstance()?.clickAt(readyCoord!!.first, readyCoord!!.second)
            StepResult(isSuccess = true, data = readyCoord, errorMsg = null)
        } else {
            StepResult(isSuccess = false, data = null, errorMsg = "循环5次未检测到准备按钮")
        }
    }

    private suspend fun clickSlideButtonStep(): StepResult<Unit> {
        LogUtil.i(TAG, "开始循环检测下滑按钮（1秒/次，最大检测30秒）")

        // 循环检测下滑按钮
        var slideCoord: Pair<Float, Float>? = null
        val isSlideFound = loopCheck(
            checkLogic = {
                val (found, coord) = checkSlideButton()
                if (found) slideCoord = coord
                found
            },
            checkInterval = 500,
            maxRetry = SLIDE_MAX_CHECK_RETRY
        )

        // 超时未检测到按钮
        if (!isSlideFound || slideCoord == null) {
            val errorMsg = "循环${SLIDE_MAX_CHECK_RETRY}次（${SLIDE_MAX_CHECK_RETRY}秒）未检测到下滑按钮"
            LogUtil.e(TAG, errorMsg)
            return StepResult(isSuccess = false, errorMsg = errorMsg)
        }

        // 持续点击下滑按钮
        LogUtil.i(TAG, "检测到下滑按钮，开始持续点击5秒（0.2秒/次），坐标：$slideCoord")
        val startTime = System.currentTimeMillis()
        var clickCount = 0

        while (System.currentTimeMillis() - startTime < SLIDE_TOTAL_DURATION) {
            clickCount++
            val (isStillFound, currentCoord) = checkSlideButton()
            if (isStillFound && currentCoord != null) {
                NinjaAccessibilityService.getInstance()?.clickAt(currentCoord.first, currentCoord.second)
                LogUtil.d(TAG, "第${clickCount}次点击下滑按钮，坐标：$currentCoord")
            } else {
                LogUtil.w(TAG, "第${clickCount}次点击前检测不到下滑按钮，跳过本次点击")
            }
            delay(SLIDE_CLICK_INTERVAL)
        }

        LogUtil.i(TAG, "下滑按钮持续点击完成（共尝试${clickCount}次，实际有效点击次数以日志为准，时长${SLIDE_TOTAL_DURATION/1000}秒）")
        return StepResult(isSuccess = true, errorMsg = null)
    }

    private suspend fun handleBattleScreenStep(): StepResult<Unit> {
        val isWarningFound = loopCheck(
            checkLogic = { checkWarningScreen() },
            checkInterval = 100L,
            maxRetry = 24
        )

        if (!isWarningFound) {
            return StepResult(isSuccess = false, errorMsg = "未检测到战斗WARNING界面")
        }

        LogUtil.i(TAG, "检测到战斗WARNING界面，延迟3秒后准备点击目标图标")
        delay(3000)

        var targetCoord: Pair<Float, Float>? = null
        val isTargetFound = loopCheck(
            checkLogic = {
                val (found, coord) = checkBattleTargetIcon()
                if (found) targetCoord = coord
                found
            },
            maxRetry = 5
        )

        if (!isTargetFound || targetCoord == null) {
            return StepResult(isSuccess = false, errorMsg = "未检测到战斗目标图标或坐标为空")
        }

        LogUtil.i(TAG, "开始点击战斗目标图标，共点击24次，每次间隔500ms，坐标：$targetCoord")
        repeat(24) { clickCount ->
            NinjaAccessibilityService.getInstance()?.clickAt(targetCoord!!.first, targetCoord!!.second)
            LogUtil.d(TAG, "第${clickCount + 1}次点击目标图标，坐标：$targetCoord")
            if (clickCount < 23) delay(500)
        }

        LogUtil.i(TAG, "24次点击目标图标完成，直接进入下一轮流程")
        return StepResult(isSuccess = true, errorMsg = null)
    }

    private suspend fun clickBlankToCloseRewardStep(): StepResult<Unit> {
        val maxRetry = 24
        var retryCount = 0

        while (retryCount < maxRetry) {
            retryCount++
            val (isTextFound, textCoord) = checkRewardCloseText()

            if (isTextFound && textCoord != null) {
                LogUtil.i(TAG, "第${retryCount}次检测到「点击空白处关闭」文字，点击坐标：$textCoord")
                NinjaAccessibilityService.getInstance()?.clickAt(textCoord.first, textCoord.second)
                delay(500)

                if (!checkRewardPopup()) {
                    LogUtil.i(TAG, "点击关闭文字区域后弹窗成功关闭")
                    return StepResult(isSuccess = true, errorMsg = null)
                } else {
                    LogUtil.w(TAG, "点击关闭文字区域后弹窗未关闭，继续重试")
                    delay(800)
                }
            } else {
                LogUtil.i(TAG, "未检测到「点击空白处关闭」文字，判定弹窗已关闭")
                return StepResult(isSuccess = true, errorMsg = null)
            }
        }

        return StepResult(
            isSuccess = false, errorMsg = "循环${maxRetry}次点击关闭文字区域失败"
        )
    }

    private suspend fun clickConfirmButtonStep(): StepResult<Pair<Float, Float>> {
        val maxRetry = 24
        var retryCount = 0

        while (retryCount < maxRetry) {
            retryCount++
            val (isConfirmFound, confirmCoord) = checkConfirmButton()

            if (isConfirmFound && confirmCoord != null) {
                LogUtil.i(TAG, "第${retryCount}次检测到确定按钮，点击坐标：$confirmCoord")
                NinjaAccessibilityService.getInstance()?.clickAt(confirmCoord.first, confirmCoord.second)
                delay(500)

                val isConfirmClosed = !checkConfirmButton().first
                if (isConfirmClosed) {
                    return StepResult(isSuccess = true, data = confirmCoord, errorMsg = null)
                } else {
                    LogUtil.w(TAG, "点击确定按钮后弹窗未关闭，继续重试")
                    delay(800)
                }
            } else {
                LogUtil.w(TAG, "第${retryCount}次未检测到确定按钮")
                delay(800)
            }
        }
        return StepResult(isSuccess = false, errorMsg = "循环${maxRetry}次未检测到确定按钮")
    }

    // ===================== 界面元素检测方法 =====================
    fun checkHallPage(): Boolean {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，请先初始化")
            return false
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测大厅")
            return false
        }

        try {
            val hallTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Hall.HALL_MAIN.getAssetsPath())
            if (hallTemplate == null) {
                LogUtil.e(TAG, "大厅模板加载失败")
                return false
            }

            val hallResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, hallTemplate, UIMap.Hall.HALL_MAIN.threshold)
            val isHallPage = hallResult.isMatched

            hallTemplate.recycle()
            LogUtil.d(TAG, "大厅检测结果：$isHallPage")
            return isHallPage
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkChatIconInHall(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，请先初始化")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测chat图标")
            return Pair(false, null)
        }

        try {
            val chatTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Hall.HALL_CHAT.getAssetsPath())
            if (chatTemplate == null) {
                LogUtil.e(TAG, "chat图标模板加载失败")
                return Pair(false, null)
            }

            val chatResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, chatTemplate, UIMap.Hall.HALL_CHAT.threshold)
            val isChatFound = chatResult.isMatched
            val chatCoord = if (isChatFound) Pair(chatResult.centerX, chatResult.centerY) else null

            chatTemplate.recycle()
            LogUtil.d(TAG, "chat图标检测结果：$isChatFound，坐标：$chatCoord")
            return Pair(isChatFound, chatCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkTeamRecruitTab(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测组队招募页签")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测组队招募页签")
            return Pair(false, null)
        }

        try {
            val recruitTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Chat.TEAM_RECRUIT_TAB.getAssetsPath())
            if (recruitTemplate == null) {
                LogUtil.e(TAG, "组队招募页签模板加载失败")
                return Pair(false, null)
            }

            val recruitResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, recruitTemplate, 0.7f)
            val isRecruitFound = recruitResult.isMatched
            val recruitCoord = if (isRecruitFound) Pair(recruitResult.centerX, recruitResult.centerY) else null

            recruitTemplate.recycle()
            LogUtil.d(TAG, "组队招募页签检测结果：$isRecruitFound，坐标：$recruitCoord")
            return Pair(isRecruitFound, recruitCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkJoinTeamButton(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测加入队伍按钮")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测加入队伍按钮")
            return Pair(false, null)
        }

        try {
            val joinTeamTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Bounty.JOIN_TEAM_BUTTON.getAssetsPath())
            if (joinTeamTemplate == null) {
                LogUtil.e(TAG, "加入队伍按钮模板加载失败")
                return Pair(false, null)
            }

            val joinTeamResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, joinTeamTemplate, 0.8f)
            val isJoinTeamFound = joinTeamResult.isMatched
            val joinTeamCoord = if (isJoinTeamFound) Pair(joinTeamResult.centerX, joinTeamResult.centerY) else null

            joinTeamTemplate.recycle()
            LogUtil.d(TAG, "加入队伍按钮检测结果：$isJoinTeamFound，坐标：$joinTeamCoord")
            return Pair(isJoinTeamFound, joinTeamCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkReadyButton(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测准备按钮")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测准备按钮")
            return Pair(false, null)
        }

        try {
            val readyTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Team.READY_BUTTON.getAssetsPath())
            if (readyTemplate == null) {
                LogUtil.e(TAG, "准备按钮模板加载失败")
                return Pair(false, null)
            }

            val readyResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, readyTemplate, 0.8f)
            val isReadyFound = readyResult.isMatched
            val readyCoord = if (isReadyFound) Pair(readyResult.centerX, readyResult.centerY) else null

            readyTemplate.recycle()
            LogUtil.d(TAG, "准备按钮检测结果：$isReadyFound，坐标：$readyCoord")
            return Pair(isReadyFound, readyCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkSlideButton(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测下滑按钮")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测下滑按钮")
            return Pair(false, null)
        }

        try {
            val slideTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Battle.SLIDE_BUTTON.getAssetsPath())
            if (slideTemplate == null) {
                LogUtil.e(TAG, "下滑按钮模板加载失败")
                return Pair(false, null)
            }

            val slideResult = OpenCVTemplateMatcher.matchTemplate(
                fullScreen, slideTemplate, UIMap.Battle.SLIDE_BUTTON.threshold
            )
            val isSlideFound = slideResult.isMatched
            val slideCoord = if (isSlideFound) Pair(slideResult.centerX, slideResult.centerY) else null

            slideTemplate.recycle()
            LogUtil.d(TAG, "下滑按钮检测结果：$isSlideFound，坐标：$slideCoord")
            return Pair(isSlideFound, slideCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkWarningScreen(): Boolean {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测WARNING界面")
            return false
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测WARNING界面")
            return false
        }

        try {
            val warningTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Battle.WARNING_SCREEN.getAssetsPath())
            if (warningTemplate == null) {
                LogUtil.e(TAG, "WARNING界面模板加载失败")
                return false
            }

            val warningResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, warningTemplate, UIMap.Battle.WARNING_SCREEN.threshold)
            val isWarningFound = warningResult.isMatched

            warningTemplate.recycle()
            LogUtil.d(TAG, "WARNING界面检测结果：$isWarningFound")
            return isWarningFound
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkBattleTargetIcon(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测战斗目标图标")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测战斗目标图标")
            return Pair(false, null)
        }

        try {
            val targetTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Battle.NINJUTSU.getAssetsPath())
            if (targetTemplate == null) {
                LogUtil.e(TAG, "战斗目标图标模板加载失败")
                return Pair(false, null)
            }

            val targetResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, targetTemplate, UIMap.Battle.NINJUTSU.threshold)
            val isTargetFound = targetResult.isMatched
            val targetCoord = if (isTargetFound) Pair(targetResult.centerX, targetResult.centerY) else null

            targetTemplate.recycle()
            LogUtil.d(TAG, "战斗目标图标检测结果：$isTargetFound，坐标：$targetCoord")
            return Pair(isTargetFound, targetCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkRewardPopup(): Boolean {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测奖励弹窗")
            return false
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测奖励弹窗")
            return false
        }

        try {
            val rewardTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Reward.CLOSE_BLANK_TEXT.getAssetsPath())
            if (rewardTemplate == null) {
                LogUtil.e(TAG, "奖励弹窗模板加载失败")
                return false
            }

            val rewardResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, rewardTemplate, UIMap.Reward.CLOSE_BLANK_TEXT.threshold)
            val isPopupFound = rewardResult.isMatched

            rewardTemplate.recycle()
            LogUtil.d(TAG, "奖励弹窗检测结果：$isPopupFound")
            return isPopupFound
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkRewardCloseText(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测关闭文字区域")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测关闭文字区域")
            return Pair(false, null)
        }

        try {
            val closeTextTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Reward.CLOSE_BLANK_TEXT.getAssetsPath())
            if (closeTextTemplate == null) {
                LogUtil.e(TAG, "关闭文字模板加载失败")
                return Pair(false, null)
            }

            val closeTextResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, closeTextTemplate, UIMap.Reward.CLOSE_BLANK_TEXT.threshold)
            val isTextFound = closeTextResult.isMatched
            val textCoord = if (isTextFound) Pair(closeTextResult.centerX, closeTextResult.centerY) else null

            closeTextTemplate.recycle()
            LogUtil.d(TAG, "关闭文字区域检测结果：$isTextFound，坐标：$textCoord")
            return Pair(isTextFound, textCoord)
        } finally {
            fullScreen.recycle()
        }
    }

    fun checkConfirmButton(): Pair<Boolean, Pair<Float, Float>?> {
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，无法检测确定按钮")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败，无法检测确定按钮")
            return Pair(false, null)
        }

        try {
            val confirmTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Reward.CONFIRM_BUTTON.getAssetsPath())
            if (confirmTemplate == null) {
                LogUtil.e(TAG, "确定按钮模板加载失败")
                return Pair(false, null)
            }

            val confirmResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, confirmTemplate, UIMap.Reward.CONFIRM_BUTTON.threshold)
            val isConfirmFound = confirmResult.isMatched
            val confirmCoord = if (isConfirmFound) Pair(confirmResult.centerX, confirmResult.centerY) else null

            confirmTemplate.recycle()
            LogUtil.d(TAG, "确定按钮检测结果：$isConfirmFound，坐标：$confirmCoord")
            return Pair(isConfirmFound, confirmCoord)
        } finally {
            fullScreen.recycle()
        }
    }
}
package com.example.ninjaau.core.recognition

import android.content.Context
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.ActionResult
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.delay

class BountyExecutor(context: Context) {
    private val TAG = "BountyExecutor"
    private val capture = ScreenCapture.getInstance(context)
    private val detector = ScreenDetector(context)

    private val accessibility get() = NinjaAccessibilityService.getInstance()

    /**
     * @param detection  ScriptEngine 传入的检测结果（含坐标），避免重复截图匹配
     */
    suspend fun execute(detection: ScreenDetector.DetectionResult): ActionResult<ScreenState> {
        val state = detection.state
        val coord = detection.coord

        return when (state) {
            ScreenState.HALL_CHAT -> clickCoord(coord, ScreenState.RECRUIT_TAB, 2000)
            ScreenState.RECRUIT_TAB -> clickCoord(coord, ScreenState.JOIN_TEAM, 1000)
            ScreenState.JOIN_TEAM -> executeJoinTeam(coord)
            ScreenState.READY_BTN -> clickCoord(coord, ScreenState.SLIDE_BUTTON, 1000)
            ScreenState.SLIDE_BUTTON -> executeSlide()
            ScreenState.BATTLE_WARNING,
            ScreenState.BATTLE_TARGET -> executeBattle()
            ScreenState.REWARD_POPUP -> executeCloseReward()
            ScreenState.CONFIRM_BTN -> clickCoord(coord, ScreenState.HALL_CHAT, 500, "确定完成")
            ScreenState.BACK_BUTTON -> executeBack(coord)
            ScreenState.UNKNOWN -> ActionResult(false, ScreenState.UNKNOWN, "无法识别当前界面")
        }
    }

    /** 简单点击 + 等待，直接使用传入的坐标 */
    private suspend fun clickCoord(
        coord: Pair<Float, Float>?,
        nextState: ScreenState,
        waitMs: Long,
        msg: String? = null
    ): ActionResult<ScreenState> {
        if (coord == null) return ActionResult(false, nextState, "坐标为空")
        accessibility?.clickAt(coord.first, coord.second)
        delay(waitMs)
        return ActionResult(true, nextState, msg)
    }

    /** 加入队伍：需要循环检测 + 重试验证 */
    private suspend fun executeJoinTeam(joinCoord: Pair<Float, Float>?): ActionResult<ScreenState> {
        // 先点一次加入按钮
        if (joinCoord != null) {
            accessibility?.clickAt(joinCoord.first, joinCoord.second)
            delay(200)
        }

        repeat(24) {
            val screen = capture.capture() ?: return ActionResult(false, ScreenState.JOIN_TEAM, "截图失败")
            try {
                val joinFound = detector.matchScreen(screen, ScreenState.JOIN_TEAM)
                if (joinFound != null) {
                    accessibility?.clickAt(joinFound.first, joinFound.second)
                    delay(200)
                    // 再检查是否进入了准备页
                    val verifyScreen = capture.capture()
                    if (verifyScreen != null) {
                        try {
                            if (detector.matchScreen(verifyScreen, ScreenState.READY_BTN) != null) {
                                return ActionResult(true, ScreenState.READY_BTN)
                            }
                        } finally {
                            verifyScreen.recycle()
                        }
                    }
                } else {
                    return ActionResult(true, ScreenState.READY_BTN)
                }
            } finally {
                screen.recycle()
            }
            delay(800)
        }
        return ActionResult(false, ScreenState.JOIN_TEAM, "加入队伍重试耗尽")
    }

    /** 下滑：持续检测 + 点击 5 秒 */
    private suspend fun executeSlide(): ActionResult<ScreenState> {
        repeat(30) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    if (detector.matchScreen(screen, ScreenState.SLIDE_BUTTON) != null) return@repeat
                } finally {
                    screen.recycle()
                }
            }
            delay(1000)
        }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 5000) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    val coord = detector.matchScreen(screen, ScreenState.SLIDE_BUTTON)
                    if (coord != null) {
                        accessibility?.clickAt(coord.first, coord.second)
                    }
                } finally {
                    screen.recycle()
                }
            }
            delay(200)
        }
        return ActionResult(true, ScreenState.BATTLE_WARNING)
    }

    /** 战斗：检测 WARNING → 等待 3 秒 → 点击目标 24 次 */
    private suspend fun executeBattle(): ActionResult<ScreenState> {
        var warningFound = false
        repeat(24) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    if (detector.matchScreen(screen, ScreenState.BATTLE_WARNING) != null) {
                        warningFound = true; return@repeat
                    }
                } finally {
                    screen.recycle()
                }
            }
            delay(100)
        }
        if (!warningFound) return ActionResult(false, ScreenState.BATTLE_WARNING, "未检测到战斗WARNING")
        delay(3000)

        repeat(24) { i ->
            val screen = capture.capture()
            if (screen != null) {
                try {
                    val coord = detector.matchScreen(screen, ScreenState.BATTLE_TARGET)
                    if (coord != null) accessibility?.clickAt(coord.first, coord.second)
                } finally {
                    screen.recycle()
                }
            }
            if (i < 23) delay(500)
        }
        return ActionResult(true, ScreenState.REWARD_POPUP)
    }

    /** 关闭奖励弹窗 */
    private suspend fun executeCloseReward(): ActionResult<ScreenState> {
        repeat(24) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    val coord = detector.matchScreen(screen, ScreenState.REWARD_POPUP)
                    if (coord != null) {
                        accessibility?.clickAt(coord.first, coord.second)
                        delay(500)
                        val verify = capture.capture()
                        if (verify != null) {
                            try {
                                if (detector.matchScreen(verify, ScreenState.REWARD_POPUP) == null)
                                    return ActionResult(true, ScreenState.CONFIRM_BTN)
                            } finally {
                                verify.recycle()
                            }
                        }
                        delay(800)
                    } else {
                        return ActionResult(true, ScreenState.CONFIRM_BTN)
                    }
                } finally {
                    screen.recycle()
                }
            }
        }
        return ActionResult(false, ScreenState.REWARD_POPUP, "关闭奖励弹窗重试耗尽")
    }

    /** 返回按钮：点击后重新检测 */
    private suspend fun executeBack(coord: Pair<Float, Float>?): ActionResult<ScreenState> {
        if (coord != null) {
            accessibility?.clickAt(coord.first, coord.second)
            delay(2000)
        }
        return ActionResult(true, ScreenState.HALL_CHAT)
    }
}

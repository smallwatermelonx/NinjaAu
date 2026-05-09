package com.example.ninjaau.core.recognition

import android.content.Context
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.ActionResult
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.delay

class BountyExecutor(private val context: Context) {
    private val TAG = "BountyExecutor"
    private val detector = ScreenDetector(context)
    private val capture = ScreenCapture.getInstance(context)

    private val accessibility get() = NinjaAccessibilityService.getInstance()

    suspend fun execute(state: ScreenState): ActionResult<ScreenState> {
        return when (state) {
            ScreenState.HALL_CHAT -> clickAndProceed(state, ScreenState.RECRUIT_TAB, 2000)
            ScreenState.RECRUIT_TAB -> clickAndProceed(state, ScreenState.JOIN_TEAM, 1000)
            ScreenState.JOIN_TEAM -> executeJoinTeam()
            ScreenState.READY_BTN -> clickAndProceed(state, ScreenState.SLIDE_BUTTON, 1000)
            ScreenState.SLIDE_BUTTON -> executeSlide()
            ScreenState.BATTLE_WARNING -> executeBattle()
            ScreenState.BATTLE_TARGET -> executeBattle()
            ScreenState.REWARD_POPUP -> executeCloseReward()
            ScreenState.CONFIRM_BTN -> executeConfirm()
            ScreenState.BACK_BUTTON -> executeBack()
            ScreenState.UNKNOWN -> ActionResult(false, ScreenState.UNKNOWN, "无法识别当前界面")
        }
    }

    private suspend fun clickAndProceed(
        current: ScreenState,
        next: ScreenState,
        waitMs: Long
    ): ActionResult<ScreenState> {
        val screen = capture.capture() ?: return ActionResult(false, current, "截图失败")
        return try {
            val coord = detector.matchScreen(screen, current)
            if (coord != null) {
                accessibility?.clickAt(coord.first, coord.second)
                delay(waitMs)
                ActionResult(true, next)
            } else {
                ActionResult(false, next, "未检测到${current.description}")
            }
        } finally {
            screen.recycle()
        }
    }

    private suspend fun executeJoinTeam(): ActionResult<ScreenState> {
        repeat(24) { attempt ->
            val screen = capture.capture() ?: return ActionResult(false, ScreenState.JOIN_TEAM, "截图失败")
            try {
                val joinCoord = detector.matchScreen(screen, ScreenState.JOIN_TEAM)
                if (joinCoord != null) {
                    accessibility?.clickAt(joinCoord.first, joinCoord.second)
                    delay(200)
                    val readyCoord = detector.matchScreen(screen, ScreenState.READY_BTN)
                    if (readyCoord != null) {
                        return ActionResult(true, ScreenState.READY_BTN)
                    }
                } else {
                    // 未检测到加入按钮，说明已在组队页
                    return ActionResult(true, ScreenState.READY_BTN)
                }
            } finally {
                screen.recycle()
            }
        }
        return ActionResult(false, ScreenState.JOIN_TEAM, "加入队伍重试耗尽")
    }

    private suspend fun executeSlide(): ActionResult<ScreenState> {
        // 检测下滑按钮（最多30次）
        var coord: Pair<Float, Float>? = null
        repeat(30) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    coord = detector.matchScreen(screen, ScreenState.SLIDE_BUTTON)
                } finally {
                    screen.recycle()
                }
            }
            if (coord != null) return@repeat
            delay(1000)
        }
        if (coord == null) return ActionResult(false, ScreenState.SLIDE_BUTTON, "未检测到下滑按钮")

        // 持续点击5秒
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 5000) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    val currentCoord = detector.matchScreen(screen, ScreenState.SLIDE_BUTTON)
                    if (currentCoord != null) {
                        accessibility?.clickAt(currentCoord.first, currentCoord.second)
                    }
                } finally {
                    screen.recycle()
                }
            }
            delay(200)
        }
        return ActionResult(true, ScreenState.BATTLE_WARNING)
    }

    private suspend fun executeBattle(): ActionResult<ScreenState> {
        // 检测WARNING画面
        var warningFound = false
        repeat(24) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    if (detector.matchScreen(screen, ScreenState.BATTLE_WARNING) != null) {
                        warningFound = true
                        return@repeat
                    }
                } finally {
                    screen.recycle()
                }
            }
            delay(100)
        }
        if (!warningFound) return ActionResult(false, ScreenState.BATTLE_WARNING, "未检测到战斗WARNING")

        delay(3000)

        // 检测并点击目标图标24次
        repeat(24) { i ->
            val screen = capture.capture()
            if (screen != null) {
                try {
                    val coord = detector.matchScreen(screen, ScreenState.BATTLE_TARGET)
                    if (coord != null) {
                        accessibility?.clickAt(coord.first, coord.second)
                    }
                } finally {
                    screen.recycle()
                }
            }
            if (i < 23) delay(500)
        }
        return ActionResult(true, ScreenState.REWARD_POPUP)
    }

    private suspend fun executeCloseReward(): ActionResult<ScreenState> {
        repeat(24) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    val coord = detector.matchScreen(screen, ScreenState.REWARD_POPUP)
                    if (coord != null) {
                        accessibility?.clickAt(coord.first, coord.second)
                        delay(500)
                        // 验证弹窗是否关闭
                        val verifyScreen = capture.capture()
                        if (verifyScreen != null) {
                            try {
                                if (detector.matchScreen(verifyScreen, ScreenState.REWARD_POPUP) == null) {
                                    return ActionResult(true, ScreenState.CONFIRM_BTN)
                                }
                            } finally {
                                verifyScreen.recycle()
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

    private suspend fun executeConfirm(): ActionResult<ScreenState> {
        var coord: Pair<Float, Float>? = null
        repeat(10) {
            val screen = capture.capture()
            if (screen != null) {
                try {
                    coord = detector.matchScreen(screen, ScreenState.CONFIRM_BTN)
                } finally {
                    screen.recycle()
                }
            }
            if (coord != null) {
                accessibility?.clickAt(coord!!.first, coord!!.second)
                delay(500)
                return ActionResult(true, ScreenState.HALL_CHAT, "确认完成")
            }
            delay(1000)
        }
        return ActionResult(false, ScreenState.CONFIRM_BTN, "未检测到确定按钮")
    }

    private suspend fun executeBack(): ActionResult<ScreenState> {
        val screen = capture.capture() ?: return ActionResult(false, ScreenState.BACK_BUTTON, "截图失败")
        return try {
            val coord = detector.matchScreen(screen, ScreenState.BACK_BUTTON)
            if (coord != null) {
                accessibility?.clickAt(coord.first, coord.second)
                delay(2000)
            }
            ActionResult(true, ScreenState.HALL_CHAT)
        } finally {
            screen.recycle()
        }
    }
}

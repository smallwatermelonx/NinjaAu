package com.example.ninjaau.core

import android.content.Context
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.recognition.BountyExecutor
import com.example.ninjaau.core.recognition.ScreenDetector
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class ScriptEngine(private val context: Context) {
    private val TAG = "ScriptEngine"
    private val capture = ScreenCapture.getInstance(context)
    private val detector = ScreenDetector(context)
    private val executor = BountyExecutor(context)

    companion object {
        private const val MAX_RECOVERY = 3
        private const val SCREENSHOT_FAIL_BACKOFF = 5000L  // 连续截图失败时的退避等待
    }

    suspend fun runLoop(bountyConfigs: List<BountyConfig>): Boolean {
        LogUtil.i(TAG, "=== 开始悬赏自动化循环 ===")
        var recoveryCount = 0
        var consecutiveFailures = 0  // 连续截图失败计数

        while (coroutineContext.isActive && recoveryCount <= MAX_RECOVERY) {
            val screen = capture.capture()
            if (screen == null) {
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    LogUtil.e(TAG, "连续 $consecutiveFailures 次截图失败，等待 ${SCREENSHOT_FAIL_BACKOFF / 1000}s 后重试")
                    delay(SCREENSHOT_FAIL_BACKOFF)
                    consecutiveFailures = 0
                } else {
                    delay(1000)
                }
                continue
            }

            consecutiveFailures = 0

            try {
                val detection = detector.detect(screen)
                LogUtil.i(TAG, "当前界面: ${detection.state.description}")

                if (detection.state == ScreenState.UNKNOWN) {
                    recoveryCount++
                    LogUtil.w(TAG, "无法识别界面，恢复尝试 $recoveryCount/$MAX_RECOVERY")
                    delay(1000)
                    continue
                }

                recoveryCount = 0
                val result = executor.execute(detection)

                if (!result.success) {
                    LogUtil.w(TAG, "步骤执行失败: ${result.message}")
                    if (result.data == ScreenState.UNKNOWN) recoveryCount++
                }

                if (detection.state == ScreenState.CONFIRM_BTN && result.success) {
                    LogUtil.i(TAG, "本轮悬赏流程完成")
                    return true
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "循环异常: ${e.message}", e)
                recoveryCount++
            } finally {
                screen.recycle()
            }

            delay(1000)
        }

        LogUtil.w(TAG, "自动化循环结束，恢复次数: $recoveryCount")
        return false
    }
}

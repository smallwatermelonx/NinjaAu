package com.example.ninjaau.core

import android.content.Context
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.recognition.BountyExecutor
import com.example.ninjaau.core.recognition.ScreenDetector
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.ActionResult
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

    private val maxRecoveryCount = 3

    suspend fun runLoop(bountyConfigs: List<BountyConfig>): Boolean {
        LogUtil.i(TAG, "=== 开始悬赏自动化循环 ===")
        var recoveryCount = 0

        while (coroutineContext.isActive && recoveryCount <= maxRecoveryCount) {
            val screen = capture.capture()
            if (screen == null) {
                LogUtil.w(TAG, "截图失败，等待重试")
                delay(1000)
                continue
            }

            try {
                val detection = detector.detect(screen)
                LogUtil.i(TAG, "当前界面: ${detection.state.description}")

                if (detection.state == ScreenState.UNKNOWN) {
                    recoveryCount++
                    LogUtil.w(TAG, "无法识别界面，恢复尝试 $recoveryCount/$maxRecoveryCount")
                    delay(1000)
                    continue
                }

                recoveryCount = 0
                val result = executor.execute(detection.state)

                if (!result.success) {
                    LogUtil.w(TAG, "步骤执行失败: ${result.message}")
                    if (result.data == ScreenState.UNKNOWN) {
                        recoveryCount++
                    }
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

        LogUtil.w(TAG, "自动化循环结束，恢复次数耗尽: $recoveryCount")
        return false
    }
}

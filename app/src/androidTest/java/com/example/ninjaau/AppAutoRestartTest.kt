package com.example.ninjaau

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ninjaau.core.AppMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppAutoRestartTest {

    // 目标APP的包名（替换成你要监控的APP包名）
    private val TARGET_APP_PACKAGE = "com.pandadagames.ninja.global"
    // 检测间隔（5秒）
    private val CHECK_INTERVAL = 5000L
    // 测试总时长（10分钟，避免无限循环）
    private val TOTAL_TEST_DURATION = TimeUnit.MINUTES.toMillis(10)

    private val appMonitor by lazy {
        AppMonitor(
            context = ApplicationProvider.getApplicationContext(), // 获取全局上下文
            targetPackageName = TARGET_APP_PACKAGE
        )
    }

    @Test
    fun testAutoRestartWhenCrash(){
        runBlocking {
            Log.d(
                "AppMonitor",
                "开始监控APP：$TARGET_APP_PACKAGE，持续${TOTAL_TEST_DURATION / 1000}秒"
            )
            val startTime = System.currentTimeMillis()

            // 循环监控，直到达到总时长
            while (System.currentTimeMillis() - startTime < TOTAL_TEST_DURATION) {
                val isRunning = appMonitor.isAppRunning()
                if (!isRunning) {
                    Log.d("AppMonitor", "APP已崩溃，尝试重启...")
                    val restartSuccess = appMonitor.restartApp()
                    Log.d("AppMonitor", "重启结果：${if (restartSuccess) "成功" else "失败"}")
                } else {
                    Log.d("AppMonitor", "APP正常运行中")
                }
                delay(CHECK_INTERVAL) // 间隔指定时间后再次检测
            }

            Log.d("AppMonitor", "监控结束")
        }
    }
}
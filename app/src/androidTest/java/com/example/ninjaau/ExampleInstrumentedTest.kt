package com.example.ninjaau

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ninjaau.core.AppMonitor // 导入AppMonitor类
import com.example.ninjaau.core.GameManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private val CHECK_INTERVAL = 10000L // 10秒检测间隔
    private val TOTAL_TEST_DURATION = 600000L // 测试总时长：10分钟（600000毫秒）
    private val TARGET_APP_PACKAGE = "com.example.ninjaau" // 要监控的APP包名（替换成实际包名）

    // 初始化appMonitor
    private val appMonitor by lazy {
        AppMonitor(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            targetPackageName = TARGET_APP_PACKAGE
        )
    }

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.ninjaau", appContext.packageName)
    }

    @Test
    fun startAutoRestartLogic() {
        runBlocking {
            Log.d("AutoRestartTest", "开始监控游戏状态，持续${TOTAL_TEST_DURATION/1000}秒")
            val startTime = System.currentTimeMillis()

            // 循环监控，直到达到总时长
            while (System.currentTimeMillis() - startTime < TOTAL_TEST_DURATION) {
                val isRunning = appMonitor.isAppRunning() // 现在appMonitor已定义
                if (!isRunning) {
                    Log.d("AutoRestartTest", "APP未运行，尝试启动...")
                    val success = appMonitor.restartApp() // 调用重启方法
                    Log.d("AutoRestartTest", "启动结果：${if (success) "成功" else "失败"}")
                } else {
                    Log.d("AutoRestartTest", "APP正在运行")
                }
                delay(CHECK_INTERVAL) // 间隔10秒再次检测
            }

            Log.d("AutoRestartTest", "监控结束")
        }
    }
}
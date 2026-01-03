package com.example.ninjaau

import com.example.ninjaau.core.GameManager
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    private val gameManager = GameManager()
    private val CHECK_INTERVAL = 10000L // 10秒检测间隔

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun startAutoRestartLogic() = runBlocking {
        // 模拟AutoRestartService的监控逻辑
        println("开始手动测试：监控游戏状态")

        while (true) { // 无限循环检测
            val isRunning = gameManager.isGameRunning()
            if (!isRunning) {
                println("游戏未运行，尝试启动...")
                val success = gameManager.launchGame()
                println("启动结果：${if (success) "成功" else "失败"}")
            } else {
                println("游戏正在运行")
            }

            // 等待下一次检测
            kotlinx.coroutines.delay(CHECK_INTERVAL)
        }
    }
}
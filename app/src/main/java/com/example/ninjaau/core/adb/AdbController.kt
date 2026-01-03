package com.example.ninjaau.core.adb

import com.example.ninjaau.core.util.LogUtil
import java.io.BufferedReader
import java.io.InputStreamReader

object AdbController {
    private const val TAG = "AdbController"

    // 执行ADB命令并返回结果
    fun executeAdbCommand(command: String): String? {
        return try {
            // 直接执行adb命令（模拟器内可直接调用adb）
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            // 读取输出结果
            val result = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result.append(line).append("\n")
            }

            // 读取错误信息（用于调试）
            val error = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
            if (error.isNotEmpty()) {
                LogUtil.e(TAG, "ADB命令错误: $error")
            }

            process.waitFor()
            result.toString().trim()
        } catch (e: Exception) {
            LogUtil.e(TAG, "执行ADB命令失败: ${e.message}")
            null
        }
    }

    // 启动指定包名的应用
    fun launchApp(packageName: String, mainActivity: String = ".MainActivity"): Boolean {
        val command = "adb shell am start -n $packageName/$mainActivity"
        val result = executeAdbCommand(command)
        return result?.contains("Starting: Intent") == true
    }

    // 强制停止应用
    fun stopApp(packageName: String): Boolean {
        val command = "adb shell am force-stop $packageName"
        val result = executeAdbCommand(command)
        return result.isNullOrEmpty() // 成功执行时通常无输出
    }

    // 检查应用是否在运行
    fun isAppRunning(packageName: String): Boolean {
        val command = "adb shell ps | grep $packageName"
        val result = executeAdbCommand(command)
        return result?.contains(packageName) == true
    }
}

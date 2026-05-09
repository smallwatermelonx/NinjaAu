package com.example.ninjaau.core.util

import android.app.ActivityManager
import android.content.Context
import com.example.ninjaau.core.GameManager

object AppControl {
    private const val TAG = "AppControl"

    fun launchScript(context: Context): Boolean {
        return try {
            GameManager.startScript(context)
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "启动脚本失败: ${e.message}", e)
            false
        }
    }

    fun isAppRunning(context: Context, packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.any { it.processName == packageName } ?: false
        } catch (e: Exception) {
            LogUtil.e(TAG, "检查应用运行状态失败: ${e.message}", e)
            false
        }
    }
}

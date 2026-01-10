package com.example.ninjaau.core.appcontrol

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.util.LogUtil

/**
 * 改用Android原生API实现应用控制（彻底抛弃设备内adb调用）
 */
object AdbController {
    private const val TAG = "AdbController"

    /**
     * 启动应用（原生Intent方式，稳定无依赖）
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                // 加FLAG确保启动新任务（后台启动也能生效）
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                LogUtil.i(TAG, "成功启动应用: $packageName")
                GameManager.startScript(context);
                true
            } else {
                LogUtil.e(TAG, "未找到应用的启动入口（包名错误）: $packageName")
                false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "启动应用失败: ${e.message}", e)
            false
        }
    }

    /**
     * 停止应用（原生ActivityManager方式）
     * 注意：Android 10+需要系统权限，若普通应用无法调用，可改用"启动应用+按返回键"模拟停止
     */
    @RequiresPermission(Manifest.permission.KILL_BACKGROUND_PROCESSES)
    fun stopApp(context: Context, packageName: String): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            activityManager.killBackgroundProcesses(packageName)
            LogUtil.i(TAG, "成功停止应用: $packageName")
            true
        } catch (e: SecurityException) {
            LogUtil.e(TAG, "停止应用需要系统权限，请在模拟器/root设备中运行", e)
            false
        } catch (e: Exception) {
            LogUtil.e(TAG, "停止应用失败: ${e.message}", e)
            false
        }
    }

    /**
     * 检查应用是否在运行（原生ActivityManager方式）
     */
    fun isAppRunning(context: Context, packageName: String): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activityManager.runningAppProcesses
            } else {
                @Suppress("DEPRECATION")
                activityManager.runningAppProcesses
            } ?: return false

            runningProcesses.any { it.processName == packageName }
        } catch (e: Exception) {
            LogUtil.e(TAG, "检查应用运行状态失败: ${e.message}", e)
            false
        }
    }

    /**
     * 检测应用是否在前台（原生ActivityManager方式）
     */
    fun isAppInForeground(context: Context, packageName: String): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses ?: return false

            runningAppProcesses.any {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && it.processName == packageName
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "检测前台失败: ${e.message}", e)
            false
        }
    }
}

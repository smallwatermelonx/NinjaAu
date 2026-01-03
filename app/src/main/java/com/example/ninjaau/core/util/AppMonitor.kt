package com.example.ninjaau.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class AppMonitor(private val context: Context, private val targetPackageName: String) {

    // 检测目标APP是否正在运行（进程是否存在）
    fun isAppRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false

        // 打印所有进程信息（方便调试，看目标APP的真实进程名）
        Log.d("ProcessDebug", "=== 检测到的进程列表 ===")
        runningProcesses.forEach { process ->
            Log.d("ProcessDebug", "进程名：${process.processName} | 关联包名：${process.pkgList.joinToString()}")
        }

        // 优化判断逻辑：
        // 1. 进程名是否以目标包名开头（覆盖自定义进程，如 com.xxx:xxx）
        // 2. 进程关联的包名列表中是否包含目标包名（确保该进程属于目标APP）
        val isRunning = runningProcesses.any { process ->
            val processNameMatches = process.processName.startsWith(targetPackageName)
            val pkgListContains = process.pkgList.contains(targetPackageName)
            processNameMatches && pkgListContains
        }

        Log.d("ProcessDebug", "目标APP（$targetPackageName）是否运行？$isRunning")
        return isRunning
    }

    // 重启目标APP（启动其主Activity）
    // 修改 AppMonitor.kt 中的 restartApp() 方法
    fun restartApp(): Boolean {
        Log.d("AppLaunchDebug", "开始尝试启动APP：$targetPackageName")
        return try {
            // 1. 打印是否能获取到启动意图
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackageName)
            if (launchIntent == null) {
                Log.e("AppLaunchDebug", "启动失败：无法获取 $targetPackageName 的启动意图（可能包名错误或APP未安装）")
                return false
            }
            Log.d("AppLaunchDebug", "成功获取启动意图，准备启动...")

            // 2. 配置启动参数
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d("AppLaunchDebug", "启动参数配置完成，即将调用 startActivity")

            // 3. 执行启动
            context.startActivity(launchIntent)
            Log.d("AppLaunchDebug", "startActivity 调用成功，已尝试启动 $targetPackageName")
            true
        } catch (e: Exception) {
            // 4. 捕获启动时的异常（如权限不足、系统限制等）
            Log.e("AppLaunchDebug", "启动过程抛出异常：${e.message}", e)
            false
        }
    }
}
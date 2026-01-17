package com.example.ninjaau.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.app.ActivityManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.ninjaau.R
import com.example.ninjaau.core.appcontrol.AdbController
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 简化版：只检测游戏进程是否挂掉，挂掉则重启（无前台检测、无权限依赖）
 */
class AutoRestartService : Service() {
    private val TAG = "AutoRestartService"
    private var isMonitoring = false
    private val CHECK_INTERVAL = 10000L // 10秒检测一次进程是否存活
    private val CHANNEL_ID = "game_monitor_channel"
    // 独立协程域，避免阻塞主流程
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 启动前台服务，避免被系统杀死（Android8+必须）
        val notification = createNotification("游戏进程监控中...")
        startForeground(1, notification)
        LogUtil.i(TAG, "AutoRestartService 已创建（仅监控进程存活）")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            startMonitoring()
        }
        // 服务被杀死后自动重启
        return START_REDELIVER_INTENT
    }

    private fun startMonitoring() {
        isMonitoring = true
        LogUtil.i(TAG, "开始10秒循环检测：仅检查游戏进程是否存活")

        serviceScope.launch {
            while (isMonitoring) {
                try {
                    val packageName = Constant.NINJA_GAME_PACKAGE
                    // 只检测：游戏进程是否存在（无前台检测、无权限依赖）
                    val isProcessRunning = isGameProcessRunning(packageName)

                    if (!isProcessRunning) {
                        // 仅在进程完全挂掉时重启，无任何前台操作
                        LogUtil.i(TAG, "检测到游戏进程已挂掉 → 重启游戏：$packageName")
                        val startSuccess = AdbController.launchApp(applicationContext, packageName)
                        val msg = if (startSuccess) "游戏进程已重启" else "游戏重启失败"
                        LogUtil.i(TAG, msg)
                        updateNotification(msg)

                        // 重启之后自动检测登录页面模拟登录
                        GameManager.startScript(applicationContext)
                    } else {
                        LogUtil.i(TAG, "游戏进程正常运行（无需操作）")
                        updateNotification("游戏进程运行正常")
                    }
                } catch (e: Exception) {
                    // 捕获异常，避免循环终止
                    LogUtil.e(TAG, "进程检测异常", e)
                }
                delay(CHECK_INTERVAL) // 10秒检测一次
            }
        }
    }

    /**
     * 仅检测游戏进程是否存活（无任何权限依赖，Android全版本通用）
     */
    private fun isGameProcessRunning(packageName: String): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        // 遍历所有运行的进程，判断包名是否匹配
        for (process in am.runningAppProcesses) {
            // processName 对应应用包名，uid排除自身进程
            if (process.processName == packageName && process.uid != Process.myUid()) {
                return true
            }
        }
        return false
    }

    // 以下为通知相关逻辑，无修改（保持前台服务状态）
    private fun stopMonitoring() {
        isMonitoring = false
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
        LogUtil.i(TAG, "停止进程监控，服务销毁")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "游戏进程监控服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("忍三自动化")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher) // 用你自己的图标，或替换为系统图标
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        LogUtil.i(TAG, "AutoRestartService 已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
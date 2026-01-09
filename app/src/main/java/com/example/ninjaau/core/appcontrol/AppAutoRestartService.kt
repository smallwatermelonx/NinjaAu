package com.example.ninjaau.core.appcontrol

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.ninjaau.core.util.LogUtil

/**
 * 应用重启后台服务（整合：后台运行+持续监控进程/前台状态）
 */
class AppAutoRestartService : Service() {
    private val TAG = "AppAutoRestartService"
    private lateinit var packageName: String // 要监控的应用包名（忍三）
    private val handler = Handler(Looper.getMainLooper())
    private val monitorInterval = 30000L // 监控间隔：30秒

    // 监控任务（循环检测）
    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                // 1. 先查进程是否存在
                val isRunning = AdbController.isAppRunning(
                    applicationContext, packageName
                ) // 加applicationContext
                if (!isRunning) {
                    LogUtil.i(TAG, "忍三进程已消失，重启应用")
                    AdbController.launchApp(applicationContext, packageName) // 加applicationContext
                } else {
                    val isForeground = AdbController.isAppInForeground(
                        applicationContext, packageName
                    ) // 加applicationContext
                    if (!isForeground) {
                        LogUtil.i(TAG, "忍三在后台")
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "监控出错: ${e.message}")
            }
            // 循环执行：每隔30秒查一次
            handler.postDelayed(this, monitorInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 从Intent获取要监控的包名（启动服务时传入）
        packageName = intent?.getStringExtra("PACKAGE_NAME") ?: ""
        if (packageName.isEmpty()) {
            LogUtil.e(TAG, "未传入监控包名，服务停止")
            stopSelf()
            return START_NOT_STICKY
        }

        // 新增：Android 8.0+启动前台通知（必须）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationChannel = android.app.NotificationChannel(
                "NINJA_MONITOR", "忍三监控服务", android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val notification = android.app.Notification.Builder(this, "NINJA_MONITOR")
                .setContentTitle("忍三监控中").setContentText("正在监控忍三进程状态")
                .setSmallIcon(android.R.drawable.ic_media_play) // 替换成你的图标
                .build()
            startForeground(1, notification) // 启动前台服务
        }

        // 启动持续监控（融入AppProcessMonitor的startMonitoring逻辑）
        LogUtil.i(TAG, "开始监控忍三进程，间隔${monitorInterval / 1000}秒")
        handler.post(monitorRunnable)

        return START_STICKY // 服务被杀后自动重启
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止监控（融入AppProcessMonitor的stopMonitoring逻辑）
        handler.removeCallbacks(monitorRunnable)
        LogUtil.i(TAG, "停止监控忍三进程")
    }
}
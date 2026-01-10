package com.example.ninjaau.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.ninjaau.R
import com.example.ninjaau.core.appcontrol.AdbController
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自动检测与重启服务
 */
class AutoRestartService : Service() {
    private val TAG = "AutoRestartService"
    private var isMonitoring = false
    private val CHECK_INTERVAL = 10000L // 10秒

    private val CHANNEL_ID = "game_monitor_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        isMonitoring = true
        LogUtil.d(TAG, "开始自动循环检测")

        val notification = createNotification("自动检测中...")
        startForeground(1, notification)

        CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoring) {
                // 核心修复：直接调用单例 GameManager 检查游戏运行状态
                val isRunning = GameManager.isGameRunning(applicationContext)

                if (!isRunning) {
                    LogUtil.d(TAG, "检测到游戏未运行，尝试启动...")
                    // 核心修复：调用 AdbController 启动游戏
                    val startSuccess = AdbController.launchApp(applicationContext, Constant.NINJA_GAME_PACKAGE)
                    if (startSuccess) {
                        updateNotification("游戏已启动")
                    } else {
                        updateNotification("游戏启动失败")
                    }
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "游戏自动检测服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("忍者自动脚本")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

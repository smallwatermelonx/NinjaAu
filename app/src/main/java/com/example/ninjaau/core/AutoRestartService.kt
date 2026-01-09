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
import com.example.ninjaau.core.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoRestartService : Service() {
    private val TAG = "AutoRestartService"
    private lateinit var gameManager: GameManager
    private var isMonitoring = false // 循环检测开关
    private val CHECK_INTERVAL = 10000L // 检测间隔（10秒）

    // 通知渠道配置（Android 8.0+必填）
    private val CHANNEL_ID = "game_monitor_channel"

    override fun onCreate() {
        super.onCreate()
        // 核心修改：传入Service的applicationContext（避免内存泄漏）
        gameManager = GameManager(applicationContext)
        createNotificationChannel()
    }

    // 服务启动时自动开始循环检测
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            startMonitoring() // 自动开始循环检测
        }
        return START_STICKY // 服务被杀死后自动重启
    }

    // 循环检测逻辑
    private fun startMonitoring() {
        isMonitoring = true
        LogUtil.d(TAG, "开始自动循环检测（间隔${CHECK_INTERVAL/1000}秒）")

        // 启动前台服务（必须显示通知，否则Android 8.0+会杀死服务）
        val notification = createNotification("自动检测中...")
        startForeground(1, notification)

        // 用协程在后台循环执行（避免阻塞主线程）
        CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoring) {
                // 1. 检测游戏状态
                val isRunning = gameManager.isGameRunning()

                // 2. 如果游戏未运行，启动游戏
                if (!isRunning) {
                    LogUtil.d(TAG, "检测到游戏未运行，尝试启动...")
                    val startSuccess = gameManager.launchGame()
                    if (startSuccess) {
                        updateNotification("游戏已启动")
                        LogUtil.d(TAG, "游戏启动成功")
                    } else {
                        updateNotification("游戏启动失败")
                        LogUtil.e(TAG, "游戏启动失败")
                    }
                } else {
                    LogUtil.d(TAG, "游戏正在运行，无需操作")
                }

                // 3. 等待下一次检测
                delay(CHECK_INTERVAL)
            }
        }
    }

    // 停止检测
    private fun stopMonitoring() {
        isMonitoring = false
        LogUtil.d(TAG, "已停止自动检测")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // 创建通知渠道
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "游戏自动检测服务",
                NotificationManager.IMPORTANCE_LOW // 低优先级，不打扰用户
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // 创建通知
    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("忍者自动脚本")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher) // 需在res/mipmap放置图标（可使用默认图标）
            .build()
    }

    // 更新通知内容
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring() // 服务销毁时停止循环
    }

    override fun onBind(intent: Intent?): IBinder? = null // 无需绑定，返回null
}
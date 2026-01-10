package com.example.ninjaau.core.screenshot

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.ninjaau.R
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.core.util.TemplateScreenshotTool

/**
 * 前台截图服务：在后台静默执行截图，不影响前台游戏
 */
class ForegroundScreenshotService : Service() {
    companion object {
        const val CHANNEL_ID = "ScreenshotServiceChannel"
        const val EXTRA_TEMPLATE_NAME = "extra_template_name"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val templateName = intent?.getStringExtra(EXTRA_TEMPLATE_NAME) ?: "template_${System.currentTimeMillis()}"

        val resultCode = PermissionManager.mResultCode
        val data = PermissionManager.mProjectionIntent

        if (resultCode == Activity.RESULT_OK && data != null) {
            startForegroundNotification()
            
            // 在子线程中执行截图，避免阻塞主线程
            Thread {
                executeScreenshot(resultCode, data, templateName)
            }.start()
        } else {
            Toast.makeText(this, "截图权限未就绪，请重新 Link Start 授权", Toast.LENGTH_SHORT).show()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "截图服务", 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在保存游戏模板")
            .setPriority(Notification.PRIORITY_MIN)
            .build()

        startForeground(101, notification)
    }

    private fun executeScreenshot(resultCode: Int, data: Intent, templateName: String) {
        try {
            // 1. 截图前：发送隐藏广播（修正引用为 BroadcastActions）
            sendBroadcast(Intent(Constant.HIDE_FLOATING_WINDOW))
            Thread.sleep(200)

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val screenshotTool = TemplateScreenshotTool(this, mediaProjection, windowManager)
            
            val path = screenshotTool.captureFullScreen(templateName)
            
            // 使用 ContextCompat 获取主线程执行器，确保 UI 操作安全
            ContextCompat.getMainExecutor(this).execute {
                Toast.makeText(this, "模板保存成功: $path", Toast.LENGTH_SHORT).show()
            }
            
            mediaProjection.stop()
        } catch (e: Exception) {
            ContextCompat.getMainExecutor(this).execute {
                Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            // 2. 截图后：发送显示广播（修正引用为 BroadcastActions）
            sendBroadcast(Intent(Constant.SHOW_FLOATING_WINDOW))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        }
    }
}
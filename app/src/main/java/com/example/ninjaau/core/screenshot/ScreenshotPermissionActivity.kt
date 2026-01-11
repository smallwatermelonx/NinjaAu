package com.example.ninjaau.core.screenshot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.ninjaau.core.floating.FloatingWindowService
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager

/**
 * 透明Activity：仅负责请求MediaProjection权限
 * 修复：授权成功后仅保存状态，不再自动触发截图
 */
class ScreenshotPermissionActivity : Activity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val REQUEST_CODE_SCREEN_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            // 1. 保存授权数据（确保非空）
            PermissionManager.mResultCode = resultCode
            PermissionManager.mProjectionIntent = data
            LogUtil.i("ScreenshotPermission", "授权成功 → 主动启动悬浮窗服务")

            // 2. 授权成功后，直接启动Service（不用等用户再点）
            val context = this
            val serviceIntent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Toast.makeText(this, "授权成功，服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            // 授权失败时，清空旧数据，避免脏数据导致后续判断错误
            PermissionManager.mResultCode = -1
            PermissionManager.mProjectionIntent = null
            Toast.makeText(this, "未授予截图权限", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

package com.example.ninjaau.core.capture

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

class CapturePermissionActivity : Activity() {
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
            PermissionManager.mResultCode = resultCode
            PermissionManager.mProjectionIntent = data
            PermissionManager.saveProjectionPermission(this) // 持久化授权数据
            LogUtil.i("CapturePermission", "授权成功，启动悬浮窗服务")

            val serviceIntent = Intent(this, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "授权成功，服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            PermissionManager.mResultCode = -1
            PermissionManager.mProjectionIntent = null
            Toast.makeText(this, "未授予截图权限", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

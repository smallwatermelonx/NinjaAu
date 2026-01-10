package com.example.ninjaau.core.screenshot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
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
            
            // 1. 核心修复：仅存入单例，实现“一次授权，全局复用”
            PermissionManager.mResultCode = resultCode
            PermissionManager.mProjectionIntent = data

            // 2. 移除之前自动启动 ForegroundScreenshotService 的逻辑
            // 这样点击 Link Start 授权后，就不会莫名其妙截一张图了。
            
            Toast.makeText(this, "截图权限已就绪", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "未授予截图权限", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

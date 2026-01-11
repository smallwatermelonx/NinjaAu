package com.example.ninjaau

import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.ui.NinjaScriptMainUI
import com.example.ninjaau.ui.theme.NinjaAuTheme

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val REQUEST_CODE_SCREEN_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            NinjaAuTheme {
                // 将权限检查与启动逻辑绑定到 UI 回调中
                NinjaScriptMainUI()
            }
        }
    }

    /**
     * 供 UI 调用：点击 Link Start 时的核心检查逻辑
     * 注意：由于 UI 在 NinjaScriptMainUI 中，我们通过一个全局或单例回调来触发，
     * 或者在这里通过 setContent 的组合逻辑来处理。
     * 为了简单且符合你当前的逻辑，我将复用 NinjaScriptMainUI 中的 onLinkStart。
     */
}

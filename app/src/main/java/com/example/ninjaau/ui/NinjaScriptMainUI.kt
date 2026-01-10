package com.example.ninjaau.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ninjaau.core.appcontrol.AdbController
import com.example.ninjaau.core.appcontrol.AppAutoRestartService
import com.example.ninjaau.core.floating.FloatingWindowService
import com.example.ninjaau.core.screenshot.ScreenshotPermissionActivity
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.ui.settings.RewardGrabSettingsUI

/**
 * 主界面容器
 * 核心修改：修复监控服务参数传递问题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NinjaScriptMainUI() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("启动", "功能设置")
    val context = LocalContext.current

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // 核心业务逻辑：点击 Link Start
    val onLinkStart: () -> Unit = {
        // 1. 检查无障碍服务
        if (!PermissionManager.isAccessibilityServiceEnabled(context)) {
            showToast("请先在设置中开启 [NinjaAu] 无障碍服务")
            context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            // 2. 检查截图权限
            if (!PermissionManager.hasProjectionPermission()) {
                showToast("请授予截图权限以复用")
                context.startActivity(Intent(context, ScreenshotPermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                // 3. 启动悬浮窗服务
                val floatingIntent = Intent(context, FloatingWindowService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(floatingIntent)
                } else {
                    context.startService(floatingIntent)
                }

                // 4. 核心修复：启动监控服务并传递包名参数
                val monitorIntent = Intent(context, AppAutoRestartService::class.java).apply {
                    putExtra("PACKAGE_NAME", Constant.NINJA_GAME_PACKAGE)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(monitorIntent)
                } else {
                    context.startService(monitorIntent)
                }

                // 5. 启动游戏
                val isLaunched = AdbController.launchApp(context, Constant.NINJA_GAME_PACKAGE)
                if (isLaunched) {
                    showToast("Link Start! 监控已开启")
                } else {
                    showToast("悬浮窗已就绪，请手动启动游戏")
                }
            }
        }
    }

    val onDisconnect: () -> Unit = {
        context.stopService(Intent(context, FloatingWindowService::class.java))
        context.stopService(Intent(context, AppAutoRestartService::class.java))
        showToast("已停止监控并断开链接")
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6C63FF),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE0E0E0)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("忍三自动化", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showToast("全局设置待开发") }) {
                            Icon(Icons.Default.Settings, "设置", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2C))
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF2C2C2C)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 16.sp) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> LaunchTab(onLinkStart = onLinkStart, onDisconnect = onDisconnect)
                    1 -> RewardGrabSettingsUI(onSaveSettings = { showToast("抢悬赏设置已保存") })
                }
            }
        }
    }
}

@Composable
private fun LaunchTab(
    onLinkStart: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onLinkStart,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Link Start!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFFF5252))
        ) {
            Text("断开链接", color = Color(0xFFFF5252), fontSize = 16.sp)
        }
    }
}

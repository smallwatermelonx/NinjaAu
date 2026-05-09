package com.example.ninjaau.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.floating.FloatingWindowService
import com.example.ninjaau.core.capture.CapturePermissionActivity
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NinjaScriptMainUI() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("启动", "关于")
    val context = LocalContext.current

    val showToast = remember { { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } }
    val checkOverlayPermission = remember { { Settings.canDrawOverlays(context) } }

    val onLinkStart = remember {
        {
            LogUtil.i("MainUI", "===== 点击Link Start =====")
            if (!PermissionManager.isAccessibilityServiceEnabled(context)) {
                showToast("请先开启无障碍服务")
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else if (!checkOverlayPermission()) {
                showToast("请开启悬浮窗权限")
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } else if (!PermissionManager.hasProjectionPermission()) {
                showToast("请授权截图权限")
                context.startActivity(Intent(context, CapturePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                val floatingIntent = Intent(context, FloatingWindowService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(floatingIntent)
                } else {
                    context.startService(floatingIntent)
                }
                GameManager.startScript(context)
                showToast("Link Start! 自动化服务启动")
            }
        }
    }

    val onDisconnect = remember {
        {
            LogUtil.i("MainUI", "===== 点击断开链接 =====")
            context.stopService(Intent(context, FloatingWindowService::class.java))
            showToast("已停止所有服务")
        }
    }

    LaunchedEffect(Unit) {
        LogUtil.i("MainUI", "===== APP启动，检查权限 =====")
        val tips = mutableListOf<String>()
        if (!PermissionManager.isAccessibilityServiceEnabled(context)) tips.add("无障碍服务")
        if (!checkOverlayPermission()) tips.add("悬浮窗权限")
        if (!PermissionManager.hasProjectionPermission()) tips.add("截图权限")
        if (tips.isNotEmpty()) {
            showToast("请先开启以下权限：${tips.joinToString("、")}")
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6C63FF),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("忍三自动化", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2C))
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(Color(0xFF121212))) {
                TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF2C2C2C)) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
                when (selectedTab) {
                    0 -> LaunchTab(onLinkStart = onLinkStart, onDisconnect = onDisconnect)
                    1 -> AboutTab()
                }
            }
        }
    }
}

@Composable
private fun LaunchTab(onLinkStart: () -> Unit, onDisconnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
            Text("Disconnect", color = Color(0xFFFF5252), fontSize = 16.sp)
        }
    }
}

@Composable
private fun AboutTab() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NinjaAu 忍三自动化", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("通过悬浮窗选择悬赏类型，脚本自动识别界面并执行", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("v2.0.0", color = Color.Gray, fontSize = 12.sp)
    }
}

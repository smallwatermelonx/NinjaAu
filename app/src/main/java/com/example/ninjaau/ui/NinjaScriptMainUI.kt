package com.example.ninjaau.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.ScriptState
import com.example.ninjaau.core.floating.FloatingWindowService
import com.example.ninjaau.core.capture.CapturePermissionActivity
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import kotlinx.coroutines.launch

private val Purple = Color(0xFF7C4DFF)
private val PurpleLight = Color(0xFFB388FF)
private val DarkBg = Color(0xFF1A1A2E)
private val DarkSurface = Color(0xFF16213E)
private val DarkCard = Color(0xFF0F3460)
private val GreenOn = Color(0xFF4CAF50)
private val RedOff = Color(0xFF616161)
private val Gold = Color(0xFFFFD700)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val LogBg = Color(0xFF0D0D1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NinjaScriptMainUI() {
    val context = LocalContext.current
    val scriptState by GameManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    // 用户勾选配置
    var bountyConfigs by remember { mutableStateOf(BountyConfig.defaultList()) }
    // 运行日志
    var logLines by remember { mutableStateOf(listOf("就绪，等待启动...")) }
    // 权限状态
    var permAccessibility by remember { mutableStateOf(false) }
    var permOverlay by remember { mutableStateOf(false) }
    var permProjection by remember { mutableStateOf(false) }

    // 检查权限
    fun refreshPerms() {
        permAccessibility = PermissionManager.isAccessibilityServiceEnabled(context)
        permOverlay = Settings.canDrawOverlays(context)
        permProjection = PermissionManager.hasProjectionPermission()
    }

    // 用 LifecycleObserver 在每次恢复时刷新权限状态（从系统设置返回后生效）
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPerms()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // 收集 GameManager 日志事件到运行日志面板
    LaunchedEffect(Unit) {
        GameManager.logEvents.collect { msg ->
            logLines = (logLines + msg).take(200)
        }
    }

    fun addLog(msg: String) {
        logLines = (logLines + msg).take(200)
    }

    val onStart = {
        LogUtil.i("MainUI", "===== 点击启动 =====")
        // 实时检查权限，不依赖可能过期的缓存状态
        val hasAcc = PermissionManager.isAccessibilityServiceEnabled(context)
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasProj = PermissionManager.hasProjectionPermission()

        if (!hasAcc) {
            addLog("❌ 需要开启无障碍服务")
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else if (!hasOverlay) {
            addLog("❌ 需要悬浮窗权限")
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else if (!hasProj) {
            addLog("📷 请求截图权限...")
            context.startActivity(Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else if (bountyConfigs.none { it.enabled }) {
            addLog("⚠️ 请先在下方勾选需要完成的悬赏等级")
        } else {
            // 同步 UI 勾选到 GameManager
            GameManager.updateBountyConfigs(bountyConfigs.filter { it.enabled })

            addLog("🚀 启动脚本...")
            // 用 Activity Context 启动前台服务（Android 12+ 要求）
            val intent = Intent(context, FloatingWindowService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            GameManager.startScript(context)
        }
    }

    val onPause = {
        addLog("⏸ 暂停脚本")
        GameManager.pauseScript()
    }

    val onResumeAction = {
        addLog("▶ 恢复脚本")
        GameManager.resumeScript(context)
    }

    val onStop = {
        addLog("⏹ 停止所有服务")
        GameManager.stopScript()
        context.stopService(Intent(context, FloatingWindowService::class.java))
        Unit
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = DarkBg,
            surface = DarkSurface,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NinjaAu", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("忍三自动化", fontSize = 14.sp, color = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            },
            containerColor = DarkBg
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ========== 权限状态行 ==========
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PermChip("无障碍", permAccessibility)
                    PermChip("悬浮窗", permOverlay)
                    PermChip("截图", permProjection)
                }

                // ========== 启动/停止按钮 ==========
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 脚本状态指示器
                        Text(
                            when (scriptState) {
                                ScriptState.IDLE -> "● 空闲"
                                ScriptState.RUNNING -> "● 运行中"
                                ScriptState.PAUSED -> "● 已暂停"
                            },
                            fontSize = 13.sp,
                            color = when (scriptState) {
                                ScriptState.RUNNING -> GreenOn
                                ScriptState.PAUSED -> Gold
                                ScriptState.IDLE -> TextSecondary
                            }
                        )
                        Spacer(Modifier.height(16.dp))

                        // 大启动/暂停/继续按钮
                        Button(
                            onClick = {
                                when (scriptState) {
                                    ScriptState.IDLE -> onStart()
                                    ScriptState.RUNNING -> onPause()
                                    ScriptState.PAUSED -> onResumeAction()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (scriptState) {
                                    ScriptState.IDLE -> Purple
                                    ScriptState.RUNNING -> Color(0xFFF5A623)
                                    ScriptState.PAUSED -> GreenOn
                                }
                            )
                        ) {
                            Text(
                                when (scriptState) {
                                    ScriptState.IDLE -> "▶  LINK START"
                                    ScriptState.RUNNING -> "⏸  暂停"
                                    ScriptState.PAUSED -> "▶  继续"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 运行中/暂停时显示停止按钮
                        if (scriptState != ScriptState.IDLE) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onStop) {
                                Text("停止脚本", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                // ========== 悬赏选择面板（日常 + 活动分栏） ==========
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("悬赏选择", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    bountyConfigs = bountyConfigs.map { it.copy(enabled = true) }
                                }) { Text("全选", fontSize = 13.sp, color = PurpleLight) }
                                TextButton(onClick = {
                                    bountyConfigs = bountyConfigs.map { it.copy(enabled = false) }
                                }) { Text("清空", fontSize = 13.sp, color = TextSecondary) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // 日常悬赏
                        Text("日常悬赏", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val dailyConfigs = bountyConfigs.filter { !it.grade.isEvent }
                        val dailyRows = dailyConfigs.chunked(4)
                        dailyRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { config ->
                                    BountyGradeCard(
                                        config = config,
                                        modifier = Modifier.weight(1f),
                                        onToggle = {
                                            bountyConfigs = bountyConfigs.map {
                                                if (it.grade == config.grade) it.copy(enabled = !it.enabled)
                                                else it
                                            }
                                            GameManager.updateBountyConfigs(bountyConfigs.filter { it.enabled })
                                        }
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        // 活动悬赏分隔
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.weight(1f).height(1.dp).background(DarkCard))
                            Spacer(Modifier.width(8.dp))
                            Text("活动悬赏 (N系列)", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f).height(1.dp).background(DarkCard))
                        }
                        Spacer(Modifier.height(8.dp))
                        val eventConfigs = bountyConfigs.filter { it.grade.isEvent }
                        val eventRows = eventConfigs.chunked(4)
                        eventRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { config ->
                                    BountyGradeCard(
                                        config = config,
                                        modifier = Modifier.weight(1f),
                                        onToggle = {
                                            bountyConfigs = bountyConfigs.map {
                                                if (it.grade == config.grade) it.copy(enabled = !it.enabled)
                                                else it
                                            }
                                            GameManager.updateBountyConfigs(bountyConfigs.filter { it.enabled })
                                        }
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                // ========== 运行日志 ==========
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LogBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("运行日志", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        logLines.takeLast(8).forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("v3.0", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

// ========== 子组件 ==========

@Composable
private fun PermChip(label: String, granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (granted) DarkCard else DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) GreenOn else RedOff)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = if (granted) TextPrimary else TextSecondary)
    }
}

@Composable
private fun BountyGradeCard(
    config: BountyConfig,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val bg = if (config.enabled) DarkCard else DarkSurface
    val border = if (config.enabled) Purple else Color.Transparent

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (config.enabled) 1.dp else 0.dp, border),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = config.grade.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (config.enabled) PurpleLight else TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${config.targetRuns}次",
                fontSize = 11.sp,
                color = if (config.enabled) TextSecondary else TextSecondary.copy(alpha = 0.5f)
            )
        }
    }
}

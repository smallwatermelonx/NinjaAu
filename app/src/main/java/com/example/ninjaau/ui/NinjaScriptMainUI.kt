package com.example.ninjaau.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ninjaau.core.config.ScriptConfigRepository
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.ScriptState
import com.example.ninjaau.core.floating.FloatingWindowService
import com.example.ninjaau.core.capture.CapturePermissionActivity
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══ Theme (VS Code Dark+ / IntelliJ Light) ═══
private object Theme {
    var isLight = mutableStateOf(false)

    private val D_Bg = Color(0xFF1E1E1E)
    private val D_Surface = Color(0xFF252526)
    private val D_Card = Color(0xFF2D2D30)
    private val D_Border = Color(0xFF3C3C3C)
    private val D_Text = Color(0xFFD4D4D4)
    private val D_TextMid = Color(0xFF969696)
    private val D_TextLow = Color(0xFF6A6A6A)
    private val D_Accent = Color(0xFF569CD6)
    private val D_AccentLight = Color(0xFF9CDCFE)
    private val D_Success = Color(0xFF4EC9B0)
    private val D_Warning = Color(0xFFDCDCAA)
    private val D_Danger = Color(0xFFF44747)
    private val D_LogBg = Color(0xFF1B1B1C)
    private val D_Gold = Color(0xFFCE9178)

    private val L_Bg = Color(0xFFF2F2F2)
    private val L_Surface = Color(0xFFFFFFFF)
    private val L_Card = Color(0xFFF7F8FA)
    private val L_Border = Color(0xFFD1D5DB)
    private val L_Text = Color(0xFF1E1E1E)
    private val L_TextMid = Color(0xFF6B7280)
    private val L_TextLow = Color(0xFF9CA3AF)
    private val L_Accent = Color(0xFF3B82F6)
    private val L_AccentLight = Color(0xFF2563EB)
    private val L_Success = Color(0xFF10B981)
    private val L_Warning = Color(0xFFF59E0B)
    private val L_Danger = Color(0xFFEF4444)
    private val L_LogBg = Color(0xFFE9ECEF)
    private val L_Gold = Color(0xFFD97706)

    val Accent get() = if (isLight.value) L_Accent else D_Accent
    val AccentLight get() = if (isLight.value) L_AccentLight else D_AccentLight
    val AccentGlow get() = Accent.copy(alpha = 0.10f)
    val Bg get() = if (isLight.value) L_Bg else D_Bg
    val Surface get() = if (isLight.value) L_Surface else D_Surface
    val Card get() = if (isLight.value) L_Card else D_Card
    val Border get() = if (isLight.value) L_Border else D_Border
    val Text get() = if (isLight.value) L_Text else D_Text
    val TextMid get() = if (isLight.value) L_TextMid else D_TextMid
    val TextLow get() = if (isLight.value) L_TextLow else D_TextLow
    val Warning get() = if (isLight.value) L_Warning else D_Warning
    val Danger get() = if (isLight.value) L_Danger else D_Danger
    val LogBg get() = if (isLight.value) L_LogBg else D_LogBg
    val Gold get() = if (isLight.value) L_Gold else D_Gold
    val DisabledBg get() = Border.copy(alpha = 0.5f)
    val DisabledText get() = TextLow
}

private enum class MainTab(val label: String) {
    HOME("首页"),
    SETTINGS("设置")
}

private enum class ConfigTarget(val label: String) {
    DAILY("日常悬赏"),
    PERSONAL("个人悬赏"),
    NS("逆袭悬赏"),
    TREASURE("藏宝图")
}

// ═══ 藏宝图 ═══
private enum class TreasureElement(val label: String, val icon: String) {
    WIND("风", "🌿"), FIRE("火", "🔥"), WATER("水", "💧"), THUNDER("雷", "⚡")
}

private enum class TreasureGrade(val label: String) {
    FAN("凡"), ZHEN("珍"), JUE("绝"), SHEN("神"), CHUANSONG("传颂")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NinjaScriptMainUI() {
    val context = LocalContext.current
    val scriptState by GameManager.state.collectAsState()
    val isLight by Theme.isLight

    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var configTarget by remember { mutableStateOf<ConfigTarget?>(null) }

    var bountyConfigs by remember { mutableStateOf(ScriptConfigRepository.bountyConfigs.value) }
    var personalConfigs by remember { mutableStateOf(ScriptConfigRepository.personalConfigs.value) }
    var nsConfigs by remember { mutableStateOf(ScriptConfigRepository.nsConfigs.value) }
    var enabledElements by remember { mutableStateOf(setOf<TreasureElement>()) }
    var enabledGrades by remember { mutableStateOf(setOf<TreasureGrade>()) }
    var chaseDreamGrades by remember { mutableStateOf(setOf<TreasureGrade>()) }

    var dailyEnabled by remember { mutableStateOf(ScriptConfigRepository.dailyEnabled.value) }
    var personalEnabled by remember { mutableStateOf(ScriptConfigRepository.personalEnabled.value) }
    var nsEnabled by remember { mutableStateOf(ScriptConfigRepository.nsEnabled.value) }
    var treasureEnabled by remember { mutableStateOf(ScriptConfigRepository.treasureEnabled.value) }
    var inviteCheckEnabled by remember { mutableStateOf(ScriptConfigRepository.inviteCheckEnabled.value) }
    var fastClickEnabled by remember { mutableStateOf(ScriptConfigRepository.fastClickEnabled.value) }
    var fastClickX by remember { mutableStateOf(ScriptConfigRepository.fastClickX.value.toString()) }
    var fastClickY by remember { mutableStateOf(ScriptConfigRepository.fastClickY.value.toString()) }

    fun saveAll() {
        ScriptConfigRepository.setDailyEnabled(dailyEnabled)
        ScriptConfigRepository.setPersonalEnabled(personalEnabled)
        ScriptConfigRepository.setNsEnabled(nsEnabled)
        ScriptConfigRepository.setTreasureEnabled(treasureEnabled)
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var logEntries by remember { mutableStateOf(listOf(System.currentTimeMillis() to "就绪，等待启动...")) }

    LaunchedEffect(Unit) {
        GameManager.logEvents.collect { msg ->
            logEntries = (logEntries + (System.currentTimeMillis() to msg)).take(200)
        }
    }

    fun addLog(msg: String) {
        logEntries = (logEntries + (System.currentTimeMillis() to msg)).take(200)
    }

    val onStart = {
        LogUtil.i("MainUI", "===== 点击启动 =====")
        when {
            !PermissionManager.isAccessibilityServiceEnabled(context) -> {
                // 尝试通过 root 自动启用
                if (PermissionManager.tryEnableAccessibilityViaRoot(context)) {
                    addLog("✅ 已通过 root 自动启用无障碍服务")
                } else {
                    addLog("❌ 需要开启无障碍服务")
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
            !Settings.canDrawOverlays(context) -> {
                addLog("❌ 需要悬浮窗权限")
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
            !PermissionManager.hasProjectionPermission() -> {
                addLog("📷 请求截图权限...")
                context.startActivity(Intent(context, CapturePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            !dailyEnabled && !personalEnabled && !nsEnabled -> {
                addLog("⚠️ 请至少勾选一条业务线")
            }
            else -> {
                ScriptConfigRepository.setBountyConfigs(bountyConfigs)
                ScriptConfigRepository.setDailyEnabled(dailyEnabled)
                ScriptConfigRepository.setPersonalEnabled(personalEnabled)
                ScriptConfigRepository.setNsEnabled(nsEnabled)
                ScriptConfigRepository.setInviteCheckEnabled(inviteCheckEnabled)
                ScriptConfigRepository.setFastClickEnabled(fastClickEnabled)
                ScriptConfigRepository.setFastClickX(fastClickX.toIntOrNull() ?: 0)
                ScriptConfigRepository.setFastClickY(fastClickY.toIntOrNull() ?: 0)
                if (personalEnabled) ScriptConfigRepository.setPersonalConfigs(personalConfigs)
                addLog("🚀 启动脚本...")
                val intent = Intent(context, FloatingWindowService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                GameManager.startScript(context)
            }
        }
    }

    MaterialTheme(
        colorScheme = if (isLight) lightColorScheme(
            primary = Theme.Accent, background = Theme.Bg, surface = Theme.Surface,
            onPrimary = Color.White, onBackground = Theme.Text, onSurface = Theme.Text
        ) else darkColorScheme(
            primary = Theme.Accent, background = Theme.Bg, surface = Theme.Surface,
            onPrimary = Color.White, onBackground = Theme.Text, onSurface = Theme.Text
        )
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("NinjaAu", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                        actions = {
                            TextButton(onClick = { Theme.isLight.value = !Theme.isLight.value }) {
                                Text(if (isLight) "☀" else "☾", fontSize = 18.sp, color = Theme.TextMid)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Theme.Surface)
                    )
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Theme.Surface,
                        indicator = {}, divider = {}
                    ) {
                        MainTab.entries.forEach { tab ->
                            val sel = selectedTab == tab
                            Tab(
                                selected = sel,
                                onClick = { selectedTab = tab; if (tab == MainTab.SETTINGS) configTarget = null },
                                text = {
                                    Text(tab.label, fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sel) Theme.AccentLight else Theme.TextLow)
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = Theme.Border, thickness = 1.dp)
                }
            },
            containerColor = Theme.Bg
        ) { padding ->
            when (selectedTab) {
                MainTab.HOME -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ═══ Left: task list ═══
                        Column(
                            modifier = Modifier.weight(0.2f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Theme.Surface)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)) {
                                    TaskRow("日常悬赏", dailyEnabled, onToggle = {
                                        dailyEnabled = !dailyEnabled
                                        saveAll()
                                        if (dailyEnabled && bountyConfigs.none { it.enabled }) {
                                            bountyConfigs = bountyConfigs.map { it.copy(enabled = true) }
                                        }
                                        ScriptConfigRepository.setBountyConfigs(bountyConfigs)
                                    }, onGearClick = { configTarget = ConfigTarget.DAILY })
                                    HorizontalDivider(color = Theme.Border, modifier = Modifier.padding(horizontal = 8.dp))
                                    TaskRow("个人悬赏", personalEnabled, onToggle = {
                                        personalEnabled = !personalEnabled
                                        if (personalEnabled && personalConfigs.none { it.enabled }) {
                                            personalConfigs = personalConfigs.map { it.copy(enabled = true) }
                                            ScriptConfigRepository.setPersonalConfigs(personalConfigs)
                                        }
                                        saveAll()
                                        ScriptConfigRepository.setPersonalEnabled(personalEnabled)
                                    }, onGearClick = { configTarget = ConfigTarget.PERSONAL })
                                    HorizontalDivider(color = Theme.Border, modifier = Modifier.padding(horizontal = 8.dp))
                                    TaskRow("逆袭悬赏", nsEnabled, onToggle = {
                                        nsEnabled = !nsEnabled
                                        saveAll()
                                    }, onGearClick = { configTarget = ConfigTarget.NS })
                                    HorizontalDivider(color = Theme.Border, modifier = Modifier.padding(horizontal = 8.dp))
                                    TaskRow("藏宝图", treasureEnabled, onToggle = {
                                        treasureEnabled = !treasureEnabled
                                        saveAll()
                                    }, onGearClick = { configTarget = ConfigTarget.TREASURE })
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Box(
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.horizontalGradient(
                                        colors = when (scriptState) {
                                            ScriptState.IDLE, ScriptState.PAUSED -> listOf(Theme.Accent, Theme.AccentLight)
                                            ScriptState.RUNNING -> listOf(Theme.Danger, Color(0xFFFF7675))
                                        }
                                    ))
                                    .clickable {
                                        when (scriptState) {
                                            ScriptState.IDLE -> onStart()
                                            ScriptState.RUNNING -> { GameManager.toggleScript(context); addLog("⏸ 暂停") }
                                            ScriptState.PAUSED -> { GameManager.toggleScript(context); addLog("▶ 恢复") }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when (scriptState) {
                                        ScriptState.IDLE -> "Link Start!"
                                        ScriptState.RUNNING -> "暂停"
                                        ScriptState.PAUSED -> "恢复"
                                    },
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp
                                )
                            }
                            if (scriptState != ScriptState.IDLE) {
                                TextButton(
                                    onClick = {
                                        GameManager.stopScript()
                                        context.stopService(Intent(context, FloatingWindowService::class.java))
                                        addLog("⏹ 停止")
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) { Text("停止", fontSize = 10.sp, color = Theme.TextLow) }
                            }
                        }

                        // ═══ Middle: config panel ═══
                        Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                            if (configTarget != null) {
                                ConfigPanel(
                                    target = configTarget!!,
                                    bountyConfigs = bountyConfigs,
                                    personalConfigs = personalConfigs,
                                    nsConfigs = nsConfigs,
                                    enabledElements = enabledElements,
                                    enabledGrades = enabledGrades,
                                    chaseDreamGrades = chaseDreamGrades,
                                    onBountyConfigsChanged = { configs ->
                                        bountyConfigs = configs
                                        val enabledList = configs.filter { it.enabled }
                                        dailyEnabled = enabledList.any { !it.grade.isEvent }
                                        saveAll()
                                        ScriptConfigRepository.setBountyConfigs(configs)
                                    },
                                    onPersonalConfigsChanged = { configs ->
                                        personalConfigs = configs
                                        val e = configs.any { it.enabled }; personalEnabled = e
                                        ScriptConfigRepository.setPersonalEnabled(e)
                                        ScriptConfigRepository.setPersonalConfigs(configs)
                                    },
                                    onNsConfigsChanged = { configs ->
                                        nsConfigs = configs
                                        nsEnabled = configs.any { it.enabled }
                                        // 只同步 NS 等级（NSS+, NS, NA）回 bountyConfigs，不动日常等级
                                        val nsGrades = configs.map { it.grade }.toSet()
                                        bountyConfigs = bountyConfigs.map { bc ->
                                            if (bc.grade in nsGrades) {
                                                val nsMatch = configs.find { it.grade == bc.grade }
                                                if (nsMatch != null) bc.copy(enabled = nsMatch.enabled, chaseDream = nsMatch.chaseDream) else bc
                                            } else bc
                                        }
                                        saveAll()
                                        ScriptConfigRepository.setNsConfigs(configs)
                                        ScriptConfigRepository.setBountyConfigs(bountyConfigs)
                                    },
                                    onEnabledElementsChanged = { enabledElements = it },
                                    onEnabledGradesChanged = { enabledGrades = it },
                                    onChaseDreamGradesChanged = { chaseDreamGrades = it }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("⚙", fontSize = 24.sp, color = Theme.DisabledText)
                                        Spacer(Modifier.height(4.dp))
                                        Text("点击齿轮配置业务", fontSize = 11.sp, color = Theme.TextLow)
                                    }
                                }
                            }
                        }

                        // ═══ Right: log ═══
                        Card(
                            modifier = Modifier.weight(0.2f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Theme.LogBg)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("日志", fontSize = 9.sp, color = Theme.TextLow, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                val logListState = rememberLazyListState()
                                val displayLogs = logEntries.takeLast(50)
                                LaunchedEffect(displayLogs.size) {
                                    if (displayLogs.isNotEmpty()) logListState.animateScrollToItem(displayLogs.size - 1)
                                }
                                LazyColumn(state = logListState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    items(displayLogs) { (ts, line) ->
                                        val time = timeFormatter.format(Date(ts))
                                        val color = when {
                                            line.contains("❌") -> Theme.Danger
                                            line.contains("⚠") -> Theme.Warning
                                            else -> Theme.TextLow
                                        }
                                        Text(text = "$time  $line", fontSize = 8.sp, color = color,
                                            lineHeight = 10.sp, modifier = Modifier.padding(vertical = 0.3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                MainTab.SETTINGS -> SettingsContent(
                    modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
                    inviteCheckEnabled = inviteCheckEnabled,
                    onInviteCheckChanged = { inviteCheckEnabled = it },
                    fastClickEnabled = fastClickEnabled,
                    onFastClickEnabledChanged = {
                        fastClickEnabled = it
                        ScriptConfigRepository.setFastClickEnabled(it)
                    },
                    fastClickX = fastClickX,
                    onFastClickXChanged = {
                        fastClickX = it
                        ScriptConfigRepository.setFastClickX(it.toIntOrNull() ?: 0)
                    },
                    fastClickY = fastClickY,
                    onFastClickYChanged = {
                        fastClickY = it
                        ScriptConfigRepository.setFastClickY(it.toIntOrNull() ?: 0)
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════
//  配置面板
// ════════════════════════════════════════
@Composable
private fun ConfigPanel(
    target: ConfigTarget,
    bountyConfigs: List<BountyConfig>,
    personalConfigs: List<BountyConfig>,
    nsConfigs: List<BountyConfig>,
    enabledElements: Set<TreasureElement>,
    enabledGrades: Set<TreasureGrade>,
    chaseDreamGrades: Set<TreasureGrade>,
    onBountyConfigsChanged: (List<BountyConfig>) -> Unit,
    onPersonalConfigsChanged: (List<BountyConfig>) -> Unit,
    onNsConfigsChanged: (List<BountyConfig>) -> Unit,
    onEnabledElementsChanged: (Set<TreasureElement>) -> Unit,
    onEnabledGradesChanged: (Set<TreasureGrade>) -> Unit,
    onChaseDreamGradesChanged: (Set<TreasureGrade>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.Surface)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(target.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Theme.Text)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Theme.Border)
        Spacer(Modifier.height(8.dp))

        when (target) {
            ConfigTarget.DAILY -> BountyGradePanel(
                configs = bountyConfigs.filter { !it.grade.isEvent },
                allConfigs = bountyConfigs,
                onConfigsChanged = onBountyConfigsChanged,
                showChaseDream = true
            )
            ConfigTarget.PERSONAL -> BountyGradePanel(
                configs = personalConfigs,
                allConfigs = personalConfigs,
                onConfigsChanged = onPersonalConfigsChanged,
                showChaseDream = false,
                lockedGrade = BountyGrade.SS_PLUS
            )
            ConfigTarget.NS -> BountyGradePanel(
                configs = nsConfigs,
                allConfigs = nsConfigs,
                onConfigsChanged = onNsConfigsChanged,
                showChaseDream = true
            )
            ConfigTarget.TREASURE -> TreasureMapPanel(
                enabledElements = enabledElements,
                enabledGrades = enabledGrades,
                chaseDreamGrades = chaseDreamGrades,
                onEnabledElementsChanged = onEnabledElementsChanged,
                onEnabledGradesChanged = onEnabledGradesChanged,
                onChaseDreamGradesChanged = onChaseDreamGradesChanged
            )
        }
    }
}

// ════════════════════════════════════════
//  悬赏等级面板（垂直行布局）
// ════════════════════════════════════════
@Composable
private fun BountyGradePanel(
    configs: List<BountyConfig>,
    allConfigs: List<BountyConfig>,
    onConfigsChanged: (List<BountyConfig>) -> Unit,
    showChaseDream: Boolean,
    lockedGrade: BountyGrade? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("全选", Theme.AccentLight) {
                onConfigsChanged(allConfigs.map { if (it.grade != lockedGrade) it.copy(enabled = true) else it })
            }
            PillButton("清空", Theme.TextLow) {
                onConfigsChanged(allConfigs.map { it.copy(enabled = false) })
            }
        }

        configs.forEach { config ->
            val locked = lockedGrade != null && config.grade == lockedGrade
            val enabled = config.enabled && !locked
            val dreaming = config.chaseDream

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Theme.Card)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = {
                            if (!locked) {
                                onConfigsChanged(allConfigs.map {
                                    if (it.grade == config.grade) it.copy(enabled = !enabled) else it
                                })
                            }
                        },
                        enabled = !locked,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Theme.Accent, uncheckedColor = Theme.TextLow,
                            disabledCheckedColor = Theme.Accent.copy(alpha = 0.4f),
                            disabledUncheckedColor = Theme.DisabledText
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        config.grade.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (locked) Theme.DisabledText else Theme.Text,
                        modifier = Modifier.weight(1f)
                    )

                    if (showChaseDream && config.grade.canChaseDream) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (dreaming && enabled) Theme.Gold.copy(alpha = 0.15f)
                                    else if (enabled) Theme.Surface else Color.Transparent
                                )
                                .clickable(enabled = enabled) {
                                    onConfigsChanged(allConfigs.map {
                                        if (it.grade == config.grade) it.copy(chaseDream = !dreaming) else it
                                    })
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "追梦", fontSize = 9.sp,
                                fontWeight = if (dreaming) FontWeight.Bold else FontWeight.Normal,
                                color = if (dreaming && enabled) Theme.Gold
                                else if (enabled) Theme.TextMid else Theme.DisabledText
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  藏宝图面板
// ════════════════════════════════════════
@Composable
private fun TreasureMapPanel(
    enabledElements: Set<TreasureElement>,
    enabledGrades: Set<TreasureGrade>,
    chaseDreamGrades: Set<TreasureGrade>,
    onEnabledElementsChanged: (Set<TreasureElement>) -> Unit,
    onEnabledGradesChanged: (Set<TreasureGrade>) -> Unit,
    onChaseDreamGradesChanged: (Set<TreasureGrade>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ═══ 元素筛选行 ═══
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Theme.Card)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("属性", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Theme.TextMid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TreasureElement.entries.forEach { element ->
                        val active = element in enabledElements
                        val bg = if (active) Theme.AccentGlow else Theme.Surface
                        val border = if (active) Theme.Accent else Theme.Border
                        val textColor = if (active) Theme.AccentLight else Theme.TextMid
                        Card(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onEnabledElementsChanged(
                                        if (active) enabledElements - element
                                        else enabledElements + element
                                    )
                                },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, border),
                            colors = CardDefaults.cardColors(containerColor = bg)
                        ) {
                            Text(
                                "${element.icon}${element.label}",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // ═══ 级别行（勾选 + 追梦） ═══
        TreasureGrade.entries.forEach { grade ->
            val active = grade in enabledGrades
            val dreaming = grade in chaseDreamGrades

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Theme.Card)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = active,
                        onCheckedChange = {
                            onEnabledGradesChanged(
                                if (active) enabledGrades - grade
                                else enabledGrades + grade
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Theme.Accent, uncheckedColor = Theme.TextLow
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        grade.label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Theme.Text, modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(
                                if (dreaming) Theme.Gold.copy(alpha = 0.15f)
                                else if (active) Theme.Surface else Color.Transparent
                            )
                            .clickable(enabled = active) {
                                onChaseDreamGradesChanged(
                                    if (dreaming) chaseDreamGrades - grade
                                    else chaseDreamGrades + grade
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "追梦", fontSize = 9.sp,
                            fontWeight = if (dreaming) FontWeight.Bold else FontWeight.Normal,
                            color = if (dreaming) Theme.Gold
                            else if (active) Theme.TextMid else Theme.DisabledText
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  设置页
// ════════════════════════════════════════
@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    inviteCheckEnabled: Boolean,
    onInviteCheckChanged: (Boolean) -> Unit,
    fastClickEnabled: Boolean,
    onFastClickEnabledChanged: (Boolean) -> Unit,
    fastClickX: String,
    onFastClickXChanged: (String) -> Unit,
    fastClickY: String,
    onFastClickYChanged: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Theme.Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("检测设置", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Theme.TextMid)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = inviteCheckEnabled, onCheckedChange = onInviteCheckChanged,
                        colors = CheckboxDefaults.colors(checkedColor = Theme.Accent, uncheckedColor = Theme.TextLow)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("检测组队邀请并自动拒绝", fontSize = 12.sp, color = Theme.TextMid)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Theme.Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("抢悬赏设置", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Theme.TextMid)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = fastClickEnabled, onCheckedChange = onFastClickEnabledChanged,
                        colors = CheckboxDefaults.colors(checkedColor = Theme.Accent, uncheckedColor = Theme.TextLow)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("启用抢悬赏（跳过等级匹配，直接点击加入队伍）", fontSize = 12.sp, color = Theme.TextMid)
                }
                if (fastClickEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("加入队伍按钮像素位置", fontSize = 11.sp, color = Theme.TextLow)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fastClickX,
                            onValueChange = { v -> if (v.all { it.isDigit() }) onFastClickXChanged(v) },
                            label = { Text("X", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Theme.Accent,
                                unfocusedBorderColor = Theme.TextLow,
                                focusedLabelColor = Theme.Accent,
                                unfocusedLabelColor = Theme.TextLow
                            )
                        )
                        OutlinedTextField(
                            value = fastClickY,
                            onValueChange = { v -> if (v.all { it.isDigit() }) onFastClickYChanged(v) },
                            label = { Text("Y", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Theme.Accent,
                                unfocusedBorderColor = Theme.TextLow,
                                focusedLabelColor = Theme.Accent,
                                unfocusedLabelColor = Theme.TextLow
                            )
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  通用子组件
// ════════════════════════════════════════

@Composable
private fun TaskRow(label: String, enabled: Boolean, locked: Boolean = false, onToggle: () -> Unit, onGearClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !locked) { onToggle() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = enabled, onCheckedChange = { if (!locked) onToggle() }, enabled = !locked,
            colors = CheckboxDefaults.colors(
                checkedColor = Theme.Accent, uncheckedColor = Theme.TextLow,
                disabledCheckedColor = Theme.Accent.copy(alpha = 0.4f), disabledUncheckedColor = Theme.DisabledText
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = if (locked) Theme.DisabledText else Theme.Text, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                .background(if (locked) Theme.DisabledBg else Theme.Card)
                .clickable(enabled = !locked) { onGearClick() },
            contentAlignment = Alignment.Center
        ) { Text("⚙", fontSize = 12.sp, color = if (locked) Theme.DisabledText else Theme.TextMid) }
    }
}

@Composable
private fun PillButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Theme.Card).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Text(text, fontSize = 11.sp, color = color) }
}

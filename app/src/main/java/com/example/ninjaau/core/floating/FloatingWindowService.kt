package com.example.ninjaau.core.floating

import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import com.example.ninjaau.R
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.ScriptState
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.core.util.ToastUtil
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

class FloatingWindowService : Service() {
    companion object {
        private const val CHANNEL_ID = "FloatingWindowServiceChannel"
        private const val NOTIFICATION_ID = 102
        private const val AUTO_HIDE_MS = 5000L
        private const val ANIM_DURATION = 300L
        private const val PANEL_WIDTH = 260
        private const val VISIBLE_TAB = 30
        private const val TOAST_DURATION_MS = 1500L
        private const val HUD_ALPHA = 0.85f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var flMainBall: View
    private lateinit var llMenu: LinearLayout
    private lateinit var ivControlIcon: ImageView

    // ── 旧布局面板引用（保留 inflate 兼容，已隐藏不用） ──
    private lateinit var llInfoPanel: LinearLayout
    private lateinit var llProgress: LinearLayout
    private lateinit var llLogs: LinearLayout
    private lateinit var svLogs: ScrollView

    // ── 独立信息面板（右侧悬浮层, NOT_TOUCHABLE） ──
    private var infoPanelRoot: LinearLayout? = null
    private var infoPanelParams: WindowManager.LayoutParams? = null
    private var progressGrid: LinearLayout? = null
    private var logContainer: LinearLayout? = null
    private var logScroll: ScrollView? = null
    private var isInfoPanelVisible = false

    // ── HUD 覆盖层（右上角进度矩形） ──
    private var hudRoot: LinearLayout? = null
    private var hudTvContent: TextView? = null
    private var hudLayoutParams: WindowManager.LayoutParams? = null

    // ── Toast 覆盖层（中上方页面跳转提示） ──
    private var toastTv: TextView? = null
    private var toastLayoutParams: WindowManager.LayoutParams? = null
    private val toastQueue = ConcurrentLinkedQueue<String>()
    private var isToastShowing = false

    // ── 悬浮球 Y 轴边界 ──
    private var minY = 0
    private var maxY = 0

    private var isExpanded = false
    private var isSideHidden = false
    private var isOnGameDataPage = false // 大厅或招募列表
    private var isReceiverRegistered = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var ballWidth = 0
    private val handler = Handler(Looper.getMainLooper())
    private val sideHideRunnable = Runnable { sideHide() }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var detector: SceneDetector

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constant.HIDE_FLOATING_WINDOW -> removeAllViews()
                Constant.SHOW_FLOATING_WINDOW -> addAllViews()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFloatingForeground()

        val initSuccess = PermissionManager.initMediaProjection(this)
        if (!initSuccess) {
            LogUtil.e("FloatingWindowService", "MediaProjection初始化失败")
            PermissionManager.clearProjectionPermission(this)
            Toast.makeText(this, "截图权限初始化失败，请重新点击Link Start授权", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        detector = SceneDetector(this)

        val filter = IntentFilter().apply {
            addAction(Constant.HIDE_FLOATING_WINDOW)
            addAction(Constant.SHOW_FLOATING_WINDOW)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isReceiverRegistered = true

        calcScreenBounds()
        initFloatingView()
        initInfoPanel()
        initHudOverlay()
        initToastOverlay()
        observeScriptState()
        observeDataFlows()
    }

    private fun calcScreenBounds() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        val statusBarRes = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (statusBarRes > 0) resources.getDimensionPixelSize(statusBarRes) else 60

        val navRes = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navHeight = if (navRes > 0) resources.getDimensionPixelSize(navRes) else 0

        minY = statusBarHeight
        maxY = screenHeight - navHeight - 200
    }

    private fun clampY(y: Int): Int {
        return when {
            y < minY -> minY
            y > maxY -> maxY
            else -> y
        }
    }

    private fun startFloatingForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NinjaAu 悬浮窗活跃中")
            .setPriority(Notification.PRIORITY_MIN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun observeScriptState() {
        serviceScope.launch {
            GameManager.state.collectLatest { state ->
                when (state) {
                    ScriptState.RUNNING -> {
                        ivControlIcon.setImageResource(android.R.drawable.ic_media_pause)
                        showHud()
                        updatePanelVisibility()
                    }
                    ScriptState.IDLE -> {
                        ivControlIcon.setImageResource(android.R.drawable.ic_media_play)
                        hideHud()
                        hideInfoPanel()
                    }
                }
            }
        }
    }

    private fun observeDataFlows() {
        // 悬赏完成进度 → HUD + 信息面板
        serviceScope.launch {
            GameManager.bountyProgress.collectLatest { progress ->
                updateHudContent(progress)
                updateProgressGrid(progress)
            }
        }
        // 运行日志 → 信息面板
        serviceScope.launch {
            GameManager.logEvents.collectLatest { msg ->
                addLogEntry(msg)
            }
        }
        // 页面跳转事件 → Toast + 面板可见性
        serviceScope.launch {
            GameManager.pageEvents.collectLatest { event ->
                showPageToast(event)
                isOnGameDataPage = event == "进入大厅" || event == "进入招募列表"
                updatePanelVisibility()
            }
        }
    }

    // ══════════════════════════════════════════
    //  独立信息面板（纯日志面板）
    // ══════════════════════════════════════════

    private fun initInfoPanel() {
        val density = resources.displayMetrics.density
        val panelWidth = (screenWidth * 0.38).toInt()
        val panelHeight = (screenHeight * 0.22).toInt()
        // HUD 位于 y=minY+50，高度约 60-80dp，infoPanel 放在 HUD 下方留 12dp 间距
        val panelY = minY + (130 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(0x991A1A2E.toInt())
                cornerRadius = 10f * density
            }
            background = bg
        }

        // ── 标题行 ──
        root.addView(TextView(this).apply {
            text = "运行日志"
            setTextColor(0xFFB388FF.toInt())
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setPadding((10 * density).toInt(), (6 * density).toInt(),
                (10 * density).toInt(), (2 * density).toInt())
        })

        // ── 日志区域（全宽） ──
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f }
        }
        logScroll = scroll
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logContainer = inner
        scroll.addView(inner)
        root.addView(scroll)

        infoPanelRoot = root

        infoPanelParams = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = panelY
        }
    }

    private fun showInfoPanel() {
        if (isInfoPanelVisible) return
        val root = infoPanelRoot ?: return
        if (root.parent == null) {
            try {
                windowManager.addView(root, infoPanelParams)
            } catch (e: Exception) {
                LogUtil.e("FloatingWindowService", "添加信息面板失败: ${e.message}")
                return
            }
        }
        isInfoPanelVisible = true
    }

    private fun hideInfoPanel() {
        if (!isInfoPanelVisible) return
        infoPanelRoot?.let {
            if (it.parent != null) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        isInfoPanelVisible = false
    }

    private fun updatePanelVisibility() {
        if (GameManager.state.value == ScriptState.RUNNING && isOnGameDataPage) {
            showInfoPanel()
        } else {
            hideInfoPanel()
        }
    }

    private fun updateProgressGrid(progress: Map<BountyGrade, Pair<Int, Int>>) {
        val grid = progressGrid ?: return
        grid.removeAllViews()

        if (progress.isEmpty()) {
            grid.addView(infoText("暂无数据", "#FF9E9E9E"))
            return
        }

        val entries = mutableListOf<Pair<String, String>>()
        for (grade in BountyGrade.sorted()) {
            val counts = progress[grade] ?: continue
            val (completed, target) = counts
            if (target <= 0) continue
            val done = completed >= target
            val label = "${grade.displayName} ${completed}/${target}${if (done) " ✓" else ""}"
            val color = if (done) "#FF4CAF50" else "#FFE0E0E0"
            entries.add(label to color)
        }

        for (chunk in entries.chunked(2)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for ((text, color) in chunk) {
                row.addView(infoText(text, color).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            }
            if (chunk.size < 2) {
                row.addView(Space(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            grid.addView(row)
        }
    }

    private fun infoText(text: String, colorHex: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor(colorHex))
            setPadding(0, 3, 0, 3)
        }
    }

    private fun addLogEntry(msg: String) {
        val container = logContainer ?: return
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val tv = TextView(this).apply {
            text = "$time - $msg"
            textSize = 12f
            setTextColor(0xFFE0E0E0.toInt())
            setPadding(0, 2, 0, 2)
        }
        container.addView(tv)
        while (container.childCount > 30) {
            container.removeViewAt(0)
        }
        logScroll?.post { logScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    // ══════════════════════════════════════════
    //  HUD 覆盖层（右上角进度矩形）
    // ══════════════════════════════════════════

    private fun initHudOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 8)
            val bg = GradientDrawable().apply {
                setColor(0xAA1A1A2E.toInt())
                cornerRadius = 8f
            }
            background = bg
            alpha = HUD_ALPHA
        }
        val title = TextView(this).apply {
            text = "◆ 进度"
            setTextColor(0xFFB388FF.toInt())
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title)
        val content = TextView(this).apply {
            textSize = 14f
            setLineSpacing(2f, 1f)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 4, 0, 2)
        }
        root.addView(content)
        hudTvContent = content
        hudRoot = root

        val maxW = (150 * resources.displayMetrics.density).toInt()
        hudLayoutParams = WindowManager.LayoutParams(
            maxW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = minY + 50
        }
    }

    private fun showHud() {
        hudRoot?.let { v ->
            if (v.parent == null && hudLayoutParams != null) {
                try {
                    windowManager.addView(v, hudLayoutParams)
                } catch (e: Exception) {
                    LogUtil.e("FloatingWindowService", "添加HUD失败: ${e.message}")
                }
            }
            v.animate().alpha(HUD_ALPHA).setDuration(300).start()
            v.visibility = View.VISIBLE
        }
    }

    private fun hideHud() {
        hudRoot?.let { v ->
            v.animate().alpha(0f).setDuration(200).withEndAction {
                if (v.parent != null) {
                    try { windowManager.removeView(v) } catch (_: Exception) {}
                }
                v.visibility = View.GONE
            }.start()
        }
    }

    private fun updateHudContent(progress: Map<BountyGrade, Pair<Int, Int>>) {
        if (progress.isEmpty()) {
            hudTvContent?.text = "暂无"
            return
        }
        val sb = StringBuilder()
        var count = 0
        for (grade in BountyGrade.sorted()) {
            val counts = progress[grade] ?: continue
            val (completed, target) = counts
            if (target <= 0) continue
            val done = completed >= target
            val label = "${grade.displayName} ${completed}/${target}${if (done) "✓" else ""}"
            if (count > 0) sb.append(' ')
            sb.append(label)
            count++
        }
        hudTvContent?.text = sb.toString()
    }

    // ══════════════════════════════════════════
    //  Toast 覆盖层（中上方页面跳转提示）
    // ══════════════════════════════════════════

    private fun initToastOverlay() {
        val tv = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(28, 12, 28, 12)
            setTextColor(android.graphics.Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(0xCC1A1A2E.toInt())
                cornerRadius = 22f
            }
            background = bg
            visibility = View.GONE
            includeFontPadding = false
        }
        toastTv = tv

        toastLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = minY + 140
        }
    }

    private fun showPageToast(message: String) {
        toastQueue.offer(message)
        processToastQueue()
    }

    private fun processToastQueue() {
        if (isToastShowing || toastQueue.isEmpty()) return
        isToastShowing = true
        val msg = toastQueue.poll() ?: run { isToastShowing = false; return }

        val tv = toastTv ?: run { isToastShowing = false; return }
        if (tv.parent == null && toastLayoutParams != null) {
            try {
                windowManager.addView(tv, toastLayoutParams)
            } catch (e: Exception) {
                LogUtil.e("FloatingWindowService", "添加Toast失败: ${e.message}")
                isToastShowing = false
                return
            }
        }

        tv.apply {
            text = msg
            alpha = 0f
            visibility = View.VISIBLE
            animate().cancel()
            animate().alpha(1f).setDuration(200).withEndAction {
                postDelayed({
                    animate().alpha(0f).setDuration(300).withEndAction {
                        visibility = View.GONE
                        isToastShowing = false
                        processToastQueue()
                    }
                }, TOAST_DURATION_MS)
            }.start()
        }
    }

    // ══════════════════════════════════════════
    //  浮动球初始化
    // ══════════════════════════════════════════

    private fun initFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null)
        flMainBall = floatingView.findViewById(R.id.fl_main_ball)
        llMenu = floatingView.findViewById(R.id.ll_menu)
        ivControlIcon = floatingView.findViewById(R.id.iv_control_icon)
        // 旧布局面板引用保留用于 inflate 兼容，强制隐藏
        llInfoPanel = floatingView.findViewById(R.id.ll_info_panel)
        llProgress = floatingView.findViewById(R.id.ll_progress)
        llLogs = floatingView.findViewById(R.id.ll_logs)
        svLogs = floatingView.findViewById(R.id.sv_logs)
        llInfoPanel.visibility = View.GONE

        layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 0
            y = minY + 50
        }

        floatingView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                ballWidth = flMainBall.width
                if (ballWidth > 0) {
                    floatingView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })

        addFloatingView()
        setupTouchInteraction()
        setupMenuClicks()
        resetHideTimer()
    }

    // ══════════════════════════════════════════
    //  触摸交互 + Y 轴钳制
    // ══════════════════════════════════════════

    private fun setupTouchInteraction() {
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        var isMoving = false

        floatingView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(sideHideRunnable)
                    if (isSideHidden) restoreFromSide()
                    startX = event.rawX
                    startY = event.rawY
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isMoving = true
                        layoutParams.x = (initialX + dx).toInt()
                        layoutParams.y = clampY((initialY + dy).toInt())
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        v.performClick()
                        toggleMenu()
                    } else {
                        snapToEdge()
                    }
                    resetHideTimer()
                    true
                }
                else -> false
            }
        }
    }

    // ── 菜单点击 ──

    private fun setupMenuClicks() {
        floatingView.findViewById<View>(R.id.btn_start).setOnClickListener {
            resetHideTimer()
            GameManager.toggleScript(this)
        }
        floatingView.findViewById<View>(R.id.btn_bounty).setOnClickListener {
            resetHideTimer()
            showBountySelectionDialog()
        }
        floatingView.findViewById<View>(R.id.iv_close).setOnClickListener {
            GameManager.stopScript()
            stopSelf()
        }
        floatingView.findViewById<View>(R.id.btn_test).setOnClickListener {
            resetHideTimer()
            showNodeTestDialog()
        }
    }

    private fun showBountySelectionDialog() {
        val grades = BountyGrade.sorted()
        val items = grades.map { "${it.displayName}  (${it.defaultRuns}次)" }.toTypedArray()
        val checked = BooleanArray(grades.size) { i ->
            GameManager.getSelectedGrades().contains(grades[i])
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("选择悬赏等级")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                val configs = grades.map { grade ->
                    BountyConfig(grade = grade, enabled = checked[grades.indexOf(grade)])
                }
                GameManager.updateBountyConfigs(configs)
                val enabledNames = grades.filterIndexed { i, _ -> checked[i] }
                    .joinToString(", ") { it.displayName }
                ToastUtil.show(this, "已选择: $enabledNames")
            }
            .setNegativeButton("取消", null)
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    // ── 模板测试 ──

    private fun showNodeTestDialog() {
        val groups = SceneDetector.NodeTemplateGroup.entries
        val names = groups.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("选择当前页面节点")
            .setSingleChoiceItems(names, -1) { dialog, which ->
                dialog.dismiss()
                runNodeTest(groups[which])
            }
            .setNegativeButton("取消", null)
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun runNodeTest(group: SceneDetector.NodeTemplateGroup) {
        val screen = com.example.ninjaau.core.capture.ScreenCapture.getInstance(this).capture()
        if (screen == null) {
            ToastUtil.show(this, "截图失败，请确认截图权限已开启")
            return
        }
        try {
            val results = detector.testNodeTemplates(screen, group)
            showTestResultDialog(group.displayName, results)
        } finally {
            screen.recycle()
        }
    }

    private fun showTestResultDialog(nodeName: String, results: List<SceneDetector.TemplateTestResult>) {
        val density = resources.displayMetrics.density
        val scrollView = ScrollView(this).apply {
            setPadding((16 * density).toInt(), (12 * density).toInt(),
                (16 * density).toInt(), (8 * density).toInt())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 汇总行
        val passed = results.count { it.passed }
        val total = results.size
        container.addView(TextView(this).apply {
            text = "通过 $passed / $total"
            setTextColor(if (passed == total) 0xFF4CAF50.toInt() else 0xFFFFB74D.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        // 分隔线
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()
            ).apply { bottomMargin = (8 * density).toInt() }
            setBackgroundColor(0x33FFFFFF)
        })

        // 每条结果
        for (r in results) {
            val icon = if (r.passed) "✅" else "❌"
            val color = when {
                r.passed -> 0xFF4CAF50.toInt()
                r.threshold - r.similarity < 0.05f -> 0xFFFFB74D.toInt()
                else -> 0xFF888888.toInt()
            }
            val coordStr = if (r.centerX != null && r.centerY != null)
                "  (${String.format("%.0f", r.centerX)}, ${String.format("%.0f", r.centerY)})" else ""

            container.addView(TextView(this).apply {
                text = "$icon ${r.name}  ${String.format("%.3f", r.similarity)} / ${r.threshold}$coordStr"
                setTextColor(color)
                textSize = 12f
                setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
                typeface = Typeface.MONOSPACE
            })
        }

        scrollView.addView(container)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("模板测试 — $nodeName")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    // ── 展开/收起 ──

    private fun toggleMenu() {
        isExpanded = !isExpanded
        if (isExpanded) {
            snapToEdgeForPanel()
            showMenu()
            updatePanelVisibility()
        } else {
            hideMenu()
            hideInfoPanel()
        }
        resetHideTimer()
    }

    /**
     * 展开前将球吸附到最近边缘，确保菜单可见。
     * 球在左半 → 吸左边缘；右半 → 吸右边缘。
     */
    private fun snapToEdgeForPanel() {
        if (ballWidth == 0) ballWidth = flMainBall.width
        if (ballWidth == 0) return
        val leftEdge = 0
        val rightEdge = screenWidth - ballWidth
        layoutParams.x = if (layoutParams.x + ballWidth / 2 < screenWidth / 2) leftEdge else rightEdge
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    // ── 菜单动画（滚动式逐个出现） ──

    private fun showMenu() {
        llMenu.visibility = View.VISIBLE
        llMenu.alpha = 1f
        val density = resources.displayMetrics.density
        val slidePx = 180f * density
        val isOnRight = layoutParams.x + ballWidth / 2 > screenWidth / 2

        // 动态测量实际菜单宽度
        llMenu.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measuredWidth = llMenu.measuredWidth

        // 逐个按钮滚动出现
        val childCount = llMenu.childCount
        for (i in 0 until childCount) {
            val child = llMenu.getChildAt(i)
            child.translationX = if (isOnRight) slidePx else -slidePx
            child.alpha = 0f
            child.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(ANIM_DURATION)
                .setStartDelay(i * 50L)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }

        // 整体菜单位移（右侧时移到球左侧）
        if (isOnRight) {
            llMenu.translationX = 0f
            llMenu.animate()
                .translationX(-(measuredWidth - ballWidth).toFloat())
                .setDuration(ANIM_DURATION)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        } else {
            llMenu.translationX = 0f
        }
    }

    private fun hideMenu() {
        val density = resources.displayMetrics.density
        val slidePx = 180f * density
        val isOnRight = layoutParams.x + ballWidth / 2 > screenWidth / 2

        // 所有按钮同时收起
        for (i in 0 until llMenu.childCount) {
            val child = llMenu.getChildAt(i)
            child.animate()
                .translationX(if (isOnRight) slidePx else -slidePx)
                .alpha(0f)
                .setDuration(ANIM_DURATION / 2)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }

        // 菜单整体归位
        llMenu.animate()
            .translationX(0f)
            .alpha(0f)
            .setDuration(ANIM_DURATION / 2)
            .withEndAction {
                llMenu.visibility = View.GONE
                llMenu.translationX = 0f
                // 重置子 view 状态
                for (i in 0 until llMenu.childCount) {
                    llMenu.getChildAt(i).translationX = 0f
                    llMenu.getChildAt(i).alpha = 1f
                }
            }
            .start()
    }

    // ── 吸附与隐藏 ──

    /**
     * 拖动结束后吸附到最近的屏幕边缘。
     * 使用 ballWidth（而非 floatingView.width）判断中心位置，
     * 防止菜单展开时误判方向。
     */
    private fun snapToEdge() {
        if (ballWidth == 0) ballWidth = flMainBall.width
        val leftEdge = 0
        val rightEdge = screenWidth - ballWidth
        val targetX = if (layoutParams.x + ballWidth / 2 < screenWidth / 2) leftEdge else rightEdge
        val targetY = clampY(layoutParams.y)

        val startX = layoutParams.x
        val startY = layoutParams.y

        ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(250)
            interpolator = OvershootInterpolator(1.0f)
            addUpdateListener { a ->
                val fraction = (a as ValueAnimator).animatedFraction
                layoutParams.x = (startX + (targetX - startX) * fraction).toInt()
                layoutParams.y = (startY + (targetY - startY) * fraction).toInt()
                windowManager.updateViewLayout(floatingView, layoutParams)
            }
            start()
        }
    }

    private fun sideHide() {
        if (isExpanded || isSideHidden) return
        if (ballWidth == 0) ballWidth = flMainBall.width
        if (ballWidth == 0) return
        val targetX = if (layoutParams.x <= 0) -(ballWidth - VISIBLE_TAB) else screenWidth - VISIBLE_TAB
        val startX = layoutParams.x

        ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(300)
            addUpdateListener { a ->
                val fraction = (a as ValueAnimator).animatedFraction
                layoutParams.x = (startX + (targetX - startX) * fraction).toInt()
                windowManager.updateViewLayout(floatingView, layoutParams)
            }
            start()
        }
        isSideHidden = true
        flMainBall.animate().alpha(0.5f).setDuration(200).start()
    }

    private fun restoreFromSide() {
        if (!isSideHidden) return
        if (ballWidth == 0) ballWidth = flMainBall.width
        val targetX = if (layoutParams.x < 0) 0 else screenWidth - ballWidth
        val startX = layoutParams.x

        ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(200)
            addUpdateListener { a ->
                val fraction = (a as ValueAnimator).animatedFraction
                layoutParams.x = (startX + (targetX - startX) * fraction).toInt()
                windowManager.updateViewLayout(floatingView, layoutParams)
            }
            start()
        }
        isSideHidden = false
        flMainBall.animate().alpha(1.0f).setDuration(200).start()
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(sideHideRunnable)
        if (!isExpanded) handler.postDelayed(sideHideRunnable, AUTO_HIDE_MS)
    }

    // ── 视图生命周期 ──

    private fun addFloatingView() {
        if (::floatingView.isInitialized && floatingView.parent == null) {
            try {
                windowManager.addView(floatingView, layoutParams)
            } catch (e: Exception) {
                LogUtil.e("FloatingWindowService", "添加悬浮窗失败: ${e.message}")
            }
        }
    }

    private fun removeFloatingView() {
        if (::floatingView.isInitialized && floatingView.parent != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                LogUtil.e("FloatingWindowService", "移除悬浮窗失败: ${e.message}")
            }
        }
    }

    private fun addAllViews() {
        addFloatingView()
        if (GameManager.state.value != ScriptState.IDLE) {
            showHud()
            updatePanelVisibility()
        }
    }

    private fun removeAllViews() {
        removeFloatingView()
        hideInfoPanel()
        hudRoot?.let {
            if (it.parent != null) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        toastTv?.let {
            if (it.parent != null) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        handler.removeCallbacks(sideHideRunnable)
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        removeAllViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        PermissionManager.releaseMediaProjection()
    }
}

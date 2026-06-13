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
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

class FloatingWindowService : Service() {
    companion object {
        private const val CHANNEL_ID = "FloatingWindowServiceChannel"
        private const val NOTIFICATION_ID = 102
        private const val AUTO_HIDE_MS = 5000L
        private const val ANIM_DURATION = 300L
        private const val VISIBLE_TAB = 30
        private const val TOAST_DURATION_MS = 1500L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var flMainBall: View

    private lateinit var llMenuLeft: LinearLayout
    private lateinit var llMenuRight: LinearLayout
    private lateinit var ivControlIconLeft: ImageView
    private lateinit var ivControlIconRight: ImageView

    private val llMenu: LinearLayout get() = if (isBallOnRight()) llMenuLeft else llMenuRight
    private val ivControlIcon: ImageView get() = if (isBallOnRight()) ivControlIconLeft else ivControlIconRight

    private lateinit var hudManager: HudManager

    private var toastTv: TextView? = null
    private var toastLayoutParams: WindowManager.LayoutParams? = null
    private val toastQueue = ConcurrentLinkedQueue<String>()
    private var isToastShowing = false

    private var minY = 0
    private var maxY = 0

    private var isExpanded = false
    private var isSideHidden = false
    private var isOnGameDataPage = false
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
        hudManager = HudManager(this, windowManager)
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
                        ivControlIconLeft.setImageResource(android.R.drawable.ic_media_pause)
                        ivControlIconRight.setImageResource(android.R.drawable.ic_media_pause)
                        if (isOnGameDataPage) hudManager.show()
                    }
                    ScriptState.IDLE -> {
                        ivControlIconLeft.setImageResource(android.R.drawable.ic_media_play)
                        ivControlIconRight.setImageResource(android.R.drawable.ic_media_play)
                        hudManager.hide()
                    }
                }
            }
        }
    }

    private fun observeDataFlows() {
        serviceScope.launch {
            GameManager.bountyProgress.collectLatest { progress ->
                hudManager.updateProgress(progress)
            }
        }
        serviceScope.launch {
            GameManager.pageEvents.collectLatest { event ->
                showPageToast(event)
                val wasOnPage = isOnGameDataPage
                isOnGameDataPage = event == "进入大厅" || event == "进入招募列表"
                if (isOnGameDataPage && !wasOnPage && GameManager.state.value == ScriptState.RUNNING) {
                    hudManager.show()
                } else if (!isOnGameDataPage && wasOnPage) {
                    hudManager.hide()
                }
            }
        }
    }

    // ══════════════════════════════════════════
    //  Toast 覆盖层
    // ══════════════════════════════════════════

    private fun initToastOverlay() {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((32 * density).toInt(), (14 * density).toInt(),
                (32 * density).toInt(), (14 * density).toInt())
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(0xBB222222.toInt())
                cornerRadius = 24f * density
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
        llMenuLeft = floatingView.findViewById(R.id.ll_menu_left)
        llMenuRight = floatingView.findViewById(R.id.ll_menu_right)
        ivControlIconLeft = floatingView.findViewById(R.id.iv_control_icon_left)
        ivControlIconRight = floatingView.findViewById(R.id.iv_control_icon_right)

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
    //  触摸交互
    // ══════════════════════════════════════════

    private fun setupTouchInteraction() {
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        var isMoving = false

        flMainBall.setOnTouchListener { _, event ->
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

    // ── 菜单点击（左右各一套，统一行为） ──

    private fun setupMenuClicks() {
        val startAction = { resetHideTimer(); GameManager.toggleScript(this) }
        val bountyAction = { resetHideTimer(); showBountySelectionDialog() }
        val closeAction = { GameManager.stopScript(); stopSelf() }
        val testAction = { resetHideTimer(); showNodeTestDialog() }

        floatingView.findViewById<View>(R.id.btn_start_left).setOnClickListener { startAction() }
        floatingView.findViewById<View>(R.id.btn_start_right).setOnClickListener { startAction() }
        floatingView.findViewById<View>(R.id.btn_bounty_left).setOnClickListener { bountyAction() }
        floatingView.findViewById<View>(R.id.btn_bounty_right).setOnClickListener { bountyAction() }
        floatingView.findViewById<View>(R.id.iv_close_left).setOnClickListener { closeAction() }
        floatingView.findViewById<View>(R.id.iv_close_right).setOnClickListener { closeAction() }
        floatingView.findViewById<View>(R.id.btn_test_left).setOnClickListener { testAction() }
        floatingView.findViewById<View>(R.id.btn_test_right).setOnClickListener { testAction() }
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

        val passed = results.count { it.passed }
        val total = results.size
        container.addView(TextView(this).apply {
            text = "通过 $passed / $total"
            setTextColor(if (passed == total) 0xFF4CAF50.toInt() else 0xFFFFB74D.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()
            ).apply { bottomMargin = (8 * density).toInt() }
            setBackgroundColor(0x33FFFFFF)
        })

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

    // ══════════════════════════════════════════
    //  菜单展开/收起
    // ══════════════════════════════════════════

    private fun isBallOnRight(): Boolean {
        return layoutParams.x + ballWidth / 2 > screenWidth / 2
    }

    private fun toggleMenu() {
        isExpanded = !isExpanded
        if (isExpanded) showMenu() else hideMenu()
        resetHideTimer()
    }

    private fun showMenu() {
        val onRight = isBallOnRight()
        val activeMenu = llMenu
        val density = resources.displayMetrics.density
        val slidePx = 180f * density

        activeMenu.visibility = View.VISIBLE
        activeMenu.alpha = 1f
        animateMenuIn(slidePx, onRight)
    }

    private fun animateMenuIn(slidePx: Float, onRight: Boolean) {
        val activeMenu = llMenu
        for (i in 0 until activeMenu.childCount) {
            val child = activeMenu.getChildAt(i)
            child.translationX = if (onRight) -slidePx else slidePx
            child.alpha = 0f
            child.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(ANIM_DURATION)
                .setStartDelay(i * 60L)
                .setInterpolator(DecelerateInterpolator(2.5f))
                .start()
        }
    }

    private fun hideMenu() {
        val onRight = isBallOnRight()
        val activeMenu = llMenu
        val density = resources.displayMetrics.density
        val slidePx = 180f * density

        for (i in 0 until activeMenu.childCount) {
            val child = activeMenu.getChildAt(i)
            child.animate()
                .translationX(if (onRight) -slidePx else slidePx)
                .alpha(0f)
                .setDuration(ANIM_DURATION / 2)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        activeMenu.animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION / 2)
            .withEndAction {
                activeMenu.visibility = View.GONE
                for (i in 0 until activeMenu.childCount) {
                    activeMenu.getChildAt(i).translationX = 0f
                    activeMenu.getChildAt(i).alpha = 1f
                }
            }
            .start()
    }

    // ══════════════════════════════════════════
    //  吸附边缘 + 侧边隐藏
    // ══════════════════════════════════════════

    private fun snapToEdge() {
        if (ballWidth == 0) ballWidth = flMainBall.width
        if (ballWidth == 0) return

        val goRight = layoutParams.x + ballWidth / 2 >= screenWidth / 2
        val startX = layoutParams.x
        val startY = layoutParams.y
        val targetY = clampY(layoutParams.y)
        val targetX = if (goRight) screenWidth - ballWidth else 0

        ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(250)
            interpolator = OvershootInterpolator(1.0f)
            addUpdateListener { a ->
                val f = (a as ValueAnimator).animatedFraction
                layoutParams.x = (startX + (targetX - startX) * f).toInt()
                layoutParams.y = (startY + (targetY - startY) * f).toInt()
                windowManager.updateViewLayout(floatingView, layoutParams)
            }
            start()
        }
    }

    private fun sideHide() {
        if (isExpanded || isSideHidden) return
        if (ballWidth == 0) ballWidth = flMainBall.width
        if (ballWidth == 0) return

        val startX = layoutParams.x
        val targetX = -(ballWidth - VISIBLE_TAB)

        ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(300)
            addUpdateListener { a ->
                val f = (a as ValueAnimator).animatedFraction
                layoutParams.x = (startX + (targetX - startX) * f).toInt()
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

        val startX = layoutParams.x
        ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(200)
            addUpdateListener { a ->
                val f = (a as ValueAnimator).animatedFraction
                layoutParams.x = (startX * (1 - f)).toInt()
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
        if (GameManager.state.value != ScriptState.IDLE && isOnGameDataPage) {
            hudManager.show()
        }
    }

    private fun removeAllViews() {
        removeFloatingView()
        hudManager.hide()
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

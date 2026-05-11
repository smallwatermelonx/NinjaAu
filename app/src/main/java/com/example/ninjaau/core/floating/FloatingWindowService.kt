package com.example.ninjaau.core.floating

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.example.ninjaau.R
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.ScriptState
import com.example.ninjaau.core.capture.CapturePermissionActivity
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.core.util.ToastUtil
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

class FloatingWindowService : Service() {
    companion object {
        private const val CHANNEL_ID = "FloatingWindowServiceChannel"
        private const val NOTIFICATION_ID = 102
        private const val AUTO_HIDE_MS = 5000L
        private const val ANIM_DURATION = 300L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var flMainBall: View
    private lateinit var llMenu: LinearLayout
    private lateinit var ivControlIcon: ImageView

    private var isExpanded = false
    private var isSideHidden = false
    private var screenWidth = 0
    private var ballWidth = 0
    private val handler = Handler(Looper.getMainLooper())
    private val sideHideRunnable = Runnable { sideHide() }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constant.HIDE_FLOATING_WINDOW -> removeFloatingView()
                Constant.SHOW_FLOATING_WINDOW -> addFloatingView()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFloatingForeground()

        // 尝试从本地恢复上次的授权数据（进程重启后无需重新授权）
        PermissionManager.restoreProjectionPermission(this)

        val initSuccess = PermissionManager.initMediaProjection(this)
        if (!initSuccess) {
            LogUtil.e("FloatingWindowService", "MediaProjection初始化失败")
            Toast.makeText(this, "截图权限初始化失败，请重新点击Link Start授权", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val filter = IntentFilter().apply {
            addAction(Constant.HIDE_FLOATING_WINDOW)
            addAction(Constant.SHOW_FLOATING_WINDOW)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        initFloatingView()
        observeScriptState()
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
        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun observeScriptState() {
        serviceScope.launch {
            GameManager.state.collectLatest { state ->
                when (state) {
                    ScriptState.RUNNING -> ivControlIcon.setImageResource(android.R.drawable.ic_media_pause)
                    else -> ivControlIcon.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    private fun initFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null)
        flMainBall = floatingView.findViewById(R.id.fl_main_ball)
        llMenu = floatingView.findViewById(R.id.ll_menu)
        ivControlIcon = floatingView.findViewById(R.id.iv_control_icon)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels

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
            y = 500
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
                        layoutParams.y = (initialY + dy).toInt()
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

    private fun toggleMenu() {
        isExpanded = !isExpanded
        if (isExpanded) {
            llMenu.visibility = View.VISIBLE
            val animSet = AnimationSet(true).apply {
                addAnimation(TranslateAnimation(-250f, 0f, 0f, 0f).apply {
                    duration = ANIM_DURATION
                    interpolator = OvershootInterpolator(1.2f)
                })
                addAnimation(AlphaAnimation(0f, 1f).apply {
                    duration = ANIM_DURATION
                })
                duration = ANIM_DURATION
                fillAfter = true
            }
            llMenu.startAnimation(animSet)
        } else {
            val animSet = AnimationSet(true).apply {
                addAnimation(TranslateAnimation(0f, -250f, 0f, 0f).apply {
                    duration = ANIM_DURATION
                    interpolator = AccelerateInterpolator()
                })
                addAnimation(AlphaAnimation(1f, 0f).apply {
                    duration = (ANIM_DURATION * 0.7).toLong()
                })
                duration = ANIM_DURATION
                fillAfter = false
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationEnd(animation: Animation?) {
                        llMenu.visibility = View.GONE
                        llMenu.translationX = 0f
                    }
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            }
            llMenu.startAnimation(animSet)
        }
    }

    private fun snapToEdge() {
        if (ballWidth == 0) ballWidth = flMainBall.width
        layoutParams.x = if (layoutParams.x + floatingView.width / 2 < screenWidth / 2) 0 else screenWidth - ballWidth
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun sideHide() {
        if (isExpanded || isSideHidden) return
        if (ballWidth == 0) ballWidth = flMainBall.width
        if (ballWidth == 0) return
        val hideOffset = (ballWidth * 0.8).toInt()
        layoutParams.x = if (layoutParams.x <= 0) -hideOffset else screenWidth - (ballWidth - hideOffset)
        isSideHidden = true
        flMainBall.alpha = 0.5f
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun restoreFromSide() {
        if (!isSideHidden) return
        if (ballWidth == 0) ballWidth = flMainBall.width
        layoutParams.x = if (layoutParams.x < 0) 0 else screenWidth - ballWidth
        isSideHidden = false
        flMainBall.alpha = 1.0f
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(sideHideRunnable)
        if (!isExpanded) handler.postDelayed(sideHideRunnable, AUTO_HIDE_MS)
    }

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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        handler.removeCallbacks(sideHideRunnable)
        unregisterReceiver(receiver)
        removeFloatingView()
    }
}

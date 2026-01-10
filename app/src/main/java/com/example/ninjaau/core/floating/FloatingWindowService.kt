package com.example.ninjaau.core.floating

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.core.content.ContextCompat
import com.example.ninjaau.R
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.ScriptState
import com.example.ninjaau.core.screenshot.ForegroundScreenshotService
import com.example.ninjaau.core.screenshot.ScreenshotPermissionActivity
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * 高级智能悬浮窗Service
 * 核心修复：作为具备 mediaProjection 属性的前台服务，承担初始化 MediaProjection 的职责
 */
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
        
        // 1. 立即启动前台服务通知
        startFloatingForeground()

        // 2. 核心修复：在这个前台服务内完成 MediaProjection 的初始化
        // 这样就满足了系统 "require a foreground service" 的安全要求
        val success = PermissionManager.initMediaProjection(this)
        if (success) {
            LogUtil.i("FloatingWindowService", "MediaProjection 在服务中初始化成功")
        } else {
            LogUtil.e("FloatingWindowService", "MediaProjection 在服务中初始化失败，可能未获得授权")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NinjaAu 悬浮窗活跃中")
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun observeScriptState() {
        serviceScope.launch {
            GameManager.state.collectLatest { state ->
                when (state) {
                    ScriptState.IDLE -> {
                        ivControlIcon.setImageResource(android.R.drawable.ic_media_play)
                    }
                    else -> {
                        ivControlIcon.setImageResource(android.R.drawable.ic_media_pause)
                    }
                }
            }
        }
    }

    private fun initFloatingView() {
        floatingView = View.inflate(this, R.layout.layout_floating_window, null)
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

        floatingView.findViewById<View>(R.id.btn_screenshot).setOnClickListener {
            resetHideTimer()
            showScreenshotDialog()
        }

        floatingView.findViewById<View>(R.id.iv_close).setOnClickListener {
            GameManager.stopScript()
            stopSelf()
        }
    }

    private fun toggleMenu() {
        isExpanded = !isExpanded
        if (isExpanded) {
            llMenu.visibility = View.VISIBLE
            val anim = TranslateAnimation(-200f, 0f, 0f, 0f).apply {
                duration = ANIM_DURATION
                fillAfter = true
            }
            llMenu.startAnimation(anim)
        } else {
            val anim = TranslateAnimation(0f, -200f, 0f, 0f).apply {
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
            llMenu.startAnimation(anim)
        }
    }

    private fun snapToEdge() {
        layoutParams.x = if (layoutParams.x + floatingView.width / 2 < screenWidth / 2) 0 else screenWidth - flMainBall.width
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun sideHide() {
        if (isExpanded || isSideHidden) return
        val hideOffset = (flMainBall.width * 0.8).toInt()
        layoutParams.x = if (layoutParams.x <= 0) -hideOffset else screenWidth - (flMainBall.width - hideOffset)
        isSideHidden = true
        flMainBall.alpha = 0.5f
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun restoreFromSide() {
        if (!isSideHidden) return
        layoutParams.x = if (layoutParams.x < 0) 0 else screenWidth - flMainBall.width
        isSideHidden = false
        flMainBall.alpha = 1.0f
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(sideHideRunnable)
        if (!isExpanded) handler.postDelayed(sideHideRunnable, AUTO_HIDE_MS)
    }

    private fun showScreenshotDialog() {
        if (!PermissionManager.hasProjectionPermission()) {
            startActivity(Intent(this, ScreenshotPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        
        val input = EditText(this).apply { hint = "模板名称" }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("采集模板").setView(input)
            .setPositiveButton("截图") { _, _ ->
                val name = input.text.toString().ifEmpty { "temp_${System.currentTimeMillis()}" }
                val intent = Intent(this, ForegroundScreenshotService::class.java).apply {
                    putExtra(ForegroundScreenshotService.EXTRA_TEMPLATE_NAME, name)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            }.create()
            
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun addFloatingView() {
        if (::floatingView.isInitialized && floatingView.parent == null) {
            windowManager.addView(floatingView, layoutParams)
        }
    }

    private fun removeFloatingView() {
        if (::floatingView.isInitialized && floatingView.parent != null) {
            windowManager.removeView(floatingView)
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

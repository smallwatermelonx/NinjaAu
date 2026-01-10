package com.example.ninjaau.core

import android.content.Context
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.recognition.LoginPageRecognizer
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 自动化业务状态枚举
 */
enum class ScriptState {
    IDLE,       // 空闲
    LOGIN_CHECK,// 登录页检测中
    HALL_LOOP   // 大厅循环中
}

/**
 * 全局业务总管：负责自动化主循环、状态管理与业务分发
 */
object GameManager {
    private const val TAG = "GameManager"

    private val _state = MutableStateFlow(ScriptState.IDLE)
    val state: StateFlow<ScriptState> = _state

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private const val LOGIN_CHECK_INTERVAL = 5000L 
    private const val HALL_CHECK_INTERVAL = 1000L  

    /**
     * 切换脚本运行状态（供悬浮窗一键调用）
     */
    fun toggleScript(context: Context) {
        if (_state.value == ScriptState.IDLE) {
            startScript(context)
        } else {
            stopScript()
        }
    }

    /**
     * 启动脚本
     */
    fun startScript(context: Context) {
        if (_state.value != ScriptState.IDLE) return

        // 1. 核心改进：在启动循环前，先初始化全局唯一的 MediaProjection
        val initSuccess = PermissionManager.initMediaProjection(context)
        if (!initSuccess) {
            LogUtil.e(TAG, "MediaProjection 初始化失败，请确保已点击 Link Start 授权")
            return
        }

        _state.value = ScriptState.LOGIN_CHECK
        val appContext = context.applicationContext
        
        job = scope.launch {
            LogUtil.i(TAG, "自动化主流程已启动")
            try {
                // 第一步：登录页检测（循环复用全局 MediaProjection，不再崩溃）
                loginCheckLoop(appContext)
                // 第二步：进入大厅循环
                hallLoop(appContext)
            } catch (e: CancellationException) {
                LogUtil.i(TAG, "自动化任务已取消")
            } finally {
                _state.value = ScriptState.IDLE
            }
        }
    }

    private suspend fun CoroutineScope.loginCheckLoop(context: Context) {
        val loginRecognizer = LoginPageRecognizer(context)
        while (isActive && _state.value == ScriptState.LOGIN_CHECK) {
            // 这里调用 checkLoginPage()，它会复用 PermissionManager 里的实例
            val (isLoginPage, clickCoord) = loginRecognizer.checkLoginPage()
            if (isLoginPage && clickCoord != null) {
                NinjaAccessibilityService.getInstance()?.clickAt(clickCoord.first, clickCoord.second)
                LogUtil.i(TAG, "识别到登录页并点击，准备切换到大厅状态")
                delay(2000) // 等待画面过渡
                _state.value = ScriptState.HALL_LOOP
                return
            }
            delay(LOGIN_CHECK_INTERVAL)
        }
    }

    private suspend fun CoroutineScope.hallLoop(context: Context) {
        LogUtil.i(TAG, "已进入大厅状态桩，开启高频巡检")
        while (isActive && _state.value == ScriptState.HALL_LOOP) {
            // TODO: 后续在这里添加具体业务（如检测悬赏）
            delay(HALL_CHECK_INTERVAL)
        }
    }

    /**
     * 彻底停止脚本并释放所有资源
     */
    fun stopScript() {
        LogUtil.i(TAG, "手动停止流程，正在清理资源...")
        job?.cancel()
        job = null
        
        // 2. 核心改进：只有在脚本停止时，才释放截图通行证
        PermissionManager.releaseMediaProjection()
        
        _state.value = ScriptState.IDLE
    }

    fun isGameRunning(context: Context): Boolean {
        return com.example.ninjaau.core.appcontrol.AdbController.isAppRunning(
            context,
            com.example.ninjaau.core.util.Constant.NINJA_GAME_PACKAGE
        )
    }
}

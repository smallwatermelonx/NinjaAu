package com.example.ninjaau.core

import android.content.Context
import android.widget.Toast
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.appcontrol.AdbController
import com.example.ninjaau.core.recognition.BountyRecognizer
import com.example.ninjaau.core.recognition.LoginPageRecognizer
import com.example.ninjaau.core.util.Constant
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 自动化业务状态枚举（新增PAUSED状态，仅标记大厅流程暂停）
 */
enum class ScriptState {
    IDLE,       // 空闲
    LOGIN_CHECK,// 登录页检测中（不可暂停）
    HALL_LOOP,  // 大厅循环中（可暂停）
    PAUSED      // 大厅流程暂停中
}

/**
 * 全局业务总管：仅负责状态管理、协程生命周期、流程分发
 */
object GameManager {
    private const val TAG = "GameManager"

    private val _state = MutableStateFlow(ScriptState.IDLE)
    val state: StateFlow<ScriptState> = _state

    private var mainJob: Job? = null       // 整个自动化流程的主协程（登录+大厅）
    private var hallJob: Job? = null       // 仅大厅循环的协程（单独控制暂停/恢复）
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private const val LOGIN_CHECK_INTERVAL = 5000L
    private const val HALL_CHECK_INTERVAL = 1000L

    /**
     * 切换脚本运行状态（供悬浮窗一键调用）
     */
    fun toggleScript(context: Context) {
        when (_state.value) {
            ScriptState.IDLE -> startScript(context)
            ScriptState.LOGIN_CHECK -> Toast.makeText(context, "登录检测中，暂不支持暂停", Toast.LENGTH_SHORT).show()
            ScriptState.HALL_LOOP -> pauseHallLoop()
            ScriptState.PAUSED -> resumeHallLoop(context)
        }
    }

    /**
     * 启动全流程（登录检测 → 大厅循环）
     */
    fun startScript(context: Context) {
        if (_state.value != ScriptState.IDLE) return

        if (PermissionManager.mediaProjection == null) {
            LogUtil.e(TAG, "MediaProjection 未初始化，请先启动悬浮窗服务（点击Link Start）")
            Toast.makeText(context, "请先点击Link Start启动服务", Toast.LENGTH_SHORT).show()
            return
        }

        _state.value = ScriptState.LOGIN_CHECK
        val appContext = context.applicationContext

        // 主协程：登录检测 + 启动大厅循环
        mainJob = scope.launch {
            LogUtil.i(TAG, "自动化主流程已启动，开始登录检测")
            try {
                // 第一步：登录页检测（一次性流程，不可暂停）
//                loginCheckLoop(appContext)
                // 第二步：登录完成后，启动大厅循环
                startHallLoop(appContext)
            } catch (e: CancellationException) {
                LogUtil.i(TAG, "自动化主流程已取消")
            } catch (e: Exception) {
                LogUtil.e(TAG, "自动化主流程执行异常", e)
                _state.value = ScriptState.IDLE
            }
        }
    }

    /**
     * 仅启动大厅循环（供恢复时调用）
     */
    private fun startHallLoop(context: Context) {
        if (_state.value == ScriptState.PAUSED || _state.value == ScriptState.LOGIN_CHECK) {
            _state.value = ScriptState.HALL_LOOP
        }
        // 单独控制大厅循环的协程，方便暂停/恢复
        hallJob = scope.launch {
            LogUtil.i(TAG, "大厅循环已启动（可暂停）")
            hallLoop(context)
        }
    }

    /**
     * 暂停大厅循环（仅停业务流程，不释放资源）
     */
    private fun pauseHallLoop() {
        hallJob?.cancel(CancellationException("大厅流程暂停"))
        hallJob = null
        _state.value = ScriptState.PAUSED
        PermissionManager.pauseMediaProjection()
        LogUtil.i(TAG, "大厅流程已暂停，登录检测不受影响")
    }

    /**
     * 恢复大厅循环
     */
    private fun resumeHallLoop(context: Context) {
        if (PermissionManager.resumeMediaProjection(context)) {
            startHallLoop(context)
            LogUtil.i(TAG, "大厅流程已恢复")
        } else {
            Toast.makeText(context, "截图权限失效，请重新启动悬浮窗", Toast.LENGTH_SHORT).show()
            _state.value = ScriptState.IDLE
        }
    }

    /**
     * 大厅循环入口（仅调用BountyRecognizer的业务流程，无具体逻辑）
     */
    private suspend fun CoroutineScope.hallLoop(context: Context) {
        val bountyRecognizer = BountyRecognizer(context)
        val hallCheckInterval = HALL_CHECK_INTERVAL

        while (isActive && _state.value == ScriptState.HALL_LOOP) {
            // 直接调用BountyRecognizer的悬赏流程，GameManager不处理具体步骤
            val isAllStepsSuccess = bountyRecognizer.executeBountyProcess()

            if (isAllStepsSuccess) {
                LogUtil.i(TAG, "✅ 本轮悬赏流程执行完成，等待下一轮检测...")
            } else {
                LogUtil.w(TAG, "❌ 本轮悬赏流程执行失败，等待下一轮检测...")
                delay(1000)
            }
            delay(hallCheckInterval)
        }
    }

    /**
     * 登录页检测循环（一次性流程，不可暂停）
     */
    private suspend fun CoroutineScope.loginCheckLoop(context: Context) {
        val loginRecognizer = LoginPageRecognizer(context)
        while (isActive && _state.value == ScriptState.LOGIN_CHECK) {
            val (isLoginPage, clickCoord) = loginRecognizer.checkLoginPage()
            if (isLoginPage && clickCoord != null) {
                NinjaAccessibilityService.getInstance()?.clickAt(clickCoord.first, clickCoord.second)
                LogUtil.i(TAG, "识别到登录页并点击，准备进入大厅循环")
                delay(2000)
                return
            }
            delay(LOGIN_CHECK_INTERVAL)
        }
    }

    /**
     * 彻底停止所有流程（释放资源）
     */
    fun stopScript() {
        LogUtil.i(TAG, "手动停止所有流程，正在清理资源...")
        mainJob?.cancel()
        hallJob?.cancel()
        mainJob = null
        hallJob = null

        PermissionManager.releaseMediaProjection()
        _state.value = ScriptState.IDLE
        LogUtil.i(TAG, "所有流程已停止，资源已释放")
    }

    /**
     * 检查游戏是否运行
     */
    fun isGameRunning(context: Context): Boolean {
        return AdbController.isAppRunning(
            context,
            Constant.NINJA_GAME_PACKAGE
        )
    }
}
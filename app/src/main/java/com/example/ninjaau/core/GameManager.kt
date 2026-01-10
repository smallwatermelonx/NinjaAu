package com.example.ninjaau.core

import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.WindowManager
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.recognition.LoginPageRecognizer
import com.example.ninjaau.core.recognition.OpenCVTemplateMatcher
import com.example.ninjaau.core.uimap.UIMap
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.core.util.TemplateScreenshotTool
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

    // 全局复用 MediaProjection 实例
    private var mediaProjection: MediaProjection? = null

    // 检测频率配置
    private const val LOGIN_CHECK_INTERVAL = 5000L // 登录页5秒检测一次
    private const val HALL_CHECK_INTERVAL = 1000L  // 大厅1秒检测一次

    /**
     * 启动脚本（总开关）
     */
    fun startScript(context: Context) {
        if (_state.value != ScriptState.IDLE) return
        _state.value = ScriptState.LOGIN_CHECK

        val appContext = context.applicationContext
        job = scope.launch {
            LogUtil.i(TAG, "启动自动化：先检测登录页")
            // 第一步：登录页检测（仅执行一次，成功后切换到大厅）
            loginCheckLoop(appContext)

            // 第二步：登录成功→进入大厅循环
            hallLoop(appContext)
        }
    }

    /**
     * 登录页检测循环（5秒/次，成功则退出）
     * 修复：增加 CoroutineScope 接收者以使用 isActive
     */
    private suspend fun CoroutineScope.loginCheckLoop(context: Context) {
        val loginRecognizer = LoginPageRecognizer(context)
        while (isActive && _state.value == ScriptState.LOGIN_CHECK) {
            try {
                // 1. 识别登录页
                val (isLoginPage, clickCoord) = loginRecognizer.checkLoginPage()
                if (isLoginPage && clickCoord != null) {
                    // 2. 调用无障碍服务点击登录按钮
                    val accessibilityService = NinjaAccessibilityService.getInstance()
                    if (accessibilityService != null) {
                        val (x, y) = clickCoord
                        val clickSuccess = accessibilityService.clickAt(x, y)
                        if (clickSuccess) {
                            LogUtil.i(TAG, "登录按钮点击成功！坐标：($x, $y)")
                            delay(2000) // 等待界面跳转
                            _state.value = ScriptState.HALL_LOOP // 切换到大厅状态
                            return // 退出登录检测循环
                        } else {
                            LogUtil.e(TAG, "登录按钮点击失败：无障碍服务未就绪")
                        }
                    } else {
                        LogUtil.e(TAG, "登录按钮点击失败：无障碍服务实例为空")
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "登录检测异常：${e.message}")
            }
            delay(LOGIN_CHECK_INTERVAL) // 5秒检测一次
        }
    }

    /**
     * 大厅循环（1秒/次，为后续悬赏闭环做准备）
     * 修复：增加 CoroutineScope 接收者以使用 isActive
     */
    private suspend fun CoroutineScope.hallLoop(context: Context) {
        LogUtil.i(TAG, "进入大厅循环，1秒/次检测")
        while (isActive && _state.value == ScriptState.HALL_LOOP) {
            try {
                // TODO: 后续添加大厅识别逻辑（比如找悬赏按钮）
                LogUtil.d(TAG, "大厅循环检测中...（可添加悬赏识别逻辑）")

                // 示例：检测是否还在大厅（后续补充）
                // val isAtHall = HallPageRecognizer().checkHallPage()
                // if (!isAtHall) _state.value = ScriptState.LOGIN_CHECK // 回到登录检测

            } catch (e: Exception) {
                LogUtil.e(TAG, "大厅循环异常：${e.message}")
            }
            delay(HALL_CHECK_INTERVAL) // 1秒检测一次
        }
    }

    /**
     * 停止脚本
     */
    fun stopScript() {
        LogUtil.i(TAG, "停止自动化脚本")
        _state.value = ScriptState.IDLE
        job?.cancel()
        job = null
    }

    /**
     * 检查游戏是否运行（复用AdbController）
     */
    fun isGameRunning(context: Context): Boolean {
        return com.example.ninjaau.core.appcontrol.AdbController.isAppRunning(
            context,
            com.example.ninjaau.core.util.Constant.NINJA_GAME_PACKAGE
        )
    }
}

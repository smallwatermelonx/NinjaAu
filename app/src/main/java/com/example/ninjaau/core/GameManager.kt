package com.example.ninjaau.core

import android.content.Context
import android.widget.Toast
import com.example.ninjaau.core.config.ScriptConfigRepository
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GameContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ScriptState { IDLE, RUNNING, PAUSED }

object GameManager {
    private const val TAG = "GameManager"

    private val _state = MutableStateFlow(ScriptState.IDLE)
    val state: StateFlow<ScriptState> = _state

    private var mainJob: Job? = null
    private var savedContext: GameContext? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _logEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logEvents: SharedFlow<String> = _logEvents

    private val _bountyProgress = MutableStateFlow<Map<BountyGrade, Pair<Int, Int>>>(emptyMap())
    val bountyProgress: StateFlow<Map<BountyGrade, Pair<Int, Int>>> = _bountyProgress

    private val _pageEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val pageEvents: SharedFlow<String> = _pageEvents

    fun toggleScript(context: Context) {
        when (_state.value) {
            ScriptState.IDLE -> startScript(context)
            ScriptState.RUNNING -> pauseScript()
            ScriptState.PAUSED -> resumeScript(context)
        }
    }

    @Synchronized
    fun startScript(context: Context) {
        if (_state.value != ScriptState.IDLE) return
        _state.value = ScriptState.RUNNING
        val appContext = context.applicationContext

        val snapshot = ScriptConfigRepository.snapshot()

        mainJob = scope.launch {
            postLog("⏳ 等待截图授权...")

            var waited = 0
            while (PermissionManager.mediaProjection == null && waited < 20) {
                if (PermissionManager.isProjectionLost) {
                    postLog("⚠ 截图权限已被系统回收")
                    PermissionManager.clearProjectionPermission(appContext)
                    _state.value = ScriptState.IDLE
                    return@launch
                }
                delay(500)
                waited++
            }
            if (PermissionManager.mediaProjection == null) {
                postLog("❌ 截图授权超时(10s)，请重新点击Link Start")
                PermissionManager.clearProjectionPermission(appContext)
                Toast.makeText(appContext, "截图授权失败，请重新启动", Toast.LENGTH_SHORT).show()
                _state.value = ScriptState.IDLE
                return@launch
            }

            postLog("✅ 截图就绪，脚本开始运行")
            val engine = WorkflowEngine(
                appContext,
                postLog = { msg -> postLog(msg) },
                onPageEvent = { event -> _pageEvents.tryEmit(event) }
            )
            try {
                engine.runLoop(
                    snapshot.enabledBountyConfigs,
                    dailyEnabled = snapshot.dailyEnabled,
                    personalBountyEnabled = snapshot.personalEnabled,
                    personalConfigs = snapshot.enabledPersonalConfigs,
                    nsEnabled = snapshot.nsEnabled,
                    onProgress = { progress -> _bountyProgress.value = progress }
                )
                savedContext = engine.lastContext
            } catch (e: CancellationException) {
                LogUtil.i(TAG, "脚本已取消")
                savedContext = engine.lastContext
            } catch (e: Exception) {
                LogUtil.e(TAG, "脚本执行异常", e)
            }
            if (_state.value != ScriptState.PAUSED) {
                savedContext = null
            }
            _state.value = ScriptState.IDLE
        }
    }

    fun pauseScript() {
        if (_state.value != ScriptState.RUNNING) return
        postLog("⏸ 脚本已暂停")
        _state.value = ScriptState.PAUSED
        mainJob?.cancel()
        mainJob = null
    }

    @Synchronized
    fun resumeScript(context: Context) {
        if (_state.value != ScriptState.PAUSED) return
        val ctx = savedContext ?: run {
            startScript(context)
            return
        }
        savedContext = null
        _state.value = ScriptState.RUNNING
        val appContext = context.applicationContext

        mainJob = scope.launch {
            try {
                WorkflowEngine(
                    appContext,
                    postLog = { msg -> postLog(msg) },
                    onPageEvent = { event -> _pageEvents.tryEmit(event) }
                ).resumeLoop(ctx) { progress -> _bountyProgress.value = progress }
            } catch (e: CancellationException) {
                LogUtil.i(TAG, "脚本已取消")
            } catch (e: Exception) {
                LogUtil.e(TAG, "脚本执行异常", e)
            }
            savedContext = null
            _state.value = ScriptState.IDLE
        }
    }

    fun stopScript() {
        if (_state.value == ScriptState.IDLE) return
        postLog("⏹ 停止脚本")
        savedContext = null
        mainJob?.cancel()
        mainJob = null
        _state.value = ScriptState.IDLE
    }

    private fun postLog(msg: String) {
        LogUtil.i(TAG, msg)
        _logEvents.tryEmit(msg)
    }
}

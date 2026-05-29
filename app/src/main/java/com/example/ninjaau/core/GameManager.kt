package com.example.ninjaau.core

import android.content.Context
import android.widget.Toast
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ScriptState {
    IDLE,
    RUNNING,
    PAUSED
}

object GameManager {
    private const val TAG = "GameManager"

    private val _state = MutableStateFlow(ScriptState.IDLE)
    val state: StateFlow<ScriptState> = _state

    private var mainJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var selectedBounties: List<BountyConfig> = BountyConfig.defaultList().filter { it.enabled }

    /** 日志事件流 — UI 可收集此流显示到运行日志面板 */
    private val _logEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logEvents: SharedFlow<String> = _logEvents

    /** 悬赏完成进度 — 浮窗可收集此流显示完成次数 */
    private val _bountyProgress = MutableStateFlow<Map<BountyGrade, Pair<Int, Int>>>(emptyMap())
    val bountyProgress: StateFlow<Map<BountyGrade, Pair<Int, Int>>> = _bountyProgress

    /** 页面跳转事件流 — 用于中上方 Toast 提示 */
    private val _pageEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val pageEvents: SharedFlow<String> = _pageEvents

    private fun postLog(msg: String) {
        LogUtil.i(TAG, msg)
        _logEvents.tryEmit(msg)
    }

    fun updateBountyConfigs(configs: List<BountyConfig>) {
        selectedBounties = configs
    }

    /** 返回当前已勾选的等级列表（供 UI 读取） */
    fun getSelectedGrades(): List<BountyGrade> {
        return selectedBounties.filter { it.enabled }.map { it.grade }
    }

    fun toggleScript(context: Context) {
        when (_state.value) {
            ScriptState.IDLE -> startScript(context)
            ScriptState.RUNNING -> pauseScript()
            ScriptState.PAUSED -> resumeScript(context)
        }
    }

    fun startScript(context: Context) {
        if (_state.value != ScriptState.IDLE) return
        _state.value = ScriptState.RUNNING
        val appContext = context.applicationContext

        mainJob = scope.launch {
            postLog("⏳ 等待截图授权...")

            // 进程重启后尝试从本地恢复授权数据
            PermissionManager.restoreProjectionPermission(appContext)

            // 等待 FloatingWindowService 启动并初始化 MediaProjection
            // （MediaProjection 必须在前台 Service 中创建，不能用 Application context）
            var waited = 0
            while (PermissionManager.mediaProjection == null && waited < 20) {
                delay(500)
                waited++
            }
            if (PermissionManager.mediaProjection == null) {
                postLog("❌ 截图授权超时(10s)，清除旧授权数据，请重新点击Link Start")
                PermissionManager.clearProjectionPermission()
                Toast.makeText(appContext, "截图授权失败，请重新启动", Toast.LENGTH_SHORT).show()
                _state.value = ScriptState.IDLE
                return@launch
            }

            postLog("✅ 截图就绪，脚本开始运行")
            try {
                WorkflowEngine(
                    appContext,
                    postLog = { msg -> postLog(msg) },
                    onPageEvent = { event -> _pageEvents.tryEmit(event) }
                ).runLoop(
                    selectedBounties,
                    onProgress = { progress -> _bountyProgress.value = progress }
                )
            } catch (e: CancellationException) {
                LogUtil.i(TAG, "脚本已取消")
            } catch (e: Exception) {
                LogUtil.e(TAG, "脚本执行异常", e)
            }
            _state.value = ScriptState.IDLE
        }
    }

    fun pauseScript() {
        if (_state.value != ScriptState.RUNNING) return
        postLog("⏸ 脚本已暂停")
        mainJob?.cancel(CancellationException("脚本暂停"))
        mainJob = null
        _state.value = ScriptState.PAUSED
        PermissionManager.pauseMediaProjection()
    }

    fun resumeScript(context: Context) {
        if (_state.value != ScriptState.PAUSED) return
        if (PermissionManager.resumeMediaProjection(context)) {
            postLog("▶ 脚本已恢复")
            _state.value = ScriptState.RUNNING
            val appContext = context.applicationContext
            mainJob = scope.launch {
                try {
                    WorkflowEngine(
                        appContext,
                        postLog = { msg -> postLog(msg) },
                        onPageEvent = { event -> _pageEvents.tryEmit(event) }
                    ).runLoop(
                        selectedBounties,
                        onProgress = { progress -> _bountyProgress.value = progress }
                    )
                } catch (e: CancellationException) {
                    postLog("脚本已取消")
                } catch (e: Exception) {
                    postLog("❌ 脚本异常: ${e.message}")
                }
                _state.value = ScriptState.IDLE
            }
        } else {
            postLog("❌ 截图权限失效")
            Toast.makeText(context, "截图权限失效，请重新启动悬浮窗", Toast.LENGTH_SHORT).show()
            _state.value = ScriptState.IDLE
        }
    }

    fun stopScript() {
        postLog("⏹ 停止脚本")
        mainJob?.cancel()
        mainJob = null
        PermissionManager.releaseMediaProjection()
        _state.value = ScriptState.IDLE
    }
}

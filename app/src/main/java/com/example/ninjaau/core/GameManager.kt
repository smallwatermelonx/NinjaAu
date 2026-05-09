package com.example.ninjaau.core

import android.content.Context
import android.widget.Toast
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
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

    private var selectedBounties: List<BountyConfig> = BountyConfig.presetList().filter { it.enabled }

    fun updateBountyConfigs(configs: List<BountyConfig>) {
        selectedBounties = configs
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
        if (PermissionManager.mediaProjection == null) {
            LogUtil.e(TAG, "MediaProjection 未初始化")
            Toast.makeText(context, "请先点击Link Start启动服务", Toast.LENGTH_SHORT).show()
            return
        }
        _state.value = ScriptState.RUNNING
        val appContext = context.applicationContext

        mainJob = scope.launch {
            LogUtil.i(TAG, "自动化脚本启动")
            try {
                val engine = ScriptEngine(appContext)
                while (isActive && _state.value == ScriptState.RUNNING) {
                    engine.runLoop(selectedBounties)
                }
            } catch (e: CancellationException) {
                LogUtil.i(TAG, "脚本已取消")
            } catch (e: Exception) {
                LogUtil.e(TAG, "脚本执行异常", e)
                _state.value = ScriptState.IDLE
            }
        }
    }

    private fun pauseScript() {
        mainJob?.cancel(CancellationException("脚本暂停"))
        mainJob = null
        _state.value = ScriptState.PAUSED
        PermissionManager.pauseMediaProjection()
        LogUtil.i(TAG, "脚本已暂停")
    }

    private fun resumeScript(context: Context) {
        if (PermissionManager.resumeMediaProjection(context)) {
            _state.value = ScriptState.RUNNING
            val appContext = context.applicationContext
            mainJob = scope.launch {
                LogUtil.i(TAG, "脚本已恢复")
                try {
                    val engine = ScriptEngine(appContext)
                    while (isActive && _state.value == ScriptState.RUNNING) {
                        engine.runLoop(selectedBounties)
                    }
                } catch (e: CancellationException) {
                    LogUtil.i(TAG, "脚本已取消")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "脚本执行异常", e)
                    _state.value = ScriptState.IDLE
                }
            }
        } else {
            Toast.makeText(context, "截图权限失效，请重新启动悬浮窗", Toast.LENGTH_SHORT).show()
            _state.value = ScriptState.IDLE
        }
    }

    fun stopScript() {
        LogUtil.i(TAG, "停止脚本，清理资源")
        mainJob?.cancel()
        mainJob = null
        PermissionManager.releaseMediaProjection()
        _state.value = ScriptState.IDLE
    }
}

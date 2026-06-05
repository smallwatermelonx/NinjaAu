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
    RUNNING
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

    /** 组队邀请检测开关（默认关闭，UI 可配置） */
    private val _inviteCheckEnabled = MutableStateFlow(false)
    val inviteCheckEnabled: StateFlow<Boolean> = _inviteCheckEnabled

    /** 个人悬赏开关（默认关闭，UI 可配置） */
    private val _personalBountyEnabled = MutableStateFlow(false)
    val personalBountyEnabled: StateFlow<Boolean> = _personalBountyEnabled

    /** 逆袭悬赏开关（默认关闭，UI 可配置） */
    private val _nsEnabled = MutableStateFlow(false)
    val nsEnabled: StateFlow<Boolean> = _nsEnabled

    /** 日常悬赏开关（默认开启，UI 可配置） */
    private val _dailyEnabled = MutableStateFlow(true)
    val dailyEnabled: StateFlow<Boolean> = _dailyEnabled

    fun setDailyEnabled(enabled: Boolean) {
        _dailyEnabled.value = enabled
    }

    fun setNsEnabled(enabled: Boolean) {
        _nsEnabled.value = enabled
    }

    private const val PREFS_NAME = "script_prefs"
    private const val KEY_INVITE_CHECK = "invite_check_enabled"

    fun loadInviteCheckSetting(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _inviteCheckEnabled.value = prefs.getBoolean(KEY_INVITE_CHECK, false)
    }

    fun setInviteCheckEnabled(context: Context, enabled: Boolean) {
        _inviteCheckEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INVITE_CHECK, enabled).apply()
    }

    private const val KEY_PERSONAL_BOUNTY = "personal_bounty_enabled"

    fun loadPersonalBountySetting(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _personalBountyEnabled.value = prefs.getBoolean(KEY_PERSONAL_BOUNTY, false)
    }

    fun setPersonalBountyEnabled(context: Context, enabled: Boolean) {
        _personalBountyEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PERSONAL_BOUNTY, enabled).apply()
    }

    private fun postLog(msg: String) {
        LogUtil.i(TAG, msg)
        _logEvents.tryEmit(msg)
    }

    fun updateBountyConfigs(configs: List<BountyConfig>) {
        selectedBounties = configs
    }

    private var selectedPersonalBounties: List<BountyConfig> = BountyConfig.defaultList().filter { it.enabled }

    fun updatePersonalBountyConfigs(configs: List<BountyConfig>) {
        selectedPersonalBounties = configs
    }

    /** 返回当前已勾选的等级列表（供 UI 读取） */
    fun getSelectedGrades(): List<BountyGrade> {
        return selectedBounties.filter { it.enabled }.map { it.grade }
    }

    fun toggleScript(context: Context) {
        when (_state.value) {
            ScriptState.IDLE -> startScript(context)
            ScriptState.RUNNING -> stopScript()
        }
    }

    fun startScript(context: Context) {
        if (_state.value != ScriptState.IDLE) return
        _state.value = ScriptState.RUNNING
        val appContext = context.applicationContext

        mainJob = scope.launch {
            postLog("⏳ 等待截图授权...")

            // 等待 FloatingWindowService 启动并初始化 MediaProjection
            // （MediaProjection 必须在前台 Service 中创建，不能用 Application context）
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
            try {
                WorkflowEngine(
                    appContext,
                    postLog = { msg -> postLog(msg) },
                    onPageEvent = { event -> _pageEvents.tryEmit(event) }
                ).runLoop(
                    selectedBounties,
                    dailyEnabled = _dailyEnabled.value,
                    personalBountyEnabled = _personalBountyEnabled.value,
                    personalConfigs = selectedPersonalBounties,
                    nsEnabled = _nsEnabled.value,
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

    fun stopScript() {
        if (_state.value == ScriptState.IDLE) return
        postLog("⏹ 停止脚本")
        mainJob?.cancel()
        mainJob = null
        _state.value = ScriptState.IDLE
    }
}

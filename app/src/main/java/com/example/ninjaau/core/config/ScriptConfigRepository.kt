package com.example.ninjaau.core.config

import android.content.Context
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 脚本配置统一仓库 — UI 和 Engine 的唯一数据源。
 *
 * 职责：
 * 1. 持久化所有配置到 SharedPreferences
 * 2. 通过 StateFlow 暴露可观察状态
 * 3. 提供快照方法供 Engine 启动时读取（避免并发修改）
 */
object ScriptConfigRepository {

    private const val PREFS_NAME = "script_config"
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAll()
    }

    // ═══ 业务线开关 ═══

    private val _dailyEnabled = MutableStateFlow(true)
    val dailyEnabled: StateFlow<Boolean> = _dailyEnabled

    private val _personalEnabled = MutableStateFlow(false)
    val personalEnabled: StateFlow<Boolean> = _personalEnabled

    private val _nsEnabled = MutableStateFlow(false)
    val nsEnabled: StateFlow<Boolean> = _nsEnabled

    private val _treasureEnabled = MutableStateFlow(false)
    val treasureEnabled: StateFlow<Boolean> = _treasureEnabled

    fun setDailyEnabled(v: Boolean) { _dailyEnabled.value = v; save("daily_enabled", v) }
    fun setPersonalEnabled(v: Boolean) { _personalEnabled.value = v; save("personal_enabled", v) }
    fun setNsEnabled(v: Boolean) { _nsEnabled.value = v; save("ns_enabled", v) }
    fun setTreasureEnabled(v: Boolean) { _treasureEnabled.value = v; save("treasure_enabled", v) }

    // ═══ 组队邀请检测 ═══

    private val _inviteCheckEnabled = MutableStateFlow(false)
    val inviteCheckEnabled: StateFlow<Boolean> = _inviteCheckEnabled

    fun setInviteCheckEnabled(v: Boolean) { _inviteCheckEnabled.value = v; save("invite_check", v) }

    // ═══ 悬赏等级配置 ═══

    private val _bountyConfigs = MutableStateFlow(BountyGrade.sorted().map {
        BountyConfig(grade = it, enabled = !it.isEvent)
    })
    val bountyConfigs: StateFlow<List<BountyConfig>> = _bountyConfigs

    private val _personalConfigs = MutableStateFlow(BountyGrade.daily().map {
        BountyConfig(grade = it)
    })
    val personalConfigs: StateFlow<List<BountyConfig>> = _personalConfigs

    private val _nsConfigs = MutableStateFlow(BountyGrade.event().map {
        BountyConfig(grade = it)
    })
    val nsConfigs: StateFlow<List<BountyConfig>> = _nsConfigs

    fun setBountyConfigs(v: List<BountyConfig>) {
        _bountyConfigs.value = v
        saveGradeKeys("cfg_bounty_enabled", v)
        saveChaseDreamKeys("cfg_bounty_chase_dream", v)
    }

    fun setPersonalConfigs(v: List<BountyConfig>) {
        _personalConfigs.value = v
        saveGradeKeys("cfg_personal_enabled", v)
    }

    fun setNsConfigs(v: List<BountyConfig>) {
        _nsConfigs.value = v
        saveGradeKeys("cfg_ns_enabled", v)
    }

    // ═══ Engine 启动快照（线程安全） ═══

    fun snapshot(): ScriptSnapshot {
        return ScriptSnapshot(
            bountyConfigs = _bountyConfigs.value,
            personalConfigs = _personalConfigs.value,
            dailyEnabled = _dailyEnabled.value,
            personalEnabled = _personalEnabled.value,
            nsEnabled = _nsEnabled.value
        )
    }

    // ═══ 持久化 ═══

    private fun loadAll() {
        _dailyEnabled.value = prefs.getBoolean("daily_enabled", true)
        _personalEnabled.value = prefs.getBoolean("personal_enabled", false)
        _nsEnabled.value = prefs.getBoolean("ns_enabled", false)
        _treasureEnabled.value = prefs.getBoolean("treasure_enabled", false)
        _inviteCheckEnabled.value = prefs.getBoolean("invite_check", false)
        _bountyConfigs.value = loadGradeConfigs("cfg_bounty_enabled", "cfg_bounty_chase_dream")
        _personalConfigs.value = loadGradeConfigs("cfg_personal_enabled", null)
        // NS 只加载事件等级（NSS+, NS, NA），过滤掉非事件等级
        val rawNs = loadGradeConfigs("cfg_ns_enabled", null)
        _nsConfigs.value = rawNs.filter { it.grade.isEvent }
    }

    private fun loadGradeConfigs(enabledKey: String, chaseDreamKey: String?): List<BountyConfig> {
        val enabledKeys = prefs.getString(enabledKey, "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val chaseKeys = if (chaseDreamKey != null)
            prefs.getString(chaseDreamKey, "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        else emptyList()
        return BountyGrade.sorted().map { grade ->
            BountyConfig(
                grade = grade,
                enabled = enabledKeys.contains(grade.key),
                chaseDream = grade.canChaseDream && chaseKeys.contains(grade.key)
            )
        }
    }

    private fun save(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun saveGradeKeys(key: String, configs: List<BountyConfig>) {
        val keys = configs.filter { it.enabled }.joinToString(",") { it.grade.key }
        prefs.edit().putString(key, keys).apply()
    }

    private fun saveChaseDreamKeys(key: String, configs: List<BountyConfig>) {
        val keys = configs.filter { it.chaseDream }.joinToString(",") { it.grade.key }
        prefs.edit().putString(key, keys).apply()
    }
}

data class ScriptSnapshot(
    val bountyConfigs: List<BountyConfig>,
    val personalConfigs: List<BountyConfig>,
    val dailyEnabled: Boolean,
    val personalEnabled: Boolean,
    val nsEnabled: Boolean
) {
    val enabledBountyConfigs get() = bountyConfigs.filter { it.enabled }
    val enabledPersonalConfigs get() = personalConfigs.filter { it.enabled }
}

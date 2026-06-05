package com.example.ninjaau.core.util

import android.content.Context
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade

/**
 * 悬赏配置持久化存储
 * 用户勾选的悬赏等级保存到 SharedPreferences，下次启动自动恢复
 */
object BountyConfigStorage {
    private const val PREFS_NAME = "bounty_prefs"
    private const val KEY_ENABLED_GRADES = "enabled_grades"
    private const val KEY_CHASE_DREAM = "chase_dream_grades"
    private const val KEY_PERSONAL_ENABLED_GRADES = "personal_enabled_grades"
    private const val KEY_NS_ENABLED_GRADES = "ns_enabled_grades"
    private const val KEY_BUSINESS_ENABLED = "business_enabled"

    const val BUSINESS_DAILY = "daily"
    const val BUSINESS_PERSONAL = "personal"
    const val BUSINESS_NS = "ns"
    const val BUSINESS_TREASURE = "treasure"

    /** 加载首页业务线勾选状态，默认仅日常悬赏勾选 */
    fun loadBusinessEnabled(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BUSINESS_ENABLED, null)
        if (saved == null) return setOf(BUSINESS_DAILY)
        return saved.split(",").filter { it.isNotEmpty() }.toSet()
    }

    /** 保存首页业务线勾选状态 */
    fun saveBusinessEnabled(context: Context, enabled: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUSINESS_ENABLED, enabled.joinToString(",")).apply()
    }

    fun load(context: Context): List<BountyConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabledKeys = prefs.getString(KEY_ENABLED_GRADES, "")?.split(",")
            ?.filter { it.isNotEmpty() } ?: emptyList()
        val chaseDreamKeys = prefs.getString(KEY_CHASE_DREAM, "")?.split(",")
            ?.filter { it.isNotEmpty() } ?: emptyList()
        return BountyGrade.sorted().map { grade ->
            BountyConfig(
                grade = grade,
                enabled = enabledKeys.contains(grade.key),
                chaseDream = grade.canChaseDream && chaseDreamKeys.contains(grade.key)
            )
        }
    }

    fun save(context: Context, configs: List<BountyConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = configs.filter { it.enabled }.map { it.grade.key }.joinToString(",")
        val chaseKeys = configs.filter { it.chaseDream }.map { it.grade.key }.joinToString(",")
        prefs.edit()
            .putString(KEY_ENABLED_GRADES, keys)
            .putString(KEY_CHASE_DREAM, chaseKeys)
            .apply()
    }

    fun loadPersonal(context: Context): List<BountyConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabledKeys = prefs.getString(KEY_PERSONAL_ENABLED_GRADES, "")?.split(",")
            ?.filter { it.isNotEmpty() } ?: emptyList()
        return BountyGrade.daily().map { grade ->
            BountyConfig(
                grade = grade,
                enabled = enabledKeys.contains(grade.key)
            )
        }
    }

    fun savePersonal(context: Context, configs: List<BountyConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = configs.filter { it.enabled }.map { it.grade.key }.joinToString(",")
        prefs.edit()
            .putString(KEY_PERSONAL_ENABLED_GRADES, keys)
            .apply()
    }

    fun loadNs(context: Context): List<BountyConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabledKeys = prefs.getString(KEY_NS_ENABLED_GRADES, "")?.split(",")
            ?.filter { it.isNotEmpty() } ?: emptyList()
        return BountyGrade.event().map { grade ->
            BountyConfig(
                grade = grade,
                enabled = enabledKeys.contains(grade.key)
            )
        }
    }

    fun saveNs(context: Context, configs: List<BountyConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = configs.filter { it.enabled }.map { it.grade.key }.joinToString(",")
        prefs.edit()
            .putString(KEY_NS_ENABLED_GRADES, keys)
            .apply()
    }
}

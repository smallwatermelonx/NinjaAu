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
}

package com.example.ninjaau.model

/**
 * 用户对某个悬赏等级的配置
 * @param grade     悬赏等级
 * @param enabled   用户是否勾选该等级
 * @param targetRuns 需要完成的次数（用户可修改，默认 BountyGrade.defaultRuns）
 * @param completedRuns 本轮已完成的次数
 */
data class BountyConfig(
    val grade: BountyGrade,
    val enabled: Boolean = false,
    val targetRuns: Int = grade.defaultRuns,
    val completedRuns: Int = 0,
    /** 追梦模式：跳过每日上限检查，只要等级匹配就继续准备 */
    val chaseDream: Boolean = false
) {
    /** 是否还需要继续执行该等级 */
    val isRunsRemaining: Boolean get() = completedRuns < targetRuns

    companion object {
        /** 生成所有等级的默认配置（日常悬赏默认勾选，N系列活动悬赏默认不勾选） */
        fun defaultList(): List<BountyConfig> =
            BountyGrade.sorted().map { BountyConfig(grade = it, enabled = !it.isEvent) }
    }
}

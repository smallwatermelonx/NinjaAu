package com.example.ninjaau.model

/**
 * 用户对某个悬赏等级的配置
 * @param grade     悬赏等级
 * @param enabled   用户是否勾选该等级
 * @param targetRuns 需要完成的次数（用户可修改，默认 BountyGrade.defaultRuns）
 */
data class BountyConfig(
    val grade: BountyGrade,
    val enabled: Boolean = false,
    val targetRuns: Int = grade.defaultRuns,
    /** 追梦模式：跳过每日上限检查，只要等级匹配就继续准备 */
    val chaseDream: Boolean = false
)

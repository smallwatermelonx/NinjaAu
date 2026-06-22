package com.example.ninjaau.model

/**
 * 悬赏等级定义
 * 每个等级关联：显示名、默认完成次数、建议等级、招募列表等级图标模板路径、UI排序
 * level: 游戏内建议等级，队伍房间中显示为 lv{level}
 * 模板文件统一放在 assets/templates/bounty_list/ 下
 */
enum class GradeGroup(val defaultRuns: Int) {
    A_GROUP(3),
    S_GROUP(5),
    B(4),
    C(5),
    D(5),
    SS(1),
    SS_PLUS(1),
    NSS_PLUS(1),
    NS(5),
    NA(2);

    fun members(): List<BountyGrade> = BountyGrade.entries.filter { it.group == this }

    fun totalRuns(runCounts: Map<BountyGrade, Int>): Int =
        members().sumOf { runCounts[it] ?: 0 }

    fun isComplete(runCounts: Map<BountyGrade, Int>): Boolean =
        totalRuns(runCounts) >= defaultRuns
}

enum class BountyGrade(
    val key: String,
    val displayName: String,
    val defaultRuns: Int,
    val templateName: String,
    val priority: Int,
    val level: Int,
    val group: GradeGroup,
    /** 该等级可能出现的多个级别（SS+ 有 105~130 六种） */
    val levelVariants: List<Int> = emptyList()
) {
    NSS_PLUS("nss_plus", "NSS+", 1, "nss_plus.png", 12, 125, GradeGroup.NSS_PLUS),
    NS("ns", "NS", 5, "ns.png", 13, 125, GradeGroup.NS),
    NA("na", "NA", 2, "na.png", 14, 125, GradeGroup.NA),
    SS_PLUS("ss_plus", "SS+", 1, "ss_plus.png", 0, 125, GradeGroup.SS_PLUS,
        levelVariants = listOf(105, 110, 115, 120, 125, 130)),
    SS("ss", "SS", 1, "ss.png", 1, 100, GradeGroup.SS),
    S_PLUS("s_plus", "S+", 5, "s_plus.png", 2, 90, GradeGroup.S_GROUP),
    S("s", "S", 5, "s.png", 3, 90, GradeGroup.S_GROUP),
    A_PLUS("a_plus", "A+", 3, "a_plus.png", 4, 80, GradeGroup.A_GROUP),
    A("a", "A", 3, "a.png", 5, 80, GradeGroup.A_GROUP),
    B("b", "B", 4, "b.png", 6, 60, GradeGroup.B),
    C("c", "C", 5, "c.png", 7, 40, GradeGroup.C),
    D("d", "D", 5, "d.png", 8, 30, GradeGroup.D);

    /** 等级图标在 assets 中的完整路径（聊天招募列表中的字母等级图标） */
    fun gradeIconPath() = "templates/bounty_list/${displayName}.png"

    /** 个人悬赏列表中的等级图标路径 */
    fun personalGradeIconPath() = "templates/bounty_list_personal/${displayName}.png"

    /** 队伍房间中建议等级图标的路径（lv30 / lv40 / lv60 / lv80 / lv90 / lv125 等） */
    fun levelIconPath(): String? = levelToPath(level)

    /** 所有可能的等级图标路径（含多级别变体） */
    fun levelIconPaths(): List<String> {
        val levels = if (levelVariants.isNotEmpty()) levelVariants else listOf(level)
        return levels.mapNotNull { levelToPath(it) }
    }

    private fun levelToPath(lv: Int): String? = when (lv) {
        30 -> "templates/team_room/lv30.png"
        40 -> "templates/team_room/lv40.png"
        60 -> "templates/team_room/lv60.png"
        80 -> "templates/team_room/lv80.png"
        90 -> "templates/team_room/lv90.png"
        100 -> "templates/team_room/lv100.png"
        105 -> "templates/team_room/lv105.png"
        110 -> "templates/team_room/lv110.png"
        115 -> "templates/team_room/lv115.png"
        120 -> "templates/team_room/lv120.png"
        125 -> "templates/team_room/lv125.png"
        130 -> "templates/team_room/lv130.png"
        else -> null
    }

    /** 是否为活动悬赏（N 系列），非日常悬赏 */
    val isEvent: Boolean get() = this in setOf(NSS_PLUS, NS, NA)

    /** 是否支持追梦模式（S、S+、SS+、NSS+、NS、NA） */
    val canChaseDream: Boolean get() = this in setOf(S, S_PLUS, SS_PLUS, NSS_PLUS, NS, NA)

    companion object {
        /** 按 priority 排序返回所有等级 */
        fun sorted() = entries.sortedBy { it.priority }

        /** 日常悬赏（非 N 系列） */
        fun daily() = entries.filter { !it.isEvent }.sortedBy { it.priority }

        /** 活动悬赏（N 系列） */
        fun event() = entries.filter { it.isEvent }.sortedBy { it.priority }
    }
}

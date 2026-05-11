package com.example.ninjaau.model

/**
 * 悬赏等级定义
 * 每个等级关联：显示名、默认完成次数、建议等级、招募列表等级图标模板路径、UI排序
 * level: 游戏内建议等级，队伍房间中显示为 lv{level}
 * 模板文件统一放在 assets/templates/bounty/ 下
 */
enum class BountyGrade(
    val key: String,
    val displayName: String,
    val defaultRuns: Int,
    val templateName: String,
    val priority: Int,
    val level: Int   // 建议等级，队伍房间以此数值显示
) {
    NSS_PLUS("nss_plus", "NSS+", 1, "nss_plus.png", 12, 125),
    NS("ns", "NS", 5, "ns.png", 13, 125),
    NA("na", "NA", 2, "na.png", 14, 125),
    SS_PLUS("ss_plus", "SS+", 1, "ss_plus.png", 0, 125),
    SS("ss", "SS", 1, "ss.png", 1, 100),
    S_PLUS("s_plus", "S+", 5, "s_plus.png", 2, 90),
    S("s", "S", 5, "s.png", 3, 90),
    A_PLUS("a_plus", "A+", 3, "a_plus.png", 4, 80),
    A("a", "A", 3, "a.png", 5, 80),
    B("b", "B", 4, "b.png", 6, 60),
    C("c", "C", 5, "c.png", 7, 40),
    D("d", "D", 5, "d.png", 8, 30);

    /** 等级图标在 assets 中的完整路径（聊天招募列表中的字母等级图标） */
    fun gradeIconPath() = "templates/bounty/chatbox/${displayName}.png"

    /** 队伍房间中建议等级图标的路径（lv30 / lv40 / lv60 / lv125 等） */
    fun levelIconPath(): String? = when (level) {
        30 -> "templates/bounty/preparation/lv30.png"
        40 -> "templates/bounty/preparation/lv40.png"
        60 -> "templates/bounty/preparation/lv60.png"
        125 -> "templates/bounty/preparation/lv125.png"
        else -> null
    }

    /** 是否为活动悬赏（N 系列），非日常悬赏 */
    val isEvent: Boolean get() = this in setOf(NSS_PLUS, NS, NA)

    companion object {
        /** 按 priority 排序返回所有等级 */
        fun sorted() = entries.sortedBy { it.priority }

        /** 日常悬赏（非 N 系列） */
        fun daily() = entries.filter { !it.isEvent }.sortedBy { it.priority }

        /** 活动悬赏（N 系列） */
        fun event() = entries.filter { it.isEvent }.sortedBy { it.priority }
    }
}

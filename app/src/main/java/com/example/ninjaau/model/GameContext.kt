package com.example.ninjaau.model

/**
 * 运行时上下文 — 贯穿一次脚本执行的生命周期
 */
data class GameContext(
    /** 当前执行的阶段 */
    var currentPhase: GamePhase = GamePhase.IDLE,
    /** 用户勾选且未完成次数的等级列表（按优先级排序） */
    var activeGrades: List<BountyGrade> = emptyList(),
    /** 各等级已完成的次数 */
    val runCounts: MutableMap<BountyGrade, Int> = mutableMapOf(),
    /** 各等级用户设定的目标次数（来自 BountyConfig.targetRuns） */
    val targetRuns: Map<BountyGrade, Int> = emptyMap(),
    /** 当前正在处理的悬赏等级 */
    var currentBounty: BountyGrade? = null,
    /** 总轮次计数 */
    var totalCycles: Int = 0
) {
    /** 是否所有勾选的悬赏都已完成 */
    val allCompleted: Boolean
        get() = activeGrades.isEmpty() || activeGrades.all { (runCounts[it] ?: 0) >= (targetRuns[it] ?: it.defaultRuns) }
}

enum class GamePhase {
    IDLE,
    /** 大厅 — templates/lobby/ */
    LOBBY,
    /** 聊天界面 — templates/chat/ */
    CHAT,
    /** 招募列表 — templates/recruit_list/ */
    RECRUIT_LIST,
    /** 队伍房间 — templates/team_room/ */
    TEAM_ROOM,
    /** 战斗中 — templates/fight/ */
    FIGHT,
    /** 结算领奖 — templates/settlement/ */
    SETTLEMENT,
    /** 异常恢复 */
    RECOVERY,
    DONE
}

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
    /** 当前正在处理的悬赏等级 */
    var currentBounty: BountyGrade? = null,
    /** 总轮次计数 */
    var totalCycles: Int = 0
) {
    /** 是否所有勾选的悬赏都已完成 */
    val allCompleted: Boolean
        get() = activeGrades.isEmpty() || activeGrades.all { (runCounts[it] ?: 0) >= it.defaultRuns }
}

enum class GamePhase {
    IDLE,
    /** 大厅 → 聊天 → 招募tab → 扫描 */
    SCANNING,
    /** 匹配到等级 → 加入队伍 */
    JOINING,
    /** 入队校验 → 准备 */
    VALIDATING,
    /** 准备后等待倒计时 */
    WAITING,
    /** 战斗中 */
    BATTLE,
    /** 结算领奖 */
    SETTLEMENT,
    /** 异常恢复 */
    RECOVERY,
    DONE
}

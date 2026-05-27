package com.example.ninjaau.model

/**
 * 运行时上下文 — 贯穿一次脚本执行的生命周期
 */
data class GameContext(
    /** 当前执行的阶段 */
    var currentPhase: GamePhase = GamePhase.IDLE,
    /** 用户勾选且未完成次数的等级列表（按优先级排序） */
    var activeGrades: List<BountyGrade> = emptyList(),
    /** 用户勾选的全部等级（含已完成，用于进度展示） */
    val totalGrades: List<BountyGrade> = emptyList(),
    /** 各等级已完成的次数 */
    val runCounts: MutableMap<BountyGrade, Int> = mutableMapOf(),
    /** 各等级用户设定的目标次数（来自 BountyConfig.targetRuns） */
    val targetRuns: Map<BountyGrade, Int> = emptyMap(),
    /** 当前正在处理的悬赏等级（由 RecruitListNode 根据等级图标设定） */
    var currentBounty: BountyGrade? = null,
    /** 队伍房间中实际检测到的等级（由 BountyDetailNode 根据Lv图标设定，比 currentBounty 准确） */
    var actualGrade: BountyGrade? = null,
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
    /** 招募邀请弹窗 */
    RECRUIT_INVITE,
    /** 悬赏详情 — templates/team_room/ */
    BOUNTY_DETAIL,
    /** 战斗加载界面 — templates/battle_loading/ */
    BATTLE_LOADING,
    /** 战斗中 — templates/fight/ */
    FIGHT,
    /** 失败结算 */
    DEFEAT,
    /** 结算领奖 — templates/settlement/ */
    SETTLEMENT,
    /** 异常恢复 */
    RECOVERY,
    DONE
}

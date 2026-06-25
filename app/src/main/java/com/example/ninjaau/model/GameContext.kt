package com.example.ninjaau.model

enum class BusinessLine { DAILY, PERSONAL }

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
    /** 开启追梦模式的等级集合（跳过每日上限检查） */
    val chaseDreamGrades: Set<BountyGrade> = emptySet(),
    /** 总轮次计数 */
    var totalCycles: Int = 0,
    // ═══ 业务线控制 ═══
    /** 当前业务线 */
    var businessLine: BusinessLine = BusinessLine.DAILY,
    /** 日常悬赏是否有勾选等级 */
    var dailyEnabled: Boolean = false,
    /** 逆袭悬赏是否启用 */
    var nsEnabled: Boolean = false,
    // ═══ 个人悬赏相关 ═══
    /** 个人悬赏是否启用 */
    var personalBountyEnabled: Boolean = false,
    /** 个人悬赏是否全部完成 */
    var personalBountyCompleted: Boolean = false,
    /** 个人悬赏用户勾选的等级列表 */
    var personalActiveGrades: List<BountyGrade> = emptyList(),
    /** 个人悬赏各等级目标次数 */
    val personalTargetRuns: Map<BountyGrade, Int> = emptyMap(),
    /** 逆袭悬赏用户勾选的等级列表（从 dailyEnabled configs 中筛选 isEvent） */
    var nsActiveGrades: List<BountyGrade> = emptyList(),
    /** 恢复节点连续尝试次数（进入 RECOVERY 时递增，正常前进时重置） */
    var recoveryAttempt: Int = 0,
    /** 当前轮次开始时间（LobbyNode 进入时记录，SettlementNode 领奖后输出耗时） */
    var roundStartTime: Long = 0L
)

enum class GamePhase {
    IDLE,
    /** 大厅 — templates/lobby/ */
    LOBBY,
    /** 招募列表 — templates/bounty_list/ */
    RECRUIT_LIST,
    /** 悬赏详情 — templates/team_room/ */
    BOUNTY_DETAIL,
    /** 战斗加载界面 — templates/battle_loading/ */
    BATTLE_LOADING,
    /** 战斗中 — templates/fight/ */
    FIGHT,
    /** 结算领奖 — templates/settlement/ */
    SETTLEMENT,
    /** 战斗失败 — templates/defeat/ */
    DEFEAT,
    /** 异常恢复 */
    RECOVERY,
    DONE,
    // ═══ 个人悬赏 ═══
    /** 个人悬赏中心/列表 */
    PERSONAL_BOUNTY_CENTER,
    /** 个人悬赏详情 */
    PERSONAL_BOUNTY_DETAIL
}

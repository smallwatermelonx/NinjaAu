package com.example.ninjaau.model

/**
 * 游戏界面状态枚举（仅描述界面，不包含模板配置）
 * 模板配置由 SceneDetector 或各 Scanner 管理
 */
enum class ScreenState(val description: String) {
    // === 大厅 ===
    LOBBY("游戏大厅"),

    // === 聊天/招募 ===
    CHAT_ICON("聊天图标"),
    RECRUIT_TAB("组队招募页签"),
    RECRUIT_LIST("招募列表"),

    // === 入队 ===
    JOIN_BUTTON("加入队伍按钮"),
    TEAM_ROOM("队伍房间"),
    TEAM_COMPLETED("已完成标记"),
    TEAM_FULL("队伍已满"),
    READY_BUTTON("准备按钮"),
    WAITING_SCREEN("等待倒计时"),
    EXIT_CONFIRM("退出确认弹窗"),

    // === 战斗 ===
    BATTLE_WARNING("战斗WARNING"),
    BATTLE_ACTIVE("战斗中"),
    BOSS_HP_BAR("Boss血条"),
    COUNTDOWN("倒计时"),
    ULTIMATE_SKILL("大招图标"),
    WEAPON_SKILL("武器图标"),

    // === 结算 ===
    SETTLEMENT_POPUP("结算弹窗"),
    CONFIRM_BUTTON("确定按钮"),

    // === 通用 ===
    BACK_BUTTON("返回按钮"),
    UNKNOWN("未知界面");

    companion object {
        /** 战斗期间持续检测的界面列表 */
        val battleStates = setOf(BATTLE_ACTIVE, ULTIMATE_SKILL)
    }
}

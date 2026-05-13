package com.example.ninjaau.model

/**
 * 游戏界面状态枚举（仅描述界面，不包含模板配置）
 */
enum class ScreenState(val description: String) {
    // === 大厅 ===
    LOBBY("游戏大厅"),

    // === 聊天/招募 ===
    CHAT_ICON("聊天图标"),
    RECRUIT_TAB("组队招募页签"),
    RECRUIT_LIST("招募列表"),
    RECRUIT_EXCEPTION("招募列表异常"),

    // === 入队 ===
    JOIN_BUTTON("加入队伍按钮"),
    TEAM_ROOM("队伍房间"),
    TEAM_COMPLETED("已完成标记"),
    TEAM_FULL("队伍已满"),
    READY_BUTTON("准备按钮"),
    WAITING_SCREEN("等待倒计时"),
    EXIT_CONFIRM("退出确认弹窗"),
    DAILY_LIMIT("今日已达上限"),

    // === 战斗 ===
    BATTLE_WARNING("战斗WARNING"),
    BATTLE_ACTIVE("战斗中"),
    ULTIMATE_SKILL("大招图标"),
    WEAPON_SKILL("武器图标"),
    DEFEAT_POPUP("失败弹窗"),

    // === 结算 ===
    SETTLEMENT_POPUP("结算弹窗"),
    CONFIRM_BUTTON("确定按钮"),

    // === 通用 ===
    BACK_BUTTON("返回按钮"),
    /** 私聊页签（用于招募列表TAB刷新） */
    CHAT_TAB("私聊页签"),
    UNKNOWN("未知界面");
}

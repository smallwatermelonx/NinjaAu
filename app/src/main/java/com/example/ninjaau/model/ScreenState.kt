package com.example.ninjaau.model

/**
 * 游戏界面状态枚举（每个枚举值必须有对应的静态模板资源）
 */
enum class ScreenState(val description: String) {
    // === 聊天/招募 ===
    CHAT_ICON("聊天图标"),
    RECRUIT_TAB("组队招募页签"),
    RECRUIT_TAB_BLACK("组队招募页签-黑色字体"),
    OUT_OF_RANGE_RECRUIT("超出范围的悬赏"),
    /** 列表过期时的"悬赏令组队的邀请"标识 */
    RECRUIT_INVITE("招募邀请标识"),

    // === 入队 ===
    READY_BUTTON("准备按钮"),
    EXIT_CONFIRM("退出确认弹窗"),
    DAILY_LIMIT("今日已达上限"),

    // === 战斗加载 ===
    BATTLE_LOADING("战斗加载中"),
    // === 战斗 ===
    WARNING("WARNING"),
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

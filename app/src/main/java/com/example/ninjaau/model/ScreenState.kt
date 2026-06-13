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
    /** 招募列表页面专属标识（区别于 RECRUIT_TAB 页签） */
    RECRUIT_LIST_SCREEN("招募列表页面"),
    /** 列表过期时的"悬赏令组队的邀请"标识 */
    RECRUIT_INVITE("招募邀请标识"),

    // === 入队 ===
    READY_BUTTON("准备按钮"),
    EXIT_CONFIRM("退出确认按钮"),
    DAILY_LIMIT("今日已达上限"),

    // === 战斗加载 ===
    BATTLE_LOADING("战斗加载中"),
    // === 战斗 ===
    WARNING("WARNING"),
    SLIDE_BUTTON("下滑按钮"),
    LV_ICON("Lv图标"),
    JUMP_BUTTON("跳跃按钮"),
    SCROLL_UP("上翻按钮"),
    ULTIMATE_SKILL("大招图标"),
    WEAPON_SKILL("武器图标"),
    BLOOD_CURSE("血咒技能"),
    DEFEAT_POPUP("失败弹窗"),

    // === 结算 ===
    SETTLEMENT_POPUP("结算弹窗"),
    CONFIRM_BUTTON("确定按钮"),

    // === 通用 ===
    BACK_BUTTON("返回按钮"),
    /** 私聊页签（用于招募列表TAB刷新） */
    CHAT_TAB("私聊页签"),
    // === 组队邀请弹窗 ===
    /** 组队邀请弹窗整体标识（任意节点都可能弹出） */
    TEAM_INVITATION("组队邀请弹窗"),
    /** 组队邀请 — 拒绝按钮 */
    INVITE_REJECT("邀请拒绝按钮"),
    /** 组队邀请 — 勾选框 */
    INVITE_CHECKBOX("邀请勾选框"),
    /** 组队邀请 — 同意按钮 */
    INVITE_AGREE("邀请同意按钮"),

    UNKNOWN("未知界面"),

    // === 个人悬赏 ===
    /** 大厅中的个人悬赏入口图标 */
    PERSONAL_BOUNTY_ENTRY("个人悬赏入口"),
    /** 个人悬赏列表页面标识 */
    PERSONAL_BOUNTY_LIST_SCREEN("个人悬赏列表页面"),
    /** 个人悬赏详情页面标识 */
    PERSONAL_BOUNTY_DETAIL_SCREEN("个人悬赏详情页面"),
    /** 个人悬赏详情 — 发送消息按钮 */
    PERSONAL_BOUNTY_SEND_MSG("发送消息按钮"),
    /** 个人悬赏详情 — 出发按钮 */
    PERSONAL_BOUNTY_GO("出发按钮");
}

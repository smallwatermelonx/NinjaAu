package com.example.ninjaau.model

import com.example.ninjaau.core.uimap.TemplateConfig

enum class ScreenState(
    val templateConfig: TemplateConfig,
    val description: String
) {
    HALL_CHAT(TemplateConfig("hall_chat", "hall", 0.8f), "大厅Chat图标"),
    RECRUIT_TAB(TemplateConfig("team_recruit", "chat", 0.8f), "招募页签"),
    JOIN_TEAM(TemplateConfig("join_team", "bounty/chatbox", 0.8f), "加入队伍"),
    READY_BTN(TemplateConfig("prepare", "bounty/preparation", 0.8f), "准备按钮"),
    SLIDE_BUTTON(TemplateConfig("decline", "fight", 0.8f), "下滑按钮"),
    BATTLE_WARNING(TemplateConfig("warning", "fight", 0.7f), "战斗WARNING"),
    BATTLE_TARGET(TemplateConfig("ninjutsu", "fight", 0.6f), "战斗目标"),
    REWARD_POPUP(TemplateConfig("blank_space", "bounty/settlement", 0.8f), "奖励弹窗"),
    CONFIRM_BTN(TemplateConfig("confirm", "bounty/settlement", 0.8f), "确定按钮"),
    BACK_BUTTON(TemplateConfig("backward", "other", 0.8f), "返回按钮"),
    UNKNOWN(TemplateConfig("", "", 0f), "未知界面")
}

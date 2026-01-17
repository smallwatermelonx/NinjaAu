package com.example.ninjaau.core.uimap

import android.content.Context

/**
 * 增强版模板配置：支持多级模块路径（比如 "chatbox/bounty"），兼容原有单层级
 */
data class TemplateConfig(
    val templateName: String,      // 模板文件名（不带后缀）
    val module: String,            // 所属模块（支持多级：如 "login" / "chatbox/bounty" / "chat/chatbooks"）
    val threshold: Float = 0.8f,   // 匹配阈值
    val isFromAssets: Boolean = true
) {
    // 获取带后缀的文件名
    fun getFileName() = "$templateName.png"

    // 增强版路径：支持多级module（比如 module="chatbox/bounty" → templates/chatbox/bounty/join_team.png）
    fun getAssetsPath(): String = "templates/$module/${getFileName()}"
}

object UIMap {
    object Login {
        // 原有单层级配置：不受影响
        val TAP_TO_START = TemplateConfig("tap_to_start", "login", 0.75f)
        val ACCOUNT_ICON = TemplateConfig("user_info", "login", 0.8f)
    }

    object Hall {
        // 原有单层级配置：不受影响
        val HALL_MAIN = TemplateConfig("hall_main", "hall", 0.8f)
        val HALL_CHAT = TemplateConfig("hall_chat", "hall", 0.8f)
    }

    object Chat {
        // 原有单层级配置：不受影响
        val CHAT_BAR = TemplateConfig("chat_bar", "chat", 0.8f)
        val TEAM_RECRUIT_TAB = TemplateConfig("team_recruit", "chat", 0.8f)
    }

    object Bounty {
        val JOIN_TEAM_BUTTON = TemplateConfig("join_team", "bounty/chatbox", 0.8f)
    }

    object Team {
        // 原有配置（如果 prepare 下有子层级，也可以改成多级：比如 "prepare/team"）
        val READY_BUTTON = TemplateConfig("prepare", "bounty/preparation", 0.8f)
    }

    // 新增：战斗相关模板
    object Battle {
        // WARNING界面模板（对应第一张图的WARNING区域）
        val WARNING_SCREEN = TemplateConfig("warning", "fight", 0.7f)
        // 战斗中目标图标（对应第二张图的紫色图标） todo: 此处需要优化考虑，暂时给到0.7=6的匹配阈值
        val NINJUTSU = TemplateConfig("ninjutsu", "fight", 0.6f)

        val SLIDE_BUTTON = TemplateConfig("decline", "fight", 0.8f) // 新增下滑按钮配置
    }

    // 新增：奖励弹窗相关模板
    object Reward {
        // 奖励弹窗模板（比如“恭喜获得”区域）
        val CLOSE_BLANK_TEXT = TemplateConfig("blank_space", "bounty/settlement", 0.8f)
        // 确定按钮模板
        val CONFIRM_BUTTON = TemplateConfig("confirm", "bounty/settlement", 0.8f)
    }
}
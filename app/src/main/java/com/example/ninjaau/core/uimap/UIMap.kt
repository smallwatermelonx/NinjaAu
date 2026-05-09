package com.example.ninjaau.core.uimap

data class TemplateConfig(
    val templateName: String,
    val module: String,
    val threshold: Float = 0.8f,
    val isFromAssets: Boolean = true
) {
    fun getFileName() = "$templateName.png"
    fun getAssetsPath(): String = "templates/$module/${getFileName()}"
}

object UIMap {
    object Hall {
        val HALL_CHAT = TemplateConfig("hall_chat", "hall", 0.8f)
    }

    object Chat {
        val TEAM_RECRUIT_TAB = TemplateConfig("team_recruit", "chat", 0.8f)
    }

    object Bounty {
        val JOIN_TEAM_BUTTON = TemplateConfig("join_team", "bounty/chatbox", 0.8f)
    }

    object Team {
        val READY_BUTTON = TemplateConfig("prepare", "bounty/preparation", 0.8f)
    }

    object Battle {
        val WARNING_SCREEN = TemplateConfig("warning", "fight", 0.7f)
        val NINJUTSU = TemplateConfig("ninjutsu", "fight", 0.6f)
        val SLIDE_BUTTON = TemplateConfig("decline", "fight", 0.8f)
    }

    object Reward {
        val CLOSE_BLANK_TEXT = TemplateConfig("blank_space", "bounty/settlement", 0.8f)
        val CONFIRM_BUTTON = TemplateConfig("confirm", "bounty/settlement", 0.8f)
    }

    object Other {
        val BACKWARD = TemplateConfig("backward", "other", 0.8f)
    }
}

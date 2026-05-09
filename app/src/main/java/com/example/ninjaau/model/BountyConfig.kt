package com.example.ninjaau.model

data class BountyConfig(
    val id: String,
    val name: String,
    val gradeIcon: String,
    val enabled: Boolean = false
) {
    companion object {
        fun presetList(): List<BountyConfig> = listOf(
            BountyConfig("ss_plus", "SS+ 悬赏", "bounty/chatbox/SS+"),
            BountyConfig("s_plus", "S+ 悬赏", "bounty/chatbox/S+"),
            BountyConfig("s", "S 悬赏", "bounty/chatbox/S"),
            BountyConfig("a", "A 悬赏", "bounty/chatbox/A"),
            BountyConfig("b", "B 悬赏", "bounty/chatbox/B"),
            BountyConfig("c", "C 悬赏", "bounty/chatbox/C"),
            BountyConfig("d", "D 悬赏", "bounty/chatbox/D"),
        )
    }
}

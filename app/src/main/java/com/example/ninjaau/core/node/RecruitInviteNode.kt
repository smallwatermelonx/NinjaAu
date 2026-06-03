package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState

/**
 * 招募邀请节点 — 处理悬赏邀请弹窗。
 *
 * 对应页面：招募列表过期时出现的"悬赏令组队的邀请"标识弹窗。
 *
 * TODO: 后续实现具体逻辑
 */
class RecruitInviteNode(private val ctx: NodeContext) : GameNode {

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("招募邀请 Phase（暂未实现）")
        // TODO: 实现悬赏邀请处理逻辑
        return GamePhase.RECRUIT_LIST
    }
}

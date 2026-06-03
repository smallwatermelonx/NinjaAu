package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState

/**
 * 失败节点 — 处理战斗失败后的流程。
 *
 * 对应页面：战斗失败弹窗。
 *
 * TODO: 后续实现具体逻辑
 */
class DefeatNode(private val ctx: NodeContext) : GameNode {

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("失败结算 Phase（暂未实现）")
        // TODO: 实现失败处理逻辑
        return GamePhase.LOBBY
    }
}

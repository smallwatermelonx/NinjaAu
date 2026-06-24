package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * 个人悬赏中心节点 — 在个人悬赏列表中选择等级。
 *
 * 流程：
 * 1. 列表 → 识别 bounty_shop.png → 扫描用户选择的等级图标 → 点击进入详情
 *
 * 入口：PERSONAL_BOUNTY_CENTER phase（由 LobbyNode 点击入口后进入）
 * 出口：PERSONAL_BOUNTY_DETAIL
 */
class PersonalBountyCenterNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 300L
        /** 连续无匹配超过此时长才触发 BACK 兜底 */
        private const val BACK_BUTTON_TIMEOUT_MS = 15_000L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("个人悬赏中心 Phase")
        var lastMatchMs = System.currentTimeMillis()
        var noMatchCount = 0

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                var matched = false

                // ═══ 个人悬赏列表页面（已进入列表） ═══
                val listScreen = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_LIST_SCREEN)
                if (listScreen != null) {
                    lastMatchMs = System.currentTimeMillis()
                    noMatchCount = 0
                    matched = true

                    val gradesToFind = ctx.activeGrades
                    if (gradesToFind.isEmpty()) {
                        this.ctx.log("无待执行的个人悬赏等级")
                        return GamePhase.DONE
                    }

                    val match = this.ctx.detector.matchAnyPersonalGradeIcon(screen, gradesToFind)
                    if (match != null) {
                        val grade = match.grade
                        val coord = Pair(match.centerX, match.centerY)
                        ctx.currentBounty = grade
                        this.ctx.click(coord)
                        this.ctx.log("点击个人悬赏: ${grade.displayName}")
                        return GamePhase.PERSONAL_BOUNTY_DETAIL
                    }

                    this.ctx.log("未找到目标等级图标，等待刷新")
                }

                // ═══ 无匹配计数 ═══
                if (!matched) {
                    noMatchCount++
                }

                // ═══ 5. 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
            } finally {
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }
        return null
    }
}

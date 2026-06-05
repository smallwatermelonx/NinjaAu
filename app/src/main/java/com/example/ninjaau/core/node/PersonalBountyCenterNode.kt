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
 * 个人悬赏中心节点 — 从大厅进入个人悬赏列表并选择等级。
 *
 * 流程：
 * 1. 大厅 → 识别 private_bounty.png → 点击进入个人悬赏列表
 * 2. 列表 → 识别 bounty_shop.png → 扫描用户选择的等级图标 → 点击进入详情
 *
 * 入口：PERSONAL_BOUNTY_CENTER phase
 * 出口：PERSONAL_BOUNTY_DETAIL
 */
class PersonalBountyCenterNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 1000L
        /** 连续无匹配超过此时长才触发 BACK 兜底 */
        private const val BACK_BUTTON_TIMEOUT_MS = 15_000L
    }

    private var inList = false

    override suspend fun execute(ctx: GameContext): GamePhase? {
        inList = false
        this.ctx.log("个人悬赏中心 Phase")
        var lastMatchMs = System.currentTimeMillis()
        var noMatchCount = 0

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            try {
                var matched = false

                // ═══ 1. 弹窗关闭（最高优先级） ═══
                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON)
                if (confirmCoord != null) {
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(800)
                    lastMatchMs = System.currentTimeMillis()
                    matched = true
                }

                // ═══ 2. 个人悬赏列表页面（已进入列表） ═══
                if (!matched) {
                    val listScreen = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_LIST_SCREEN)
                    if (listScreen != null) {
                        inList = true
                        lastMatchMs = System.currentTimeMillis()
                        noMatchCount = 0
                        matched = true

                        val gradesToFind = ctx.activeGrades
                        if (gradesToFind.isEmpty()) {
                            this.ctx.log("无待执行的个人悬赏等级")
                            return GamePhase.DONE
                        }

                        val match = this.ctx.detector.matchAnyGradeIcon(screen, gradesToFind)
                        if (match != null) {
                            val grade = match.grade
                            val coord = Pair(match.centerX, match.centerY)
                            ctx.currentBounty = grade
                            this.ctx.click(coord)
                            this.ctx.log("点击个人悬赏: ${grade.displayName}")
                            this.ctx.delay(1000)
                            return GamePhase.PERSONAL_BOUNTY_DETAIL
                        }

                        this.ctx.log("未找到目标等级图标，等待刷新")
                    }
                }

                // ═══ 3. 大厅中的个人悬赏入口 ═══
                if (!matched) {
                    val entryCoord = this.ctx.detector.matchTemplate(screen, ScreenState.PERSONAL_BOUNTY_ENTRY)
                    if (entryCoord != null) {
                        this.ctx.click(entryCoord)
                        this.ctx.log("点击个人悬赏入口")
                        this.ctx.delay(1500)
                        lastMatchMs = System.currentTimeMillis()
                        noMatchCount = 0
                        matched = true
                    }
                }

                // ═══ 4. 大厅聊天图标（仅确认位置，不点击） ═══
                if (!matched) {
                    val chatIcon = this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON)
                    if (chatIcon != null) {
                        lastMatchMs = System.currentTimeMillis()
                        matched = true
                    }
                }

                // ═══ 5. 无匹配计数 ═══
                if (!matched) {
                    noMatchCount++
                }

                // ═══ 6. BACK 兜底：仅在长时间无匹配时触发（防止误退） ═══
                if (!matched && noMatchCount > 0) {
                    val stuckMs = System.currentTimeMillis() - lastMatchMs
                    if (stuckMs > BACK_BUTTON_TIMEOUT_MS) {
                        val backCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                        if (backCoord != null) {
                            this.ctx.click(backCoord)
                            this.ctx.delay(800)
                            if (inList) inList = false
                            lastMatchMs = System.currentTimeMillis()
                            noMatchCount = 0
                            continue
                        }
                    }
                }

                // ═══ 7. 超时检测 ═══
                checkNodeTimeout(lastMatchMs)
            } finally {
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }
        return null
    }
}

package com.example.ninjaau.core.node

import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.checkNodeTimeout
import com.example.ninjaau.model.BusinessLine
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import org.opencv.core.Mat
import kotlin.coroutines.coroutineContext

/**
 * 结算节点 — 领取奖励并回到大厅。
 *
 * 职责：
 * - 点击结算弹窗、确认按钮
 * - 更新 runCounts 和 activeGrades
 * - 判断全部完成 → DONE
 * - 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class SettlementNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val NORMAL_INTERVAL_MS = 500L
        private const val POST_CLICK_DELAY = 500L
    }

    /**
     * 结算领奖循环。
     *
     * 结算弹窗 → 点击关闭
     * 确认按钮 → 点击领奖
     * 回到大厅 → 更新完成计数并退出
     * 无匹配 → checkNodeTimeout 超时检测
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        val grade = ctx.actualGrade ?: ctx.currentBounty
        this.ctx.log("结算 Phase，悬赏=${grade?.displayName}")
        var lastMatchMs = System.currentTimeMillis()
        var confirmClicked = false

        while (coroutineContext.isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(NORMAL_INTERVAL_MS); continue }
            var screenMat: Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // 裁剪下方1/5中间1/3，检测结算弹窗和确认按钮
                val bottomMiddle = this.ctx.detector.cropBottomMiddleFifth(screenMat)
                try {
                    val x = screenMat.cols() / 3f
                    val y = screenMat.rows() * 4f / 5f

                    // 先检测结算弹窗（点空白处关闭）
                    val settlementCoord = this.ctx.detector.matchTemplateMat(bottomMiddle, ScreenState.SETTLEMENT_POPUP)
                    if (settlementCoord != null) {
                        this.ctx.delay(500)
                        this.ctx.click(Pair(settlementCoord.first + x, settlementCoord.second + y))
                        this.ctx.log("点击空白处关闭结算弹窗")
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(500)
                        continue
                    }

                    // 再检测确认按钮（点击领奖）
                    val confirmCoord = this.ctx.detector.matchTemplateMat(bottomMiddle, ScreenState.CONFIRM_BUTTON)
                    if (confirmCoord != null) {
                        this.ctx.delay(500)
                        this.ctx.click(Pair(confirmCoord.first + x, confirmCoord.second + y))
                        this.ctx.log("点击确认领奖")
                        confirmClicked = true
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(POST_CLICK_DELAY)
                        continue
                    }
                } finally {
                    bottomMiddle.release()
                }

                // 确认按钮点击后不再检测到 → 回到大厅
                if (confirmClicked) {
                    this.ctx.log("确认按钮消失，回到大厅")
                    break
                }

                checkNodeTimeout(lastMatchMs)
            } finally {
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(NORMAL_INTERVAL_MS)
        }

        // 更新完成计数（仅递增实际完成的等级）
        if (grade != null) {
            val count = ctx.runCounts[grade] ?: 0
            ctx.runCounts[grade] = count + 1
            ctx.totalCycles++

            // 持久化进度
            com.example.ninjaau.core.config.ScriptConfigRepository.saveRunCounts(ctx.runCounts)

            val group = grade.group
            val groupTotal = group.totalRuns(ctx.runCounts)
            this.ctx.log("${grade.displayName} 完成 ${groupTotal}/${group.defaultRuns}")

            if (group.isComplete(ctx.runCounts)) {
                ctx.activeGrades = ctx.activeGrades.filter { it.group != group }
                this.ctx.log("${group.name} 全部完成，从集合移除")
            }
        }
        ctx.currentBounty = null
        ctx.actualGrade = null

        if (ctx.activeGrades.isEmpty()) {
            // ═══ 日常完成 → 切换到个人悬赏 ═══
            if (ctx.businessLine == BusinessLine.DAILY && ctx.personalBountyEnabled) {
                this.ctx.log("日常悬赏全部完成，准备切换到个人悬赏")
                ctx.businessLine = BusinessLine.PERSONAL
                ctx.activeGrades = ctx.personalActiveGrades
                ctx.totalCycles = 0
                return GamePhase.PERSONAL_BOUNTY_CENTER
            }
            this.ctx.log("所有悬赏已完成！")
            return GamePhase.DONE
        }

        // ═══ 个人悬赏结算后回到个人悬赏中心 ═══
        if (ctx.businessLine == BusinessLine.PERSONAL) {
            return GamePhase.PERSONAL_BOUNTY_CENTER
        }
        return GamePhase.IDLE
    }
}

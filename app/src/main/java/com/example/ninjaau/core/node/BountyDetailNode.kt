package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 悬赏详情节点 — 队伍房间内的所有操作。
 *
 * 对应页面：悬赏详情/队伍房间（显示队伍成员、准备按钮、等待中界面）。
 *
 * 职责：
 * - 等级校验（检查队伍级别是否符合勾选范围）
 * - 点击准备按钮
 * - 等待战斗开始（30s 超时）
 * - 退出队伍（点击返回→确认→回到大厅，含确认弹窗检测）
 * - 已达上限 / 加入失败 / 速通结算 等异常处理
 */
class BountyDetailNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val POST_CLICK_DELAY = 1000L
        private const val WAIT_BATTLE_TIMEOUT_MS = 30_000L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val coord = ctx.detector.matchTemplate(screen, ScreenState.READY_BUTTON)
        return RecognizeResult(coord != null, coord)
    }

    /**
     * 悬赏详情循环：
     *
     * ① 退出确认弹窗（点击确认）
     * ② 已达上限 → exitTeam，回到大厅
     * ③ 招募列表检测 → 加入失败，返回 RECRUIT_LIST
     * ④ 战斗开始（BATTLE_WARNING）
     * ⑤ 准备按钮 + 等级校验（点击准备，启动战斗等待计时）
     * ⑥ 战斗等待中：
     *    - BATTLE_WARNING → 点击进入战斗
     *    - SETTLEMENT_POPUP / CONFIRM_BUTTON → 速通结算
     *    - 超时 → exitTeam，回到大厅
     * ⑦ 返回按钮 + 退出确认 → exitTeam
     * ⑧ 回到大厅（CHAT_ICON / RECRUIT_TAB）
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        val targetGrade = ctx.currentBounty ?: return GamePhase.LOBBY
        this.ctx.log("悬赏详情 Phase，目标=${targetGrade.displayName}(lv${targetGrade.level})")

        var battleWaitStart = 0L  // 点击准备后的战斗等待计时

        while (coroutineContext.isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(POST_CLICK_DELAY); continue }
            try {
                // ═══ ② 已达上限 ═══
                if (this.ctx.detector.matchTemplate(screen, ScreenState.DAILY_LIMIT) != null) {
                    this.ctx.log("已达上限，退出队伍")
                    exitTeam()
                    ctx.currentBounty = null
                    return GamePhase.LOBBY
                }

                // ═══ ④ 准备按钮 + 等级校验 ═══
                // 仅在未点击准备前检测
                if (battleWaitStart == 0L) {
                    val readyCoord = this.ctx.detector.matchTemplate(screen, ScreenState.READY_BUTTON)
                    if (readyCoord != null) {
                        val levelMatch = this.ctx.detector.matchAnyLevelIcon(screen, ctx.activeGrades)
                        if (levelMatch == null) {
                            this.ctx.log("队伍级别不在勾选范围内，退出")
                            exitTeam()
                            ctx.currentBounty = null
                            continue
                        }
                        val (actualGrade, _) = levelMatch
                        this.ctx.log("等级匹配 ${actualGrade.displayName} (lv${actualGrade.level})，点击准备")
                        this.ctx.click(readyCoord)
                        this.ctx.delay(POST_CLICK_DELAY)
                        battleWaitStart = System.currentTimeMillis()
                        this.ctx.log("战斗等待计时启动")
                        continue
                    }
                }

                // ═══ ⑤ 战斗等待中（已点击准备） ═══
                if (battleWaitStart > 0) {
                    // 战斗加载界面
                    val loadingCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BATTLE_LOADING)
                    if (loadingCoord != null) {
                        this.ctx.log("检测到战斗加载界面，切换至加载节点")
                        return GamePhase.BATTLE_LOADING
                    }

                    // 超时检测
                    val elapsed = System.currentTimeMillis() - battleWaitStart
                    if (elapsed >= WAIT_BATTLE_TIMEOUT_MS) {
                        this.ctx.log("等待战斗超时 ${WAIT_BATTLE_TIMEOUT_MS}ms")
                        exitTeam()
                        ctx.currentBounty = null
                        return GamePhase.LOBBY
                    }
                }

                // ═══ ⑦ 回到大厅（各种方式退出后） ═══
                if (this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null) {
                    this.ctx.log("已回到大厅")
                    ctx.currentBounty = null
                    return GamePhase.LOBBY
                }

            } finally {
                screen.recycle()
            }
        }
        return GamePhase.DONE
    }

    /**
     * 退出队伍 — 从队伍房间点击返回→确认→回到大厅。
     * 只在此节点内部调用，不对外暴露。
     */
    private suspend fun exitTeam() {
        this.ctx.log("退出队伍...")
        var confirmAttempts = 0
        repeat(10) {
            val screen = this.ctx.captureBitmap() ?: return@repeat this.ctx.delay(500)
            try {
                // 已回到大厅
                if (this.ctx.detector.matchTemplate(screen, ScreenState.CHAT_ICON) != null ||
                    this.ctx.detector.matchTemplate(screen, ScreenState.RECRUIT_TAB) != null
                ) {
                    this.ctx.log("已回到大厅")
                    return
                }

                // 退出确认弹窗
                val confirmCoord = this.ctx.detector.matchTemplate(screen, ScreenState.EXIT_CONFIRM)
                if (confirmCoord != null) {
                    this.ctx.click(confirmCoord)
                    this.ctx.delay(1000)
                    confirmAttempts++
                    if (confirmAttempts > 3) {
                        this.ctx.clickOutside(null)
                        this.ctx.delay(800)
                    }
                    return@repeat
                }

                // 返回按钮
                val backCoord = this.ctx.detector.matchTemplate(screen, ScreenState.BACK_BUTTON)
                if (backCoord != null) {
                    this.ctx.click(backCoord)
                    this.ctx.delay(1200)
                    return@repeat
                }

                // 兜底空白点击
                this.ctx.clickOutside(null)
                this.ctx.delay(800)
            } finally {
                screen.recycle()
            }
        }
    }
}

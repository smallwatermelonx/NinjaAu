package com.example.ninjaau.core.node

import android.graphics.Bitmap
import com.example.ninjaau.core.GameNode
import com.example.ninjaau.core.NodeContext
import com.example.ninjaau.core.RecognizeResult
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * 战斗节点 — 执行战斗循环直到结束。
 *
 * 职责：
 * - 检测结算/胜利/失败弹窗
 * - 自动释放技能（大招 + 武器技能，500ms 检测间隔）
 * - 连续 10 次无匹配 → SCOPE_BATTLE 整体判定
 * - 连续 3 次整体判定失败 → detectCurrentPage 兜底
 */
class FightNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val FAST_INTERVAL_MS = 100L
        private const val SKILL_DETECT_INTERVAL = 500L
    }

    override suspend fun recognize(screen: Bitmap): RecognizeResult {
        val coord = ctx.detector.matchTemplate(screen, ScreenState.WARNING)
        if (coord != null) return RecognizeResult(true, coord)
        val skill = ctx.detector.matchTemplate(screen, ScreenState.ULTIMATE_SKILL)
        return RecognizeResult(skill != null, skill)
    }

    /**
     * 战斗循环：
     * - 持续截图检测结束标志
     * - 间隔释放技能
     * - 异常兜底
     */
    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("战斗 Phase")
        var lastSkillTime = 0L
        var missCount = 0
        var battleFallbackCount = 0

        while (coroutineContext.isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(FAST_INTERVAL_MS); continue }
            try {
                if (this.ctx.detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP) != null) {
                    this.ctx.log("结算弹窗")
                    return GamePhase.SETTLEMENT
                }

                if (this.ctx.detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON) != null) {
                    this.ctx.log("胜利确定按钮")
                    return GamePhase.SETTLEMENT
                }

                if (this.ctx.detector.matchTemplate(screen, ScreenState.DEFEAT_POPUP) != null) {
                    this.ctx.log("战斗失败")
                    return GamePhase.SETTLEMENT
                }

                val now = System.currentTimeMillis()
                if (now - lastSkillTime >= SKILL_DETECT_INTERVAL) {
                    if (useSkills(screen)) lastSkillTime = now
                }

                missCount++
                if (missCount >= 10) {
                    val (state, _) = this.ctx.detector.detectForPhase(screen, SceneDetector.SCOPE_BATTLE)
                    if (state == ScreenState.CHAT_ICON || state == ScreenState.RECRUIT_TAB) {
                        this.ctx.log("战斗异常回到大厅")
                        return GamePhase.IDLE
                    }
                    if (state == ScreenState.UNKNOWN) {
                        battleFallbackCount++
                        this.ctx.log("战斗状态无法识别 ($battleFallbackCount/3)")
                        if (battleFallbackCount >= 3) {
                            this.ctx.log("连续3次战斗无法识别，尝试全量页面检测")
                            val detectedPhase = this.ctx.detectCurrentPage(screen)
                            if (detectedPhase != null) {
                                this.ctx.log("检测到当前页面: $detectedPhase，跳转")
                                return detectedPhase
                            }
                            this.ctx.log("页面完全无法识别，停止脚本")
                            throw RuntimeException("战斗阶段页面无法识别")
                        }
                    }
                    missCount = 0
                }
            } finally {
                screen.recycle()
            }
            this.ctx.delay(FAST_INTERVAL_MS)
        }
        return GamePhase.DONE
    }

    private suspend fun useSkills(screen: Bitmap): Boolean {
        val ultimate = this.ctx.detector.matchTemplate(screen, ScreenState.ULTIMATE_SKILL)
        if (ultimate != null) {
            this.ctx.click(ultimate)
            this.ctx.log("释放大招")
            this.ctx.delay(200)
            return true
        }
        val weapon = this.ctx.detector.matchTemplate(screen, ScreenState.WEAPON_SKILL)
        if (weapon != null) {
            this.ctx.click(weapon)
            this.ctx.log("释放武器技能")
            this.ctx.delay(200)
            return true
        }
        return false
    }
}

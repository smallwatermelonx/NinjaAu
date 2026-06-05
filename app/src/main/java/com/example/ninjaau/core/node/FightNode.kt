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
 * 战斗节点 — 下滑 → Boss战 → 结算。
 *
 * 流程：
 * ① 下滑阶段（500ms间隔）：识别下滑按钮并点击，
 *    连续3次无匹配 → Boss阶段
 * ② Boss检测（300ms间隔）：识别Lv图标判定Boss出现
 * ③ Boss战斗（1000ms间隔）：跳跃/大招(5次)/武器(1次)，
 *    连续3次无跳跃 → 阵亡检测 TODO → 结算节点
 * ④ 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class FightNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val SLIDE_INTERVAL_MS = 500L
        private const val BOSS_DETECT_INTERVAL_MS = 100L
        private const val BOSS_LOOP_INTERVAL_MS = 1000L
        private const val MAX_SLIDE_MISS = 3
        private const val MAX_JUMP_MISS = 3
        private const val MAX_SKILL_ATTEMPTS = 10
        private const val MAX_WEAPON_ATTEMPTS = 1
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("战斗 Phase — 下滑阶段")
        this.ctx.delay(6000)

        // ═════════════════════════════════════
        //  ① 下滑阶段
        // ═════════════════════════════════════
        var slideMissCount = 0
        while (currentCoroutineContext().isActive && slideMissCount < MAX_SLIDE_MISS) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(SLIDE_INTERVAL_MS); continue }
            try {
                val slideCoord = this.ctx.detector.matchTemplate(screen, ScreenState.SLIDE_BUTTON)
                if (slideCoord != null) {
                    this.ctx.click(slideCoord)
                    this.ctx.log("下滑")
                    slideMissCount = 0
                } else {
                    slideMissCount++
                }
            } finally {
                screen.recycle()
            }
            this.ctx.delay(SLIDE_INTERVAL_MS)
        }

        if (!currentCoroutineContext().isActive) return null
        this.ctx.log("下滑结束，进入Boss检测")

        // ═════════════════════════════════════
        //  ② Boss检测（Lv图标）
        // ═════════════════════════════════════
        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(BOSS_DETECT_INTERVAL_MS); continue }
            try {
                val lvCoord = this.ctx.detector.matchTemplate(screen, ScreenState.LV_ICON)
                if (lvCoord != null) {
                    this.ctx.log("Boss出现")
                    break
                }
            } finally {
                screen.recycle()
            }
            this.ctx.delay(BOSS_DETECT_INTERVAL_MS)
        }

        if (!currentCoroutineContext().isActive) return null
        this.ctx.log("Boss战斗阶段")

        // ═════════════════════════════════════
        //  ③ Boss战斗阶段
        // ═════════════════════════════════════
        var ultimateCount = 0
        var weaponCount = 0
        var jumpMissCount = 0
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(BOSS_LOOP_INTERVAL_MS); continue }
            var screenMat: org.opencv.core.Mat? = null
            var ultMat: org.opencv.core.Mat? = null
            var jumpMat: org.opencv.core.Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ── 结算/失败检测（全屏） ──
                if (this.ctx.detector.matchTemplateMat(screenMat, ScreenState.SETTLEMENT_POPUP) != null ||
                    this.ctx.detector.matchTemplateMat(screenMat, ScreenState.CONFIRM_BUTTON) != null
                ) {
                    this.ctx.log("结算弹窗")
                    return GamePhase.SETTLEMENT
                }
                if (this.ctx.detector.matchTemplateMat(screenMat, ScreenState.DEFEAT_POPUP) != null) {
                    this.ctx.log("战斗失败")
                    return GamePhase.SETTLEMENT
                }

                // ── 跳跃/上翻按钮（下方 1/3 ROI） ──
                jumpMat = this.ctx.detector.cropBottomThird(screenMat)
                val jumpCoord = this.ctx.detector.matchTemplateMat(jumpMat, ScreenState.JUMP_BUTTON)
                if (jumpCoord != null) {
                    val fullY = jumpCoord.second + screenMat.rows() * 2 / 3
                    this.ctx.click(Pair(jumpCoord.first, fullY))
                    this.ctx.log("跳跃")
                    jumpMissCount = 0
                    lastMatchMs = System.currentTimeMillis()
                } else {
                    val scrollCoord = this.ctx.detector.matchTemplateMat(jumpMat, ScreenState.SCROLL_UP)
                    if (scrollCoord != null) {
                        val fullY = scrollCoord.second + screenMat.rows() * 2 / 3
                        this.ctx.click(Pair(scrollCoord.first, fullY))
                        this.ctx.log("上翻")
                        jumpMissCount = 0
                        lastMatchMs = System.currentTimeMillis()
                    } else {
                        jumpMissCount++
                        if (jumpMissCount >= MAX_JUMP_MISS) {
                            this.ctx.log("连续3次未识别跳跃/上翻按钮")
                            this.ctx.log("阵亡检测 TODO → 直接进入结算节点")
                            return GamePhase.SETTLEMENT
                        }
                    }
                }

                // ── 大招（左侧 1/3 ROI） ──
                if (ultimateCount < MAX_SKILL_ATTEMPTS) {
                    ultMat = this.ctx.detector.cropLeftThird(screenMat)
                    val ultCoord = this.ctx.detector.matchTemplateMat(ultMat, ScreenState.ULTIMATE_SKILL)
                    if (ultCoord != null) {
                        this.ctx.click(ultCoord)
                        this.ctx.log("大招 (${ultimateCount + 1}/$MAX_SKILL_ATTEMPTS)")
                        ultimateCount++
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(100)
                        continue
                    }
                }

                // ── 武器（全屏） ──
                if (weaponCount < MAX_WEAPON_ATTEMPTS) {
                    val wpnCoord = this.ctx.detector.matchTemplateMat(screenMat, ScreenState.WEAPON_SKILL)
                    if (wpnCoord != null) {
                        this.ctx.click(wpnCoord)
                        this.ctx.log("武器 (1/$MAX_WEAPON_ATTEMPTS)")
                        weaponCount++
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(200)
                        continue
                    }
                }

                // ── 超时检测 ──
                checkNodeTimeout(lastMatchMs)
            } finally {
                ultMat?.release()
                jumpMat?.release()
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(BOSS_LOOP_INTERVAL_MS)
        }
        return GamePhase.DONE
    }
}

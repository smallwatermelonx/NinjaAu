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
 * ① 下滑阶段（500ms间隔）：左下1/4识别下滑按钮并点击，
 *    连续3次无匹配 → Boss阶段
 * ② Boss检测（100ms间隔）：左上1/8识别Lv图标判定Boss出现
 * ③ Boss战斗（1000ms间隔）：跳跃(右下1/4)/大招(左1/6,记住坐标连点10次)/武器(下方1/4,记住坐标连点10次)，
 *    连续3次无跳跃 → 结算节点
 * ④ 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class FightNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val SLIDE_INTERVAL_MS = 500L
        private const val BOSS_DETECT_INTERVAL_MS = 100L
        private const val BOSS_LOOP_INTERVAL_MS = 1000L
        private const val MAX_SLIDE_MISS = 3
        private const val MAX_JUMP_MISS = 3
        private const val MAX_SKILL_CLICKS = 10
        private const val SKILL_CLICK_INTERVAL_MS = 240L
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("战斗 Phase — 下滑阶段")
        this.ctx.delay(6000)

        // ═════════════════════════════════════
        //  ① 下滑阶段（左下1/4）
        // ═════════════════════════════════════
        var slideMissCount = 0
        while (currentCoroutineContext().isActive && slideMissCount < MAX_SLIDE_MISS) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(SLIDE_INTERVAL_MS); continue }
            var slideMat: org.opencv.core.Mat? = null
            try {
                slideMat = this.ctx.detector.screenToMat(screen)
                val crop = this.ctx.detector.cropBottomLeftQuarter(slideMat)
                try {
                    val slideCoord = this.ctx.detector.matchTemplateMat(crop, ScreenState.SLIDE_BUTTON)
                    if (slideCoord != null) {
                        val fullY = slideCoord.second + slideMat.rows() * 3 / 4
                        this.ctx.click(Pair(slideCoord.first, fullY))
                        this.ctx.log("下滑")
                        slideMissCount = 0
                    } else {
                        slideMissCount++
                    }
                } finally {
                    crop.release()
                }
            } finally {
                slideMat?.release()
                screen.recycle()
            }
            this.ctx.delay(SLIDE_INTERVAL_MS)
        }

        if (!currentCoroutineContext().isActive) return null
        this.ctx.log("下滑结束，进入Boss检测")

        // ═════════════════════════════════════
        //  ② Boss检测（左上1/8，Lv图标）
        // ═════════════════════════════════════
        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(BOSS_DETECT_INTERVAL_MS); continue }
            var lvMat: org.opencv.core.Mat? = null
            try {
                lvMat = this.ctx.detector.screenToMat(screen)
                val crop = this.ctx.detector.cropTopLeftEighth(lvMat)
                try {
                    val lvCoord = this.ctx.detector.matchTemplateMat(crop, ScreenState.LV_ICON)
                    if (lvCoord != null) {
                        this.ctx.log("Boss出现")
                        break
                    }
                } finally {
                    crop.release()
                }
            } finally {
                lvMat?.release()
                screen.recycle()
            }
            this.ctx.delay(BOSS_DETECT_INTERVAL_MS)
        }

        if (!currentCoroutineContext().isActive) return null
        this.ctx.log("Boss战斗阶段")

        // ═════════════════════════════════════
        //  ③ Boss战斗阶段
        // ═════════════════════════════════════
        var jumpMissCount = 0
        var lastMatchMs = System.currentTimeMillis()

        // 大招：记住坐标，连续点击
        var ultimateCoord: Pair<Float, Float>? = null
        var ultimateClickCount = 0

        // 武器：记住坐标，连续点击
        var weaponCoord: Pair<Float, Float>? = null
        var weaponClickCount = 0

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(BOSS_LOOP_INTERVAL_MS); continue }
            var screenMat: org.opencv.core.Mat? = null
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

                // ── 跳跃/上翻按钮（右下1/4 ROI） ──
                val jumpCrop = this.ctx.detector.cropBottomRightQuarter(screenMat)
                try {
                    val jumpCoord = this.ctx.detector.matchTemplateMat(jumpCrop, ScreenState.JUMP_BUTTON)
                    if (jumpCoord != null) {
                        val fullX = jumpCoord.first + screenMat.cols() / 2
                        val fullY = jumpCoord.second + screenMat.rows() * 3 / 4
                        this.ctx.click(Pair(fullX, fullY))
                        this.ctx.log("跳跃")
                        jumpMissCount = 0
                        lastMatchMs = System.currentTimeMillis()
                    } else {
                        val scrollCoord = this.ctx.detector.matchTemplateMat(jumpCrop, ScreenState.SCROLL_UP)
                        if (scrollCoord != null) {
                            val fullX = scrollCoord.first + screenMat.cols() / 2
                            val fullY = scrollCoord.second + screenMat.rows() * 3 / 4
                            this.ctx.click(Pair(fullX, fullY))
                            this.ctx.log("上翻")
                            jumpMissCount = 0
                            lastMatchMs = System.currentTimeMillis()
                        } else {
                            jumpMissCount++
                            if (jumpMissCount >= MAX_JUMP_MISS) {
                                this.ctx.log("连续3次未识别跳跃/上翻按钮，进入结算")
                                return GamePhase.SETTLEMENT
                            }
                        }
                    }
                } finally {
                    jumpCrop.release()
                }

                // ── 大招（左1/6，记住坐标连点） ──
                if (ultimateClickCount < MAX_SKILL_CLICKS) {
                    if (ultimateCoord == null) {
                        // 首次识别：匹配并记住坐标
                        val ultCrop = this.ctx.detector.cropLeftSixth(screenMat)
                        try {
                            val ultCoord = this.ctx.detector.matchTemplateMat(ultCrop, ScreenState.ULTIMATE_SKILL)
                            if (ultCoord != null) {
                                ultimateCoord = Pair(ultCoord.first, ultCoord.second)
                                this.ctx.click(ultimateCoord)
                                ultimateClickCount++
                                this.ctx.log("大招 (${ultimateClickCount}/$MAX_SKILL_CLICKS)")
                                lastMatchMs = System.currentTimeMillis()
                                this.ctx.delay(SKILL_CLICK_INTERVAL_MS)
                                continue
                            }
                        } finally {
                            ultCrop.release()
                        }
                    } else {
                        // 已记住坐标：直接点击，不识别
                        this.ctx.click(ultimateCoord)
                        ultimateClickCount++
                        this.ctx.log("大招 (${ultimateClickCount}/$MAX_SKILL_CLICKS)")
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(SKILL_CLICK_INTERVAL_MS)
                        continue
                    }
                }

                // ── 武器（下方1/4，记住坐标连点） ──
                if (weaponClickCount < MAX_SKILL_CLICKS) {
                    if (weaponCoord == null) {
                        // 首次识别：匹配并记住坐标
                        val wpnCrop = this.ctx.detector.cropBottomQuarter(screenMat)
                        try {
                            val wpnCoord = this.ctx.detector.matchTemplateMat(wpnCrop, ScreenState.WEAPON_SKILL)
                            if (wpnCoord != null) {
                                weaponCoord = Pair(wpnCoord.first, wpnCoord.second + screenMat.rows() * 3 / 4)
                                this.ctx.click(weaponCoord)
                                weaponClickCount++
                                this.ctx.log("武器 (${weaponClickCount}/$MAX_SKILL_CLICKS)")
                                lastMatchMs = System.currentTimeMillis()
                                this.ctx.delay(SKILL_CLICK_INTERVAL_MS)
                                continue
                            }
                        } finally {
                            wpnCrop.release()
                        }
                    } else {
                        // 已记住坐标：直接点击，不识别
                        this.ctx.click(weaponCoord)
                        weaponClickCount++
                        this.ctx.log("武器 (${weaponClickCount}/$MAX_SKILL_CLICKS)")
                        lastMatchMs = System.currentTimeMillis()
                        this.ctx.delay(SKILL_CLICK_INTERVAL_MS)
                        continue
                    }
                }

                // ── 超时检测 ──
                checkNodeTimeout(lastMatchMs)
            } finally {
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(BOSS_LOOP_INTERVAL_MS)
        }
        return GamePhase.DONE
    }
}

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
 * 战斗节点 — 下滑 → 上翻 → Boss战 → 结算。
 *
 * 流程：
 * ① 下滑阶段（无间隔）：左下1/4识别下滑按钮并点击，
 *    左1/2×下1/4识别血咒并点击（仅首次），无匹配 → 下滑结束
 * ② 下滑结束后立即检测一次上翻（右下1/4），有则点击
 * ③ Boss检测+战斗（统一循环）：
 *    - Lv未出现时：100ms快速扫描左上1/8 Lv图标
 *    - Lv出现后：大招(左1/6,识别则点击，300ms间隔) → 跳跃(右下1/4) → 武器(下方1/4,记住坐标连点10次)
 *    - 出口：连续3次未识别跳跃 → 结算节点
 * ④ 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class FightNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val BOSS_DETECT_INTERVAL_MS = 100L
        private const val BOSS_LOOP_INTERVAL_MS = 300L
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
        this.ctx.delay(5000) // 等待下滑按钮出现（加载结束后约5-6秒）
        var slideDetected = false
        var bloodCurseClicked = false
        while (currentCoroutineContext().isActive && !slideDetected) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(1000L); continue }
            var slideMat: org.opencv.core.Mat? = null
            try {
                slideMat = this.ctx.detector.screenToMat(screen)
                val crop = this.ctx.detector.cropBottomLeftFourth(slideMat)
                try {
                    val slideCoord = this.ctx.detector.matchTemplateMat(crop, ScreenState.SLIDE_BUTTON)
                    if (slideCoord != null) {
                        val fullY = slideCoord.second + slideMat.rows() * 3 / 4
                        this.ctx.click(Pair(slideCoord.first, fullY))
                        this.ctx.log("下滑")
                    } else {
                        slideDetected = true
                    }
                } finally {
                    crop.release()
                }

                // ── 血咒（左1/2 × 下1/4，点一次即停） ──
                if (!bloodCurseClicked) {
                    val curseCrop = this.ctx.detector.cropBottomLeftHalf(slideMat)
                    try {
                        val curseCoord = this.ctx.detector.matchTemplateMat(curseCrop, ScreenState.BLOOD_CURSE)
                        if (curseCoord != null) {
                            val fullY = curseCoord.second + slideMat.rows() * 3 / 4
                            this.ctx.click(Pair(curseCoord.first, fullY))
                            this.ctx.log("血咒")
                            bloodCurseClicked = true
                        }
                    } finally {
                        curseCrop.release()
                    }
                }
            } finally {
                slideMat?.release()
                screen.recycle()
            }
        }

        if (!currentCoroutineContext().isActive) return null
        this.ctx.log("下滑结束")

        // ═══ 下滑结束后立即检测一次上翻 ═══
        val scrollScreen = this.ctx.captureBitmap()
        if (scrollScreen != null) {
            var scrollMat: org.opencv.core.Mat? = null
            try {
                scrollMat = this.ctx.detector.screenToMat(scrollScreen)
                val scrollCrop = this.ctx.detector.cropBottomRightFourth(scrollMat)
                try {
                    val scrollCoord = this.ctx.detector.matchTemplateMat(scrollCrop, ScreenState.SCROLL_UP)
                    if (scrollCoord != null) {
                        val fullX = scrollCoord.first + scrollMat.cols() * 3 / 4
                        val fullY = scrollCoord.second + scrollMat.rows() * 3 / 4
                        this.ctx.click(Pair(fullX, fullY))
                        this.ctx.log("上翻")
                    }
                } finally {
                    scrollCrop.release()
                }
            } finally {
                scrollMat?.release()
                scrollScreen.recycle()
            }
        }

        this.ctx.log("进入Boss检测")

        // ═════════════════════════════════════
        //  ②③ Boss检测 + Boss战斗（统一循环）
        //  Lv未出现时：100ms快速扫描Lv
        //  Lv出现后：检测跳跃/大招/武器
        //  出口：连续3次无跳跃+上翻 → 结算
        // ═════════════════════════════════════
        var bossAppeared = false
        var ultimateActive = false
        var jumpMissCount = 0
        var lastMatchMs = System.currentTimeMillis()

        // 武器：记住坐标，连续点击
        var weaponCoord: Pair<Float, Float>? = null
        var weaponClickCount = 0

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(if (bossAppeared) BOSS_LOOP_INTERVAL_MS else BOSS_DETECT_INTERVAL_MS); continue }
            var screenMat: org.opencv.core.Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                if (!bossAppeared) {
                    // ── Lv检测（左上1/8） ──
                    val lvCrop = this.ctx.detector.cropTopLeftEighth(screenMat)
                    try {
                        val lvCoord = this.ctx.detector.matchTemplateMat(lvCrop, ScreenState.LV_ICON)
                        if (lvCoord != null) {
                            bossAppeared = true
                            this.ctx.log("Boss出现，进入战斗")
                            lastMatchMs = System.currentTimeMillis()
                        }
                    } finally {
                        lvCrop.release()
                    }
                    if (!bossAppeared) {
                        this.ctx.delay(BOSS_DETECT_INTERVAL_MS)
                        continue
                    }
                    // Lv刚出现：立即进入技能检测
                    continue
                }

                // ── Boss已出现：大招优先（左1/6） ──
                val ultCrop = this.ctx.detector.cropLeftSixth(screenMat)
                try {
                    val ultCoord = this.ctx.detector.matchTemplateMat(ultCrop, ScreenState.ULTIMATE_SKILL)
                    if (ultCoord != null) {
                        val fullY = ultCoord.second + screenMat.rows() / 3
                        this.ctx.click(Pair(ultCoord.first, fullY))
                        this.ctx.log("大招")
                        lastMatchMs = System.currentTimeMillis()
                        ultimateActive = true
                        checkNodeTimeout(lastMatchMs)
                        this.ctx.delay(BOSS_LOOP_INTERVAL_MS)
                        continue
                    }
                } finally {
                    ultCrop.release()
                }

                // 大招曾出现但现在未检测到 → 大招放完，跳过本轮再检测跳跃
                if (ultimateActive) {
                    ultimateActive = false
                    this.ctx.delay(BOSS_LOOP_INTERVAL_MS)
                    continue
                }

                // ── 跳跃检测（右下1/4） ──
                val jumpCrop = this.ctx.detector.cropBottomRightFourth(screenMat)
                try {
                    val jumpCoord = this.ctx.detector.matchTemplateMat(jumpCrop, ScreenState.JUMP_BUTTON)
                    if (jumpCoord != null) {
                        val fullX = jumpCoord.first + screenMat.cols() * 3 / 4
                        val fullY = jumpCoord.second + screenMat.rows() * 3 / 4
                        this.ctx.click(Pair(fullX, fullY))
                        this.ctx.log("跳跃")
                        jumpMissCount = 0
                        lastMatchMs = System.currentTimeMillis()
                    } else {
                        jumpMissCount++
                        if (jumpMissCount >= MAX_JUMP_MISS) {
                            this.ctx.log("连续3次未识别跳跃，进入结算")
                            return GamePhase.SETTLEMENT
                        }
                    }
                } finally {
                    jumpCrop.release()
                }

                // ── 武器（下方1/4，识别后连续点击 10 次） ──
                if (weaponClickCount == 0) {
                    val wpnCrop = this.ctx.detector.cropBottomQuarter(screenMat)
                    try {
                        val wpnCoord = this.ctx.detector.matchTemplateMat(wpnCrop, ScreenState.WEAPON_SKILL)
                        if (wpnCoord != null) {
                            weaponCoord = Pair(wpnCoord.first, wpnCoord.second + screenMat.rows() * 3 / 4)
                        }
                    } finally {
                        wpnCrop.release()
                    }
                }
                if (weaponCoord != null && weaponClickCount < MAX_SKILL_CLICKS) {
                    this.ctx.click(weaponCoord!!)
                    weaponClickCount++
                    this.ctx.log("武器 (${weaponClickCount}/$MAX_SKILL_CLICKS)")
                    lastMatchMs = System.currentTimeMillis()
                    this.ctx.delay(SKILL_CLICK_INTERVAL_MS)
                }

                // ── 超时检测 ──
                checkNodeTimeout(lastMatchMs)
            } finally {
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(if (bossAppeared) BOSS_LOOP_INTERVAL_MS else BOSS_DETECT_INTERVAL_MS)
        }
        return GamePhase.DONE
    }
}

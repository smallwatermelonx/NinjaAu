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
 * ① 下滑阶段：主动等待下滑按钮出现（最长10s）→ 无间隔点击 → 消失即结束
 *    左1/2×下1/4识别血咒并点击（仅首次）
 * ② 下滑结束后立即检测一次上翻（右下1/4），有则点击
 * ③ Boss检测+战斗（双频率循环）：
 *    - Lv未出现时：100ms高频扫描，抢输出
 *    - Lv出现后：300ms常规循环，大招(左1/6) → 跳跃(右下1/4) → 武器(Lv出现24秒后固定坐标每秒点击)
 *    - Boss出现后3秒内跳过Lv消失检测（大招动画导致Lv闪烁）
 *    - 3秒后Lv消失 → 战斗结束 → 500ms延迟 → 检测失败跳过按钮 → 失败节点 or 结算节点
 * ④ 30秒无匹配 → 抛 NodeTimeoutException 回到主流程
 */
class FightNode(private val ctx: NodeContext) : GameNode {

    companion object {
        private const val BOSS_SCAN_INTERVAL_MS = 100L
        private const val BOSS_LOOP_INTERVAL_MS = 300L
        private const val POST_BATTLE_DELAY_MS = 500L
        private const val LV_SETTLE_DELAY_MS = 3000L
        private const val WEAPON_DELAY_MS = 24_000L
        private const val WEAPON_CLICK_INTERVAL_MS = 1000L
        // 2560x1440 分辨率下武器坐标
        private const val WEAPON_X = 765f
        private const val WEAPON_Y = 1265f
    }

    override suspend fun execute(ctx: GameContext): GamePhase? {
        this.ctx.log("战斗 Phase — 下滑阶段")

        // ═════════════════════════════════════
        //  ① 下滑阶段（左下1/4）
        //  阶段A: 等待下滑按钮出现（最长10s，部分boss无下滑）
        //  阶段B: 出现后无间隔点击，消失即结束
        // ═════════════════════════════════════
        var bloodCurseClicked = false

        // ── 阶段A: 等待下滑按钮出现 ──
        var slideAppeared = false
        val slideWaitStart = System.currentTimeMillis()
        while (currentCoroutineContext().isActive && !slideAppeared) {
            if (System.currentTimeMillis() - slideWaitStart > 10_000) {
                this.ctx.log("下滑按钮 10s 未出现，跳过下滑阶段")
                break
            }
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(300L); continue }
            var slideMat: org.opencv.core.Mat? = null
            try {
                slideMat = this.ctx.detector.screenToMat(screen)
                val crop = this.ctx.detector.cropBottomLeftFourth(slideMat)
                try {
                    if (this.ctx.detector.matchTemplateMat(crop, ScreenState.SLIDE_BUTTON) != null) {
                        slideAppeared = true
                    }
                } finally {
                    crop.release()
                }
            } finally {
                slideMat?.release()
                screen.recycle()
            }
            if (!slideAppeared) this.ctx.delay(300)
        }

        // ── 阶段B: 无间隔点击下滑按钮，消失即结束 ──
        while (currentCoroutineContext().isActive && slideAppeared) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(500L); continue }
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
                        // 下滑按钮消失 → 下滑阶段结束
                        slideAppeared = false
                    }
                } finally {
                    crop.release()
                }
                // 血咒检测（点击期间顺便检测）
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
        //  Boss检测 + Boss战斗（统一循环，300ms）
        //  Lv未出现时：扫描Lv图标
        //  Lv出现后：大招 → 跳跃 → 武器
        //  Lv消失 → 战斗结束 → 500ms → 失败节点 or 结算节点
        // ═════════════════════════════════════
        var bossAppeared = false
        var bossAppearTime = 0L
        var lastMatchMs = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val screen = this.ctx.captureBitmap()
            if (screen == null) { this.ctx.delay(if (bossAppeared) BOSS_LOOP_INTERVAL_MS else BOSS_SCAN_INTERVAL_MS); continue }
            var screenMat: org.opencv.core.Mat? = null
            try {
                screenMat = this.ctx.detector.screenToMat(screen)

                // ── Lv检测（左上1/8） ──
                var lvDetected = false
                val lvCrop = this.ctx.detector.cropTopLeftEighth(screenMat)
                try {
                    val lvCoord = this.ctx.detector.matchTemplateMat(lvCrop, ScreenState.LV_ICON)
                    if (lvCoord != null) {
                        lvDetected = true
                    }
                } finally {
                    lvCrop.release()
                }

                // ── Lv未出现 ──
                if (!lvDetected) {
                    if (!bossAppeared) {
                        // Boss尚未出现，继续高频扫描
                        checkNodeTimeout(lastMatchMs)
                        this.ctx.delay(BOSS_SCAN_INTERVAL_MS)
                        continue
                    } else if (System.currentTimeMillis() - bossAppearTime < LV_SETTLE_DELAY_MS) {
                        // Boss刚出现3秒内，大招动画可能导致Lv闪烁，跳过消失检测
                        this.ctx.delay(BOSS_LOOP_INTERVAL_MS)
                        continue
                    } else {
                        // Boss已出现超过10秒，Lv消失 → 战斗结束
                        this.ctx.log("Lv消失，战斗结束")
                        break
                    }
                }

                // ── Lv首次出现 ──
                if (!bossAppeared) {
                    bossAppeared = true
                    bossAppearTime = System.currentTimeMillis()
                    this.ctx.log("Boss出现，进入战斗")
                }
                lastMatchMs = System.currentTimeMillis()

                // ── 大招检测（最高优先级，左1/6） ──
                var ultDetected = false
                val ultCrop = this.ctx.detector.cropLeftSixth(screenMat)
                try {
                    val ultCoord = this.ctx.detector.matchTemplateMat(ultCrop, ScreenState.ULTIMATE_SKILL)
                    if (ultCoord != null) {
                        val fullY = ultCoord.second + screenMat.rows() / 3
                        this.ctx.click(Pair(ultCoord.first, fullY))
                        this.ctx.log("大招")
                        ultDetected = true
                    }
                } finally {
                    ultCrop.release()
                }

                // ── 大招命中时跳过跳跃和武器 ──
                if (!ultDetected) {

                    // ── 跳跃检测（右下1/4，持续躲避弹幕） ──
                    val jumpCrop = this.ctx.detector.cropBottomRightFourth(screenMat)
                    try {
                        val jumpCoord = this.ctx.detector.matchTemplateMat(jumpCrop, ScreenState.JUMP_BUTTON)
                        if (jumpCoord != null) {
                            val fullX = jumpCoord.first + screenMat.cols() * 3 / 4
                            val fullY = jumpCoord.second + screenMat.rows() * 3 / 4
                            this.ctx.click(Pair(fullX, fullY))
                            this.ctx.log("跳跃")
                        }
                    } finally {
                        jumpCrop.release()
                    }

                    // ── 武器（Lv出现24秒后，固定坐标每秒点击一次） ──
                    if (System.currentTimeMillis() - bossAppearTime >= WEAPON_DELAY_MS) {
                        this.ctx.click(Pair(WEAPON_X, WEAPON_Y))
                        this.ctx.log("武器 (固定坐标)")
                    }
                }

                checkNodeTimeout(lastMatchMs)
            } finally {
                screenMat?.release()
                screen.recycle()
            }
            this.ctx.delay(if (bossAppeared) BOSS_LOOP_INTERVAL_MS else BOSS_SCAN_INTERVAL_MS)
        }

        // ═══ Lv消失 → 战斗结束 ═══
        this.ctx.log("Lv消失，战斗结束，等待${POST_BATTLE_DELAY_MS}ms判断结果")
        this.ctx.delay(POST_BATTLE_DELAY_MS)

        // 检测失败跳过按钮（接力界面）
        val resultScreen = this.ctx.captureBitmap()
        if (resultScreen != null) {
            var resultMat: org.opencv.core.Mat? = null
            try {
                resultMat = this.ctx.detector.screenToMat(resultScreen)
                val skipW = resultMat.cols() / 5
                val skipH = resultMat.rows() / 5
                val skipCrop = org.opencv.core.Mat(resultMat, org.opencv.core.Rect(resultMat.cols() * 2 / 5, resultMat.rows() * 4 / 5, skipW, skipH))
                try {
                    val skipCoord = this.ctx.detector.matchTemplateMat(skipCrop, ScreenState.DEFEAT_SKIP)
                    if (skipCoord != null) {
                        this.ctx.log("检测到接力跳过按钮，进入失败节点")
                        return GamePhase.DEFEAT
                    }
                } finally {
                    skipCrop.release()
                }
            } finally {
                resultMat?.release()
                resultScreen.recycle()
            }
        }

        this.ctx.log("未检测到失败，进入结算节点")
        return GamePhase.SETTLEMENT
    }
}

package com.example.ninjaau.core.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.util.AssetUtil
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.OpenCVUtil
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.ScreenState
import org.opencv.core.Mat

/**
 * 场景识别层 — 将 Bitmap 映射为 ScreenState + 坐标
 * 模板路径与阈值在此集中管理，ScreenState 枚举本身不耦合识别配置
 *
 * 模板缓存：首次加载后常驻内存，避免重复 I/O。
 * 按阶段检测：每个检测只检相关状态，不遍历全表。
 */
class SceneDetector(private val context: Context) {
    private val TAG = "SceneDetector"

    private data class TemplateEntry(val path: String, val threshold: Float = 0.8f)

    /** 等级匹配结果（含相似度分数，用于最佳匹配策略） */
    data class GradeMatch(
        val grade: BountyGrade,
        val similarity: Float,
        val centerX: Float,
        val centerY: Float
    )

    /** 模板测试结果 */
    data class TemplateTestResult(
        val name: String,
        val similarity: Float,
        val threshold: Float,
        val passed: Boolean,
        val centerX: Float?,
        val centerY: Float?
    )

    /** 按节点分组的模板定义 */
    enum class NodeTemplateGroup(
        val displayName: String,
        val states: List<ScreenState>,
        val testLevelIcons: Boolean,
        val testGradeIcons: Boolean
    ) {
        HALL("大厅导航", listOf(
            ScreenState.CHAT_ICON, ScreenState.RECRUIT_LIST_SCREEN
        ), false, false),
        RECRUIT_LIST("悬赏列表扫描", listOf(
            ScreenState.RECRUIT_TAB
        ), false, true),
        BOUNTY_DETAIL("悬赏详情/队伍房间", listOf(
            ScreenState.READY_BUTTON, ScreenState.DAILY_LIMIT
        ), true, false),
        BATTLE_LOADING("战斗加载", listOf(ScreenState.BATTLE_LOADING), false, false),
        FIGHT("战斗", listOf(
            ScreenState.SLIDE_BUTTON, ScreenState.ULTIMATE_SKILL, ScreenState.LV_ICON,
            ScreenState.SETTLEMENT_POPUP, ScreenState.CONFIRM_BUTTON, ScreenState.DEFEAT_POPUP,
            ScreenState.JUMP_BUTTON, ScreenState.SCROLL_UP, ScreenState.WEAPON_SKILL
        ), false, false),
        SETTLEMENT("结算领奖", listOf(
            ScreenState.SETTLEMENT_POPUP, ScreenState.CONFIRM_BUTTON, ScreenState.CHAT_ICON
        ), false, false),
        DEFEAT("战斗失败", listOf(ScreenState.DEFEAT_POPUP), false, false),
        RECRUIT_INVITE("招募邀请弹窗", listOf(ScreenState.RECRUIT_INVITE), false, false),
        INVITATION("组队邀请弹窗", listOf(
            ScreenState.TEAM_INVITATION, ScreenState.INVITE_REJECT,
            ScreenState.INVITE_CHECKBOX, ScreenState.INVITE_AGREE
        ), false, false),
    }

    /**
     * 按节点分组测试模板匹配 — 仅测试该节点相关的模板
     * @param screen 当前屏幕截图
     * @param group 要测试的节点模板组
     * @return 按相似度降序排列的测试结果
     */
    fun testNodeTemplates(screen: Bitmap, group: NodeTemplateGroup): List<TemplateTestResult> {
        val results = mutableListOf<TemplateTestResult>()

        // 1. 测试 ScreenState 模板
        for (state in group.states) {
            val entry = templates[state] ?: continue
            val template = getTemplate(state)
            if (template == null) {
                results.add(TemplateTestResult(state.description, 0f, entry.threshold, false, null, null))
                continue
            }
            val result = TemplateMatcher.match(screen, template, entry.threshold)
            results.add(TemplateTestResult(
                name = state.description,
                similarity = result.similarity,
                threshold = entry.threshold,
                passed = result.isMatched,
                centerX = if (result.isMatched) result.centerX else null,
                centerY = if (result.isMatched) result.centerY else null
            ))
        }

        // 2. 测试等级图标（levelIcon）
        if (group.testLevelIcons) {
            for (grade in BountyGrade.entries) {
                val path = grade.levelIconPath() ?: continue
                val cached = levelIconCache[grade]
                val template = if (cached != null && !cached.isRecycled) cached else {
                    val loaded = AssetUtil.loadBitmapFromAssets(context, path) ?: continue
                    levelIconCache[grade] = loaded
                    loaded
                }
                val result = TemplateMatcher.match(screen, template, 0.95f)
                results.add(TemplateTestResult(
                    name = "等级 ${grade.displayName}(lv${grade.level})",
                    similarity = result.similarity,
                    threshold = 0.95f,
                    passed = result.isMatched,
                    centerX = if (result.isMatched) result.centerX else null,
                    centerY = if (result.isMatched) result.centerY else null
                ))
            }
        }

        // 3. 测试招募列表等级图标（gradeIcon）
        if (group.testGradeIcons) {
            for (grade in BountyGrade.entries) {
                val cached = gradeIconCache[grade]
                val template = if (cached != null && !cached.isRecycled) cached else {
                    val loaded = AssetUtil.loadBitmapFromAssets(context, grade.gradeIconPath()) ?: continue
                    gradeIconCache[grade] = loaded
                    loaded
                }
                val result = TemplateMatcher.match(screen, template, 0.85f)
                results.add(TemplateTestResult(
                    name = "招募 ${grade.displayName}",
                    similarity = result.similarity,
                    threshold = 0.85f,
                    passed = result.isMatched,
                    centerX = if (result.isMatched) result.centerX else null,
                    centerY = if (result.isMatched) result.centerY else null
                ))
            }
        }

        return results.sortedByDescending { it.similarity }
    }

    private val templates: Map<ScreenState, TemplateEntry> = mapOf(
        // ── 大厅 ──
        ScreenState.CHAT_ICON to TemplateEntry("templates/lobby/hall_chat.png", 0.75f),
        // ── 聊天/招募 ──
        ScreenState.RECRUIT_TAB to TemplateEntry("templates/chat/team_recruit.png"),
        ScreenState.RECRUIT_TAB_BLACK to TemplateEntry("templates/chat/team_recruit_black.png", 0.75f),
        ScreenState.OUT_OF_RANGE_RECRUIT to TemplateEntry("templates/bounty_list/out_of_range.png", 0.7f),
        ScreenState.RECRUIT_LIST_SCREEN to TemplateEntry("templates/bounty_list/team_recruit_black.png", 0.75f),
        ScreenState.RECRUIT_INVITE to TemplateEntry("templates/bounty_list/recruit_invite.png"),
        // ── 入队 ──
        ScreenState.READY_BUTTON to TemplateEntry("templates/team_room/prepare.png"),
        ScreenState.EXIT_CONFIRM to TemplateEntry("templates/team_room/confirm.png"),
        ScreenState.DAILY_LIMIT to TemplateEntry("templates/team_room/daily_limit.png"),
        // ── 战斗 ──
        ScreenState.BATTLE_LOADING to TemplateEntry("templates/battle_loading/smile.png"),
        ScreenState.WARNING to TemplateEntry("templates/fight/warning.png", 0.7f),
        ScreenState.SLIDE_BUTTON to TemplateEntry("templates/fight/slide.png"),
        ScreenState.LV_ICON to TemplateEntry("templates/fight/lv.png"),
        ScreenState.JUMP_BUTTON to TemplateEntry("templates/fight/jump.png"),
        ScreenState.SCROLL_UP to TemplateEntry("templates/fight/scroll_up.png"),
        ScreenState.ULTIMATE_SKILL to TemplateEntry("templates/fight/shihara/r_shihara.png", 0.6f),
        ScreenState.WEAPON_SKILL to TemplateEntry("templates/fight/wopen_shedao.png", 0.6f),
        ScreenState.DEFEAT_POPUP to TemplateEntry("templates/fight/defeat_popup.png", 0.6f),
        // ── 结算 ──
        ScreenState.SETTLEMENT_POPUP to TemplateEntry("templates/settlement/black.png", 0.7f),
        ScreenState.CONFIRM_BUTTON to TemplateEntry("templates/settlement/confirm.png"),
        // ── 通用 ──
        ScreenState.BACK_BUTTON to TemplateEntry("templates/other/backward.png"),
        // ── TAB刷新（私聊页签） ──
        ScreenState.CHAT_TAB to TemplateEntry("templates/chat/private_chat.png"),
        // ── 组队邀请弹窗（任意节点可触发） ──
        ScreenState.TEAM_INVITATION to TemplateEntry("templates/invitation/team_invitation.png", 0.75f),
        ScreenState.INVITE_REJECT to TemplateEntry("templates/invitation/reject_btn.png", 0.75f),
        ScreenState.INVITE_CHECKBOX to TemplateEntry("templates/invitation/checkbox.png", 0.75f),
        ScreenState.INVITE_AGREE to TemplateEntry("templates/invitation/agree_btn.png", 0.75f),
    )

    companion object {
        /** 整体判定（全量兜底） */
        val SCOPE_ALL = ScreenState.values().toList()
    }

    // ── 模板缓存 ──
    private val templateCache = mutableMapOf<String, Bitmap>()
    private val gradeIconCache = mutableMapOf<BountyGrade, Bitmap>()
    private val levelIconCache = mutableMapOf<BountyGrade, Bitmap>()

    private fun getTemplate(state: ScreenState): Bitmap? {
        val entry = templates[state] ?: return null
        val cached = templateCache[entry.path]
        if (cached != null && !cached.isRecycled) return cached
        val loaded = AssetUtil.loadBitmapFromAssets(context, entry.path) ?: return null
        templateCache[entry.path] = loaded
        return loaded
    }

    fun release() {
        templateCache.values.forEach { if (!it.isRecycled) it.recycle() }
        templateCache.clear()
        gradeIconCache.values.forEach { if (!it.isRecycled) it.recycle() }
        gradeIconCache.clear()
        levelIconCache.values.forEach { if (!it.isRecycled) it.recycle() }
        levelIconCache.clear()
    }

    // ── 公开检测方法 ──

    /** 全量扫描（用于整体判定兜底） */
    fun detect(screenBitmap: Bitmap): ScreenState {
        return detectWithCoord(screenBitmap).first
    }

    /** 全量扫描含坐标 */
    fun detectWithCoord(screenBitmap: Bitmap): Pair<ScreenState, Pair<Float, Float>?> {
        for (state in detectionOrder) {
            val coord = matchTemplate(screenBitmap, state)
            if (coord != null) return Pair(state, coord)
        }
        return Pair(ScreenState.UNKNOWN, null)
    }

    /** 对指定状态做模板匹配，返回命中坐标（中心点）或 null */
    fun matchTemplate(screen: Bitmap, state: ScreenState): Pair<Float, Float>? {
        if (state == ScreenState.UNKNOWN) return null
        val entry = templates[state]
        if (entry == null) {
            LogUtil.w(TAG, "⚠ 跳过 $state: 无模板配置")
            return null
        }
        val template = getTemplate(state)
        if (template == null) {
            LogUtil.w(TAG, "⚠ $state: 模板图片读取失败 -> ${entry.path}")
            return null
        }
        val result = TemplateMatcher.match(screen, template, entry.threshold)
        if (result.isMatched) {
            LogUtil.i(TAG, "$state: 相似度 ${String.format("%.2f", result.similarity)} ≥ ${entry.threshold}")
            return Pair(result.centerX, result.centerY)
        }else {
            LogUtil.d(TAG, "$state: 最高相似度 = ${String.format("%.2f", result.similarity)} (阈值=${entry.threshold})")
        }
        return null
    }

    /**
     * 在指定坐标附近搜索模板（邻近扩散匹配）
     * 用于等级图标→同一悬赏块的加入按钮匹配。
     * 从 nearX,nearY 向右下矩形区域搜索，避免匹配到其他悬赏块的按钮。
     */
    fun matchTemplateNear(
        screen: Bitmap,
        state: ScreenState,
        nearX: Float,
        nearY: Float,
        rangeRight: Int = 350,
        rangeDown: Int = 300
    ): Pair<Float, Float>? {
        val entry = templates[state] ?: return null
        val template = getTemplate(state) ?: return null

        val left = maxOf(0, nearX.toInt())
        val top = maxOf(0, nearY.toInt())
        val right = minOf(screen.width, nearX.toInt() + rangeRight)
        val bottom = minOf(screen.height, nearY.toInt() + rangeDown)

        if (right - left < template.width || bottom - top < template.height) return null

        val cropped = Bitmap.createBitmap(screen, left, top, right - left, bottom - top)
        val result = TemplateMatcher.match(cropped, template, entry.threshold)
        cropped.recycle()

        if (result.isMatched) {
            val ax = left + result.centerX
            val ay = top + result.centerY
            LogUtil.i(TAG, "邻近匹配 $state: 局部(${result.centerX}, ${result.centerY}) → 全局($ax, $ay)")
            return Pair(ax, ay)
        }
        LogUtil.d(TAG, "❌ 邻近匹配 $state: 等级图标($nearX,$nearY) 附近未找到")
        return null
    }

    /** 在截图中匹配指定悬赏等级的图标（用于节点3扫描） */
    fun matchGradeIcon(screen: Bitmap, grade: BountyGrade): GradeMatch? {
        val template = gradeIconCache.getOrPut(grade) {
            AssetUtil.loadBitmapFromAssets(context, grade.gradeIconPath()) ?: return null
        }
        val result = TemplateMatcher.match(screen, template, 0.85f)
        if (result.isMatched) {
            LogUtil.i(TAG, "等级图标 ${grade.displayName}: 匹配度 ${String.format("%.2f", result.similarity)}")
            return GradeMatch(grade, result.similarity, result.centerX, result.centerY)
        }
        return null
    }

    // ── Mat 预转换方法（避免重复 Bitmap→Mat） ──

    /** 将 screen Bitmap 转为 Mat，调用方用完需 release */
    fun screenToMat(screen: Bitmap): Mat = OpenCVUtil.bitmapToMat(screen)

    /** 裁剪 Mat 左下角 1/3 区域（超出范围标识所在区域），调用方用完需 release */
    fun cropBottomLeft(mat: Mat): Mat {
        val w = mat.cols() / 3
        val h = mat.rows() / 3
        val y = mat.rows() - h
        return Mat(mat, org.opencv.core.Rect(0, y, w, h))
    }

    /** 裁剪 Mat 左侧 1/4 区域（等级图标所在区域），高度不动，调用方用完需 release */
    fun cropLeftQuarter(mat: Mat): Mat {
        val w = mat.cols() / 4
        return Mat(mat, org.opencv.core.Rect(0, 0, w, mat.rows()))
    }

    /** 使用预转换的 screen Mat 匹配指定状态，返回命中坐标或 null */
    fun matchTemplateMat(screenMat: Mat, state: ScreenState): Pair<Float, Float>? {
        if (state == ScreenState.UNKNOWN) return null
        val entry = templates[state] ?: return null
        val template = getTemplate(state) ?: return null
        val result = TemplateMatcher.matchWithMat(screenMat, template, entry.threshold)
        if (result.isMatched) {
            LogUtil.i(TAG, "$state: 相似度 ${String.format("%.2f", result.similarity)} ≥ ${entry.threshold}")
            return Pair(result.centerX, result.centerY)
        }
        return null
    }

    /** 使用预转换的 screen Mat 搜索多个等级图标 — 最佳匹配策略 */
    fun matchAnyGradeMat(screenMat: Mat, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        val matches = mutableListOf<GradeMatch>()
        for (grade in grades) {
            val cached = gradeIconCache[grade]
            val template = if (cached != null && !cached.isRecycled) cached else {
                val loaded = AssetUtil.loadBitmapFromAssets(context, grade.gradeIconPath()) ?: continue
                gradeIconCache[grade] = loaded
                loaded
            }
            val result = TemplateMatcher.matchWithMat(screenMat, template, 0.85f)
            if (result.isMatched) {
                matches.add(GradeMatch(grade, result.similarity, result.centerX, result.centerY))
            }
        }

        if (matches.isEmpty()) {
            LogUtil.d(TAG, "matchAnyGradeMat: ${grades.size}个等级均未匹配")
            return null
        }

        matches.sortByDescending { it.similarity }
        val best = matches.first()

        if (matches.size > 1) {
            val matchSummary = matches.joinToString { "${it.grade.displayName}=${String.format("%.2f", it.similarity)}" }
            LogUtil.w(TAG, "matchAnyGradeMat: 多个等级匹配 - $matchSummary，最佳=${best.grade.displayName}")
        }

        LogUtil.i(TAG, "matchAnyGradeMat → 选中 ${best.grade.displayName} (相似度=${String.format("%.2f", best.similarity)})")
        return Pair(best.grade, Pair(best.centerX, best.centerY))
    }

    /** 在截图中匹配队伍房间内的建议等级标识（lv30 / lv40 / lv60 / lv80 / lv90 / lv100 / lv125） */
    fun matchLevelIcon(screen: Bitmap, grade: BountyGrade): GradeMatch? {
        val path = grade.levelIconPath() ?: return null
        val template = levelIconCache.getOrPut(grade) {
            AssetUtil.loadBitmapFromAssets(context, path) ?: run {
                LogUtil.e(TAG, "等级图标模板加载失败: $path")
                return null
            }
        }
        val result = TemplateMatcher.match(screen, template, 0.95f)
        if (result.isMatched) {
            LogUtil.i(TAG, "建议等级图标 ${grade.displayName}(lv${grade.level}): 匹配度 ${String.format("%.2f", result.similarity)}")
            return GradeMatch(grade, result.similarity, result.centerX, result.centerY)
        }
        LogUtil.d(TAG, "等级图标 ${grade.displayName}(lv${grade.level}): 最高 ${String.format("%.2f", result.similarity)} < 0.95")
        return null
    }

    /** 在截图中搜索多个等级的建议等级标识 — 最佳匹配策略 */
    fun matchAnyLevelIcon(screen: Bitmap, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        LogUtil.i(TAG, "matchAnyLevelIcon: 检查 ${grades.joinToString { "${it.displayName}(lv${it.level})" }}")

        val matches = mutableListOf<GradeMatch>()
        for (grade in grades) {
            val match = matchLevelIcon(screen, grade) ?: continue
            matches.add(match)
        }

        if (matches.isEmpty()) {
            LogUtil.w(TAG, "matchAnyLevelIcon: ${grades.size}个等级均未匹配")
            return null
        }

        matches.sortByDescending { it.similarity }
        val best = matches.first()

        if (matches.size > 1) {
            val matchSummary = matches.joinToString { "${it.grade.displayName}=${String.format("%.2f", it.similarity)}" }
            val second = matches[1]
            val gap = best.similarity - second.similarity
            LogUtil.w(TAG, "matchAnyLevelIcon: 多个等级匹配 - $matchSummary，最佳=${best.grade.displayName}，差距=${String.format("%.3f", gap)}")
            if (gap < 0.02f) {
                LogUtil.w(TAG, "matchAnyLevelIcon: 警告! 最佳与次佳差距仅 ${String.format("%.3f", gap)}，匹配可能不可靠")
            }
        }

        LogUtil.i(TAG, "matchAnyLevelIcon → 最佳匹配 ${best.grade.displayName}(lv${best.grade.level}) 相似度=${String.format("%.2f", best.similarity)}")
        return Pair(best.grade, Pair(best.centerX, best.centerY))
    }

    /** 在截图中搜索多个悬赏等级图标 — 最佳匹配策略 */
    fun matchAnyGrade(screen: Bitmap, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        val matches = mutableListOf<GradeMatch>()
        for (grade in grades) {
            val match = matchGradeIcon(screen, grade) ?: continue
            matches.add(match)
        }

        if (matches.isEmpty()) {
            LogUtil.d(TAG, "matchAnyGrade: ${grades.size}个等级均未匹配")
            return null
        }

        matches.sortByDescending { it.similarity }
        val best = matches.first()

        if (matches.size > 1) {
            val matchSummary = matches.joinToString { "${it.grade.displayName}=${String.format("%.2f", it.similarity)}" }
            LogUtil.w(TAG, "matchAnyGrade: 多个等级匹配 - $matchSummary，最佳=${best.grade.displayName}")
        }

        LogUtil.i(TAG, "matchAnyGrade → 选中 ${best.grade.displayName} (相似度=${String.format("%.2f", best.similarity)})")
        return Pair(best.grade, Pair(best.centerX, best.centerY))
    }

    // ── 全量检测顺序（兜底用） ──
    private val detectionOrder = listOf(
        ScreenState.TEAM_INVITATION,
        ScreenState.CONFIRM_BUTTON,
        ScreenState.SETTLEMENT_POPUP,
        ScreenState.DEFEAT_POPUP,
        ScreenState.BATTLE_LOADING,
        ScreenState.WARNING,
        ScreenState.SLIDE_BUTTON,
        ScreenState.LV_ICON,
        ScreenState.JUMP_BUTTON,
        ScreenState.SCROLL_UP,
        ScreenState.ULTIMATE_SKILL,
        ScreenState.WEAPON_SKILL,
        ScreenState.DEFEAT_POPUP,
        ScreenState.READY_BUTTON,
        ScreenState.DAILY_LIMIT,
        ScreenState.EXIT_CONFIRM,
        ScreenState.RECRUIT_INVITE,
        ScreenState.RECRUIT_TAB,
        ScreenState.RECRUIT_LIST_SCREEN,
        ScreenState.OUT_OF_RANGE_RECRUIT,
        ScreenState.CHAT_ICON,
        ScreenState.BACK_BUTTON,
        ScreenState.CHAT_TAB,
    )
}

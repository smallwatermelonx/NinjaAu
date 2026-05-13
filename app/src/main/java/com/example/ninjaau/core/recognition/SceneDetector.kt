package com.example.ninjaau.core.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.util.AssetUtil
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.ScreenState

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

    private val templates: Map<ScreenState, TemplateEntry> = mapOf(
        // ── 大厅 ──
        ScreenState.CHAT_ICON to TemplateEntry("templates/lobby/hall_chat.png", 0.75f),
        // ── 聊天/招募 ──
        ScreenState.RECRUIT_TAB to TemplateEntry("templates/chat/team_recruit.png"),
        ScreenState.RECRUIT_EXCEPTION to TemplateEntry("templates/recruit_list/exception.png", 0.7f),
        // ── 入队 ──
        ScreenState.JOIN_BUTTON to TemplateEntry("templates/recruit_list/join_team.png"),
        ScreenState.TEAM_COMPLETED to TemplateEntry("templates/recruit_list/out_time.png"),
        ScreenState.TEAM_FULL to TemplateEntry("templates/team_room/template.png"),
        ScreenState.READY_BUTTON to TemplateEntry("templates/team_room/prepare.png"),
        ScreenState.EXIT_CONFIRM to TemplateEntry("templates/team_room/confirm.png"),
        // TODO DAILY_LIMIT — 待补充模板 daily_limit.png（"已达上限"弹窗）
        ScreenState.DAILY_LIMIT to TemplateEntry("templates/team_room/daily_limit.png"),
        // ── 战斗 ──
        ScreenState.BATTLE_WARNING to TemplateEntry("templates/fight/warning.png", 0.7f),
        ScreenState.ULTIMATE_SKILL to TemplateEntry("templates/fight/r_ziyuan.png", 0.6f),
        ScreenState.WEAPON_SKILL to TemplateEntry("templates/fight/wopen_shedao.png", 0.6f),
        // TODO DEFEAT_POPUP — 待补充模板 defeat_popup.png（失败弹窗）
        ScreenState.DEFEAT_POPUP to TemplateEntry("templates/fight/defeat_popup.png", 0.6f),
        // ── 结算 ──
        ScreenState.SETTLEMENT_POPUP to TemplateEntry("templates/settlement/black.png", 0.7f),
        ScreenState.CONFIRM_BUTTON to TemplateEntry("templates/settlement/confirm.png"),
        // ── 通用 ──
        ScreenState.BACK_BUTTON to TemplateEntry("templates/other/backward.png"),
        // ── TAB刷新（私聊页签） ──
        ScreenState.CHAT_TAB to TemplateEntry("templates/chat/private_chat.png"),
    )

    companion object {
        /** 节点 1 ~ 2：导航到招募列表 */
        val SCOPE_NAVIGATE = listOf(
            ScreenState.CONFIRM_BUTTON,
            ScreenState.SETTLEMENT_POPUP,
            ScreenState.CHAT_ICON,
            ScreenState.RECRUIT_TAB,
            ScreenState.RECRUIT_LIST,
            ScreenState.JOIN_BUTTON,
            ScreenState.BACK_BUTTON,
        )
        /** 节点 3：组队招募列表（抢悬赏） */
        val SCOPE_RECRUIT = listOf(
            ScreenState.RECRUIT_LIST,
            ScreenState.JOIN_BUTTON,
            ScreenState.RECRUIT_TAB,
            ScreenState.CHAT_ICON,
        )
        /** 节点 4：队伍房间 */
        val SCOPE_TEAM_ROOM = listOf(
            ScreenState.READY_BUTTON,
            ScreenState.TEAM_ROOM,
            ScreenState.TEAM_COMPLETED,
            ScreenState.TEAM_FULL,
            ScreenState.WAITING_SCREEN,
            ScreenState.DAILY_LIMIT,
            ScreenState.EXIT_CONFIRM,
        )
        /** 节点 4（等待战斗开始） */
        val SCOPE_WAIT_BATTLE = listOf(
            ScreenState.BATTLE_WARNING,
            ScreenState.WAITING_SCREEN,
            ScreenState.ULTIMATE_SKILL,
            ScreenState.SETTLEMENT_POPUP,
            ScreenState.CHAT_ICON,
            ScreenState.RECRUIT_TAB,
        )
        /** 节点 5：战斗中 */
        val SCOPE_BATTLE = listOf(
            ScreenState.SETTLEMENT_POPUP,
            ScreenState.CONFIRM_BUTTON,
            ScreenState.DEFEAT_POPUP,
            ScreenState.CHAT_ICON,
            ScreenState.RECRUIT_TAB,
            ScreenState.ULTIMATE_SKILL,
        )
        /** 节点 6：结算 */
        val SCOPE_CLAIM = listOf(
            ScreenState.CONFIRM_BUTTON,
            ScreenState.CHAT_ICON,
            ScreenState.RECRUIT_TAB,
        )
        /** 退出队伍 */
        val SCOPE_EXIT = listOf(
            ScreenState.EXIT_CONFIRM,
            ScreenState.DAILY_LIMIT,
            ScreenState.CHAT_ICON,
            ScreenState.RECRUIT_TAB,
            ScreenState.BACK_BUTTON,
        )
        /** 整体判定（全量兜底） */
        val SCOPE_ALL = ScreenState.values().toList()
    }

    // ── 模板缓存 ──
    private val templateCache = mutableMapOf<String, Bitmap>()

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

    /** 按阶段检测 — 只匹配给定的状态列表 */
    fun detectForPhase(
        screenBitmap: Bitmap,
        states: List<ScreenState>
    ): Pair<ScreenState, Pair<Float, Float>?> {
        for (state in states) {
            val coord = matchTemplate(screenBitmap, state)
            if (coord != null) {
                LogUtil.i(TAG, "✅ 匹配成功: $state")
                return Pair(state, coord)
            }
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
            LogUtil.i(TAG, "✅ $state: 相似度 ${String.format("%.2f", result.similarity)} ≥ ${entry.threshold}")
            return Pair(result.centerX, result.centerY)
        } else {
            LogUtil.d(TAG, "❌ $state: 相似度 ${String.format("%.2f", result.similarity)} < ${entry.threshold} (${entry.path})")
            return null
        }
    }

    /** 在截图中匹配指定悬赏等级的图标（用于节点3扫描） */
    fun matchGradeIcon(screen: Bitmap, grade: BountyGrade): Pair<Float, Float>? {
        val template = AssetUtil.loadBitmapFromAssets(context, grade.gradeIconPath()) ?: return null
        return try {
            val result = TemplateMatcher.match(screen, template, 0.8f)
            if (result.isMatched) Pair(result.centerX, result.centerY) else null
        } finally {
            template.recycle()
        }
    }

    /** 在截图中匹配队伍房间内的建议等级标识（lv30 / lv40 / lv60 / lv125） */
    fun matchLevelIcon(screen: Bitmap, grade: BountyGrade): Pair<Float, Float>? {
        val path = grade.levelIconPath() ?: return null
        val template = AssetUtil.loadBitmapFromAssets(context, path) ?: return null
        return try {
            val result = TemplateMatcher.match(screen, template, 0.8f)
            if (result.isMatched) Pair(result.centerX, result.centerY) else null
        } finally {
            template.recycle()
        }
    }

    /** 在截图中搜索多个等级的建议等级标识 */
    fun matchAnyLevelIcon(screen: Bitmap, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        for (grade in grades) {
            val coord = matchLevelIcon(screen, grade) ?: continue
            return Pair(grade, coord)
        }
        return null
    }

    /** 在截图中搜索多个悬赏等级图标（节点3用） */
    fun matchAnyGrade(screen: Bitmap, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        for (grade in grades) {
            val coord = matchGradeIcon(screen, grade) ?: continue
            return Pair(grade, coord)
        }
        return null
    }

    // ── 全量检测顺序（兜底用） ──
    private val detectionOrder = listOf(
        ScreenState.CONFIRM_BUTTON,
        ScreenState.SETTLEMENT_POPUP,
        ScreenState.DEFEAT_POPUP,
        ScreenState.BATTLE_WARNING,
        ScreenState.WAITING_SCREEN,
        ScreenState.READY_BUTTON,
        ScreenState.DAILY_LIMIT,
        ScreenState.TEAM_COMPLETED,
        ScreenState.TEAM_FULL,
        ScreenState.EXIT_CONFIRM,
        ScreenState.TEAM_ROOM,
        ScreenState.JOIN_BUTTON,
        ScreenState.RECRUIT_TAB,
        ScreenState.RECRUIT_EXCEPTION,
        ScreenState.CHAT_ICON,
        ScreenState.BACK_BUTTON,
        ScreenState.CHAT_TAB,
    )
}

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
 */
class SceneDetector(private val context: Context) {
    private val TAG = "SceneDetector"

    private data class TemplateEntry(val path: String, val threshold: Float = 0.8f)

    private val templates: Map<ScreenState, TemplateEntry> = mapOf(
        // 聊天按钮可见 → 确定用户在大厅（比全屏大厅截图更稳定）
        ScreenState.CHAT_ICON to TemplateEntry("templates/hall/hall_chat.png", 0.75f),
        ScreenState.RECRUIT_TAB to TemplateEntry("templates/chat/team_recruit.png"),
        ScreenState.JOIN_BUTTON to TemplateEntry("templates/bounty/chatbox/join_team.png"),
        ScreenState.TEAM_COMPLETED to TemplateEntry("templates/bounty/chatbox/out_time.png"),
        ScreenState.TEAM_FULL to TemplateEntry("templates/bounty/preparation/full.png"),
        ScreenState.READY_BUTTON to TemplateEntry("templates/bounty/preparation/prepare.png"),
        ScreenState.BATTLE_WARNING to TemplateEntry("templates/fight/warning.png", 0.7f),
        ScreenState.ULTIMATE_SKILL to TemplateEntry("templates/fight/ninjutsu.png", 0.6f),
        ScreenState.WEAPON_SKILL to TemplateEntry("templates/fight/ninjutsu.png", 0.6f),
        ScreenState.SETTLEMENT_POPUP to TemplateEntry("templates/bounty/settlement/congratulations.png"),
        ScreenState.CONFIRM_BUTTON to TemplateEntry("templates/bounty/settlement/confirm.png"),
        ScreenState.EXIT_CONFIRM to TemplateEntry("templates/bounty/preparation/confirm.png"),
        ScreenState.BACK_BUTTON to TemplateEntry("templates/other/backward.png"),
    )

    /**
     * 逆序检测列表：越靠前的状态越"具体/稀有"，优先匹配。
     * 防止前部通用节点（如 LOBBY）误吞后续状态。
     */
    private val detectionOrder = listOf(
        ScreenState.CONFIRM_BUTTON,
        ScreenState.SETTLEMENT_POPUP,
        ScreenState.BATTLE_WARNING,
        ScreenState.WAITING_SCREEN,
        ScreenState.READY_BUTTON,
        ScreenState.TEAM_COMPLETED,
        ScreenState.TEAM_FULL,
        ScreenState.EXIT_CONFIRM,
        ScreenState.TEAM_ROOM,
        ScreenState.JOIN_BUTTON,
        ScreenState.RECRUIT_TAB,
        ScreenState.CHAT_ICON,
        ScreenState.BACK_BUTTON,
    )

    /** 检测当前界面状态（仅返回状态，不返回坐标） */
    fun detect(screenBitmap: Bitmap): ScreenState {
        return detectWithCoord(screenBitmap).first
    }

    /** 检测当前界面状态及匹配坐标 */
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
        val entry = templates[state] ?: return null
        val template = AssetUtil.loadBitmapFromAssets(context, entry.path) ?: return null
        return try {
            val result = TemplateMatcher.match(screen, template, entry.threshold)
            if (result.isMatched) Pair(result.centerX, result.centerY) else null
        } finally {
            template.recycle()
        }
    }

    /** 在截图中匹配指定悬赏等级的图标 */
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

    /** 在截图中搜索多个等级的建议等级标识，返回最先匹配到的等级及坐标 */
    fun matchAnyLevelIcon(screen: Bitmap, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        for (grade in grades) {
            val coord = matchLevelIcon(screen, grade) ?: continue
            return Pair(grade, coord)
        }
        return null
    }

    /** 在截图中搜索多个等级，返回最先匹配到的等级及坐标 */
    fun matchAnyGrade(screen: Bitmap, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
        for (grade in grades) {
            val coord = matchGradeIcon(screen, grade) ?: continue
            return Pair(grade, coord)
        }
        return null
    }
}

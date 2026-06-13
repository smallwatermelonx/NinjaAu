package com.example.ninjaau.core.config

import com.example.ninjaau.model.ScreenState

/**
 * 识别规则配置 — 将硬编码的 ROI/阈值/动作参数提取为数据结构。
 *
 * 每个节点的识别逻辑可声明为一组 RecognitionRule，
 * 按顺序执行直到命中，减少节点代码中的硬编码。
 */
data class RecognitionRule(
    /** 要匹配的模板状态 */
    val template: ScreenState,
    /** 模板匹配阈值（null 使用 SceneDetector 默认值） */
    val threshold: Float? = null,
    /** ROI 裁剪区域（null = 全屏匹配） */
    val roi: RoiRegion? = null,
    /** 匹配成功后的点击偏移（null = 不点击） */
    val clickOffset: Pair<Float, Float>? = null,
    /** 匹配成功后跳转的阶段（null = 由节点自行决定） */
    val nextPhase: com.example.ninjaau.model.GamePhase? = null,
    /** 匹配成功后的日志消息 */
    val logMessage: String? = null,
    /** 匹配成功后的延迟（ms） */
    val delayMs: Long = 0,
)

/**
 * ROI 裁剪区域定义 — 与 SceneDetector 的 crop 方法对应。
 */
enum class RoiRegion {
    /** 全屏 */
    FULL,
    /** 左侧 1/10 */
    LEFT_TENTH,
    /** 左侧第3个 1/10 (x=20%~30%) */
    LEFT_MID_TENTH,
    /** 左侧 1/3 */
    LEFT_THIRD,
    /** 左侧 1/4 */
    LEFT_QUARTER,
    /** 左侧 1/6 */
    LEFT_SIXTH,
    /** 上方 1/10 */
    TOP_TENTH,
    /** 上方 1/8 */
    TOP_LEFT_EIGHTH,
    /** 上方 1/5 */
    TOP_FIFTH,
    /** 上方 1/4 */
    TOP_QUARTER,
    /** 上方中间 1/10 */
    TOP_MIDDLE_TENTH,
    /** 下方 1/5 中间 1/3 */
    BOTTOM_MIDDLE_FIFTH,
    /** 下方 1/4 */
    BOTTOM_QUARTER,
    /** 左下 1/4 */
    BOTTOM_LEFT_QUARTER,
    /** 左下 1/5 */
    BOTTOM_LEFT_FIFTH,
    /** 右下 1/4 */
    BOTTOM_RIGHT_QUARTER,
}

/**
 * 预定义的节点识别规则集。
 * 按节点分组，每个节点包含一组按优先级排序的规则。
 */
object NodeRecognitionPresets {

    val HALL = listOf(
        RecognitionRule(
            template = ScreenState.CHAT_ICON,
            roi = RoiRegion.LEFT_TENTH,
            logMessage = "导航: 聊天按钮，点击进入招募"
        ),
        RecognitionRule(
            template = ScreenState.RECRUIT_TAB,
            roi = RoiRegion.TOP_TENTH,
            logMessage = "导航: 招募页签，点击",
            nextPhase = com.example.ninjaau.model.GamePhase.RECRUIT_LIST
        ),
    )

    val BOUNTY_DETAIL = listOf(
        RecognitionRule(
            template = ScreenState.DAILY_LIMIT,
            roi = RoiRegion.TOP_FIFTH,
            logMessage = "检测到每日上限",
        ),
        RecognitionRule(
            template = ScreenState.READY_BUTTON,
            roi = RoiRegion.BOTTOM_RIGHT_QUARTER,
            logMessage = "检测到准备按钮",
        ),
        RecognitionRule(
            template = ScreenState.BATTLE_LOADING,
            roi = RoiRegion.BOTTOM_LEFT_QUARTER,
            logMessage = "检测到战斗加载",
            nextPhase = com.example.ninjaau.model.GamePhase.BATTLE_LOADING
        ),
        RecognitionRule(
            template = ScreenState.CHAT_ICON,
            logMessage = "已回到大厅",
            nextPhase = com.example.ninjaau.model.GamePhase.LOBBY
        ),
    )

    val BATTLE_LOADING = listOf(
        RecognitionRule(
            template = ScreenState.BATTLE_LOADING,
            logMessage = "战斗加载中...",
        ),
    )

    val SETTLEMENT = listOf(
        RecognitionRule(
            template = ScreenState.SETTLEMENT_POPUP,
            roi = RoiRegion.BOTTOM_MIDDLE_FIFTH,
            logMessage = "点击空白处关闭结算弹窗",
        ),
        RecognitionRule(
            template = ScreenState.CONFIRM_BUTTON,
            roi = RoiRegion.BOTTOM_MIDDLE_FIFTH,
            logMessage = "点击确认领奖",
        ),
    )
}

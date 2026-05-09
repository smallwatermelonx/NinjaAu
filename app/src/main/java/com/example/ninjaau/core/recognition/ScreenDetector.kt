package com.example.ninjaau.core.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.util.AssetUtil
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.model.ScreenState

class ScreenDetector(private val context: Context) {
    private val TAG = "ScreenDetector"

    fun detect(screenBitmap: Bitmap): DetectionResult {
        return try {
            // 逆序检测：从后往前，避免前部节点误匹配
            when {
                matchScreen(screenBitmap, ScreenState.BACK_BUTTON) != null ->
                    DetectionResult(ScreenState.BACK_BUTTON, matchScreen(screenBitmap, ScreenState.BACK_BUTTON))
                matchScreen(screenBitmap, ScreenState.CONFIRM_BTN) != null ->
                    DetectionResult(ScreenState.CONFIRM_BTN, matchScreen(screenBitmap, ScreenState.CONFIRM_BTN))
                matchScreen(screenBitmap, ScreenState.REWARD_POPUP) != null ->
                    DetectionResult(ScreenState.REWARD_POPUP, matchScreen(screenBitmap, ScreenState.REWARD_POPUP))
                matchScreen(screenBitmap, ScreenState.BATTLE_WARNING) != null ->
                    DetectionResult(ScreenState.BATTLE_WARNING, matchScreen(screenBitmap, ScreenState.BATTLE_WARNING))
                matchScreen(screenBitmap, ScreenState.SLIDE_BUTTON) != null ->
                    DetectionResult(ScreenState.SLIDE_BUTTON, matchScreen(screenBitmap, ScreenState.SLIDE_BUTTON))
                matchScreen(screenBitmap, ScreenState.READY_BTN) != null ->
                    DetectionResult(ScreenState.READY_BTN, matchScreen(screenBitmap, ScreenState.READY_BTN))
                matchScreen(screenBitmap, ScreenState.JOIN_TEAM) != null ->
                    DetectionResult(ScreenState.JOIN_TEAM, matchScreen(screenBitmap, ScreenState.JOIN_TEAM))
                matchScreen(screenBitmap, ScreenState.RECRUIT_TAB) != null ->
                    DetectionResult(ScreenState.RECRUIT_TAB, matchScreen(screenBitmap, ScreenState.RECRUIT_TAB))
                matchScreen(screenBitmap, ScreenState.HALL_CHAT) != null ->
                    DetectionResult(ScreenState.HALL_CHAT, matchScreen(screenBitmap, ScreenState.HALL_CHAT))
                else -> DetectionResult(ScreenState.UNKNOWN, null)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "界面检测异常: ${e.message}", e)
            DetectionResult(ScreenState.UNKNOWN, null)
        }
    }

    fun matchScreen(screen: Bitmap, state: ScreenState): Pair<Float, Float>? {
        if (state == ScreenState.UNKNOWN) return null
        val template = AssetUtil.loadBitmapFromAssets(context, state.templateConfig.getAssetsPath())
            ?: return null
        return try {
            val result = TemplateMatcher.match(screen, template, state.templateConfig.threshold)
            if (result.isMatched) Pair(result.centerX, result.centerY) else null
        } finally {
            template.recycle()
        }
    }

    data class DetectionResult(
        val state: ScreenState,
        val coord: Pair<Float, Float>?
    )
}

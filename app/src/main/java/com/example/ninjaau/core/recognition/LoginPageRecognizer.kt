package com.example.ninjaau.core.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.uimap.UIMap
import com.example.ninjaau.core.uimap.TemplateConfig
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.TemplateScreenshotTool
import com.example.ninjaau.core.util.PermissionManager
import android.view.WindowManager
import android.media.projection.MediaProjectionManager

/**
 * 登录页识别器：核心逻辑→3个图标匹配≥2个则判定为登录页，返回点击坐标
 */
class LoginPageRecognizer(private val context: Context) {
    private val TAG = "LoginPageRecognizer"

    /**
     * 核心判定：是否是登录页
     */
    fun checkLoginPage(): Pair<Boolean, Pair<Float, Float>?> {
        // 1. 从权限管理器获取复用的 MediaProjection 授权数据
        val projData = PermissionManager.mProjectionIntent ?: return Pair(false, null)
        val resultCode = PermissionManager.mResultCode
        if (resultCode == -1) return Pair(false, null)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // 2. 创建临时 MediaProjection（利用已有的授权数据，不会弹窗）
        val mediaProjection = mpm.getMediaProjection(resultCode, projData) ?: return Pair(false, null)
        
        // 3. 初始化截图工具
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)

        // 4. 获取全屏截图
        val fullScreenBitmap = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "登录页识别失败：截图为空")
            mediaProjection.stop()
            return Pair(false, null)
        }

        // 5. 匹配模板
        val tapResult = matchTemplate(UIMap.Login.TAP_TO_START, fullScreenBitmap)
        val accountResult = matchTemplate(UIMap.Login.ACCOUNT_ICON, fullScreenBitmap)
        val moreResult = matchTemplate(UIMap.Login.MORE_ICON, fullScreenBitmap)

        // 6. 判定逻辑
        val matchCount = listOf(tapResult, accountResult, moreResult).count { it.isMatched }
        val isLoginPage = matchCount >= 2

        val clickCoord = if (isLoginPage && tapResult.isMatched) {
            Pair(tapResult.centerX, tapResult.centerY)
        } else null

        // 7. 释放资源
        fullScreenBitmap.recycle()
        mediaProjection.stop() // 必须停止，释放系统截图资源
        
        LogUtil.d(TAG, "登录页识别：匹配数=$matchCount/3 → 是否登录页=$isLoginPage")
        return Pair(isLoginPage, clickCoord)
    }

    private fun matchTemplate(config: TemplateConfig, screenBitmap: Bitmap): OpenCVTemplateMatcher.MatchResult {
        val cropBitmap = OpenCVTemplateMatcher.cropScreenByArea(screenBitmap, config.recognizeArea)
        val templatePath = config.getAssetsPath()
        val result = OpenCVTemplateMatcher.matchTemplate(cropBitmap, templatePath, config.threshold)
        cropBitmap.recycle()
        return result
    }
}

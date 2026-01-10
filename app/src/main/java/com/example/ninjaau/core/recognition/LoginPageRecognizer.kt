package com.example.ninjaau.core.recognition

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import com.example.ninjaau.core.uimap.UIMap
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.TemplateScreenshotTool
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.core.util.AssetUtil

/**
 * 登录页识别器：全局识别版 (复用全局 MediaProjection)
 */
class LoginPageRecognizer(private val context: Context) {
    private val TAG = "LoginPageRecognizer"

    /**
     * 核心判定：是否是登录页
     */
    fun checkLoginPage(): Pair<Boolean, Pair<Float, Float>?> {
        // 1. 获取全局复用的 MediaProjection（不再自己创建/销毁）
        val mediaProjection = PermissionManager.mediaProjection ?: run {
            LogUtil.e(TAG, "全局 MediaProjection 为空，请先初始化")
            return Pair(false, null)
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 2. 初始化截图工具
        val screenshotTool = TemplateScreenshotTool(context, mediaProjection, wm)

        // 3. 获取全屏截图
        val fullScreen = screenshotTool.captureFullScreenBitmap() ?: run {
            LogUtil.e(TAG, "截图失败")
            return Pair(false, null)
        }

        try {
            // 4. 加载静态模板
            val tapTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Login.TAP_TO_START.getAssetsPath())
            val accountTemplate = AssetUtil.loadBitmapFromAssets(context, UIMap.Login.ACCOUNT_ICON.getAssetsPath())

            if (tapTemplate == null || accountTemplate == null) {
                LogUtil.e(TAG, "登录模板加载失败")
                return Pair(false, null)
            }

            // 5. 执行全局匹配
            val tapResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, tapTemplate, UIMap.Login.TAP_TO_START.threshold)
            val accountResult = OpenCVTemplateMatcher.matchTemplate(fullScreen, accountTemplate, UIMap.Login.ACCOUNT_ICON.threshold)

            // 6. 判定逻辑
            val isLoginPage = tapResult.isMatched || accountResult.isMatched
            
            val clickCoord = if (tapResult.isMatched) {
                Pair(tapResult.centerX, tapResult.centerY)
            } else if (accountResult.isMatched) {
                Pair(fullScreen.width * 0.5f, fullScreen.height * 0.8f)
            } else null

            tapTemplate.recycle()
            accountTemplate.recycle()

            LogUtil.d(TAG, "识别流：tapMatch=${tapResult.isMatched}, accountMatch=${accountResult.isMatched} → 判定登录页: $isLoginPage")
            return Pair(isLoginPage, clickCoord)

        } finally {
            // 核心：只释放截图 Bitmap，绝对不调用 mediaProjection.stop()！
            fullScreen.recycle()
        }
    }
}

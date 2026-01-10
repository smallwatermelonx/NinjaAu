package com.example.ninjaau.core.recognition

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.ninjaau.core.util.FileUtil
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.OpenCVUtil
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * OpenCV找图匹配器：核心算法实现
 */
object OpenCVTemplateMatcher {
    private const val TAG = "OpenCVTemplateMatcher"

    /**
     * 核心找图方法
     * @param screenBitmap 屏幕截图的Bitmap
     * @param templateBitmap 模板图的Bitmap
     * @param threshold 相似度阈值（0-1）
     * @return 匹配结果：包含中心坐标、相似度
     */
    fun matchTemplate(
        screenBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Float
    ): MatchResult {
        // 1. 初始化检查
        if (!OpenCVUtil.initOpenCV()) {
            LogUtil.e(TAG, "OpenCV初始化失败")
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }

        // 2. Bitmap转Mat
        val screenMat = OpenCVUtil.bitmapToMat(screenBitmap)
        val templateMat = OpenCVUtil.bitmapToMat(templateBitmap)

        // 3. 检查尺寸
        if (templateMat.cols() > screenMat.cols() || templateMat.rows() > screenMat.rows()) {
            LogUtil.e(TAG, "模板图尺寸大于屏幕截图，匹配失败")
            screenMat.release()
            templateMat.release()
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }

        // 4. 执行匹配
        val resultMat = Mat()
        Imgproc.matchTemplate(screenMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

        // 5. 查找结果
        val minMaxLoc = Core.minMaxLoc(resultMat)
        val maxSimilarity = minMaxLoc.maxVal.toFloat()
        val matchPoint = minMaxLoc.maxLoc

        val isMatched = maxSimilarity >= threshold
        
        // 6. 计算坐标（左上角 + 中心点）
        val matchX = matchPoint.x.toFloat()
        val matchY = matchPoint.y.toFloat()
        val centerX = matchX + templateBitmap.width / 2f
        val centerY = matchY + templateBitmap.height / 2f

        // 7. 释放资源
        screenMat.release()
        templateMat.release()
        resultMat.release()

        LogUtil.d(TAG, "匹配结果：相似度=$maxSimilarity | 匹配=$isMatched")
        return MatchResult(isMatched, maxSimilarity, matchX, matchY, centerX, centerY)
    }

    /**
     * 重载方法：支持路径加载
     */
    fun matchTemplate(
        screenBitmap: Bitmap,
        templatePath: String,
        threshold: Float
    ): MatchResult {
        val templateBitmap = FileUtil.bitmapFromPath(templatePath) ?: run {
            LogUtil.e(TAG, "模板文件读取失败：$templatePath")
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }
        return matchTemplate(screenBitmap, templateBitmap, threshold)
    }

    /**
     * 匹配结果数据类
     */
    data class MatchResult(
        val isMatched: Boolean,
        val similarity: Float,
        val matchX: Float,      // 左上角X
        val matchY: Float,      // 左上角Y
        val centerX: Float,     // 中心点X（点击推荐坐标）
        val centerY: Float      // 中心点Y（点击推荐坐标）
    )

    /**
     * 辅助方法：根据识别区域裁剪
     */
    fun cropScreenByArea(screenBitmap: Bitmap, area: RectF): Bitmap {
        val left = (area.left * screenBitmap.width).toInt()
        val top = (area.top * screenBitmap.height).toInt()
        val width = (area.width() * screenBitmap.width).toInt()
        val height = (area.height() * screenBitmap.height).toInt()
        return Bitmap.createBitmap(screenBitmap, left, top, width, height)
    }
}

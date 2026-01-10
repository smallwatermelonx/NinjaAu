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
 * 修正版 OpenCV 找图匹配器：真的用到 OpenCVUtil，且内存释放更严谨
 */
object OpenCVTemplateMatcher {
    private const val TAG = "OpenCVTemplateMatcher"

    /**
     * 核心找图方法（真正走 OpenCV 匹配逻辑）
     */
    fun matchTemplate(
        screenBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Float
    ): MatchResult {
        // 1. 先检查 OpenCV 是否初始化成功
        if (!OpenCVUtil.initOpenCV()) {
            LogUtil.e(TAG, "OpenCV 未初始化，匹配失败")
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }

        // 2. 定义所有需要用到的 Mat
        var screenMat: Mat? = null
        var templateMat: Mat? = null
        var resultMat: Mat? = null

        try {
            // 3. Bitmap 转 Mat
            screenMat = OpenCVUtil.bitmapToMat(screenBitmap)
            templateMat = OpenCVUtil.bitmapToMat(templateBitmap)

            // 4. 尺寸检查：模板不能比截图大
            if (templateMat.cols() > screenMat.cols() || templateMat.rows() > screenMat.rows()) {
                LogUtil.e(TAG, "模板尺寸 > 截图尺寸，匹配失败")
                return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
            }

            // 5. 执行 OpenCV 模板匹配
            resultMat = Mat()
            // TM_CCOEFF_NORMED 返回相似度 0-1
            Imgproc.matchTemplate(screenMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

            // 6. 提取匹配结果
            val minMaxLoc = Core.minMaxLoc(resultMat)
            val maxSimilarity = minMaxLoc.maxVal.toFloat()
            val isMatched = maxSimilarity >= threshold

            // 7. 计算坐标（左上角 + 中心点）
            val matchX = minMaxLoc.maxLoc.x.toFloat()
            val matchY = minMaxLoc.maxLoc.y.toFloat()
            val centerX = matchX + templateBitmap.width / 2f
            val centerY = matchY + templateBitmap.height / 2f

            LogUtil.d(TAG, "OpenCV 匹配结果：相似度=$maxSimilarity | 阈值=$threshold | 匹配=$isMatched")
            return MatchResult(isMatched, maxSimilarity, matchX, matchY, centerX, centerY)

        } catch (e: Exception) {
            LogUtil.e(TAG, "OpenCV 匹配异常：${e.message}", e)
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        } finally {
            // 8. 必须释放所有 Mat
            OpenCVUtil.releaseMats(screenMat, templateMat, resultMat)
        }
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
        val result = matchTemplate(screenBitmap, templateBitmap, threshold)
        templateBitmap.recycle()
        return result
    }

    /**
     * 裁剪识别区域
     */
    fun cropScreenByArea(screenBitmap: Bitmap, area: RectF): Bitmap {
        val left = (area.left * screenBitmap.width).toInt()
        val top = (area.top * screenBitmap.height).toInt()
        val width = (area.width() * screenBitmap.width).toInt()
        val height = (area.height() * screenBitmap.height).toInt()
        return Bitmap.createBitmap(screenBitmap, left, top, width, height)
    }

    /**
     * 匹配结果数据类
     */
    data class MatchResult(
        val isMatched: Boolean,
        val similarity: Float,
        val matchX: Float,      
        val matchY: Float,      
        val centerX: Float,     
        val centerY: Float      
    )
}

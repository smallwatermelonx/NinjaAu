package com.example.ninjaau.core.recognition

import android.graphics.Bitmap
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.OpenCVUtil
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object TemplateMatcher {
    private const val TAG = "TemplateMatcher"

    fun match(screenBitmap: Bitmap, templateBitmap: Bitmap, threshold: Float): MatchResult {
        if (!OpenCVUtil.initOpenCV()) {
            LogUtil.e(TAG, "OpenCV 未初始化")
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }
        var screenMat: Mat? = null
        var templateMat: Mat? = null
        var resultMat: Mat? = null
        try {
            screenMat = OpenCVUtil.bitmapToMat(screenBitmap)
            templateMat = OpenCVUtil.bitmapToMat(templateBitmap)
            if (templateMat.cols() > screenMat.cols() || templateMat.rows() > screenMat.rows()) {
                return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
            }
            resultMat = Mat()
            Imgproc.matchTemplate(screenMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
            val minMaxLoc = Core.minMaxLoc(resultMat)
            val similarity = minMaxLoc.maxVal.toFloat()
            val isMatched = similarity >= threshold
            val matchX = minMaxLoc.maxLoc.x.toFloat()
            val matchY = minMaxLoc.maxLoc.y.toFloat()
            return MatchResult(
                isMatched, similarity, matchX, matchY,
                matchX + templateBitmap.width / 2f,
                matchY + templateBitmap.height / 2f
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "模板匹配异常: ${e.message}", e)
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        } finally {
            OpenCVUtil.releaseMats(screenMat, templateMat, resultMat)
        }
    }

    data class MatchResult(
        val isMatched: Boolean,
        val similarity: Float,
        val matchX: Float,
        val matchY: Float,
        val centerX: Float,
        val centerY: Float
    )
}

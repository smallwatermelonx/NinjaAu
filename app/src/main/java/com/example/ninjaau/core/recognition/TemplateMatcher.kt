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

    /**
     * 使用预转换的 screen Mat 进行匹配 — 避免重复 Bitmap→Mat 转换。
     * 调用方负责 screenMat 的生命周期（不要在此方法内 release）。
     */
    fun matchWithMat(screenMat: Mat, templateBitmap: Bitmap, threshold: Float): MatchResult {
        if (!OpenCVUtil.initOpenCV()) {
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }
        var templateMat: Mat? = null
        var resultMat: Mat? = null
        try {
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
            OpenCVUtil.releaseMats(templateMat, resultMat)
        }
    }

    /**
     * Mat 对 Mat 匹配 — 完全避免 Bitmap→Mat 转换。
     * 调用方负责 screenMat 和 templateMat 的生命周期。
     */
    fun matchMatWithMat(screenMat: Mat, templateMat: Mat, threshold: Float, templateWidth: Int, templateHeight: Int): MatchResult {
        if (!OpenCVUtil.initOpenCV()) {
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        }
        var resultMat: Mat? = null
        try {
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
                matchX + templateWidth / 2f,
                matchY + templateHeight / 2f
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "模板匹配异常: ${e.message}", e)
            return MatchResult(false, 0f, 0f, 0f, 0f, 0f)
        } finally {
            resultMat?.release()
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

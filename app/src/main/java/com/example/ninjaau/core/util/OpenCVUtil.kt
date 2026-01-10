package com.example.ninjaau.core.util

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * 优化版OpenCV工具：复用Mat对象，避免高频创建/销毁
 */
object OpenCVUtil {
    // 预创建Mat对象（复用，减少GC）
    private val screenMat = Mat()
    private val templateMat = Mat()
    private val resultMat = Mat()
    private val tmpMat = Mat()

    // 初始化（App启动时调用一次）
    fun initOpenCV(): Boolean {
        return OpenCVLoader.initDebug()
    }

    /**
     * Bitmap转Mat（复用预创建的Mat，避免高频new）
     */
    fun bitmapToMat(bitmap: Bitmap, targetMat: Mat = screenMat): Mat {
        synchronized(this) { // 线程安全（高频检测可能多线程）
            org.opencv.android.Utils.bitmapToMat(bitmap, tmpMat)
            Imgproc.cvtColor(tmpMat, targetMat, Imgproc.COLOR_RGBA2BGR)
            tmpMat.setTo(org.opencv.core.Scalar.all(0.0)) // 清空临时Mat
            return targetMat
        }
    }

    /**
     * 释放所有复用的Mat（退出时调用）
     */
    fun releaseAllMat() {
        synchronized(this) {
            screenMat.release()
            templateMat.release()
            resultMat.release()
            tmpMat.release()
        }
    }
}
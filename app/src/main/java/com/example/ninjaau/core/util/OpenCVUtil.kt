package com.example.ninjaau.core.util

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * OpenCV核心工具类：实现图像格式转换与初始化
 */
object OpenCVUtil {
    private const val TAG = "OpenCVUtil"
    private var isLoaded = false

    fun initOpenCV(): Boolean {
        if (isLoaded) return true
        return try {
            // quickbirdstudios AAR 已打包 opencv_java4 原生库，直接加载
            System.loadLibrary("opencv_java4")
            isLoaded = true
            LogUtil.i(TAG, "OpenCV 初始化成功")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "OpenCV 初始化异常: ${e.message}", e)
            false
        }
    }

    /**
     * Bitmap 转 OpenCV Mat (RGBA -> BGR)
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        // 1. 将 Bitmap 数据拷贝到 Mat (默认 RGBA)
        Utils.bitmapToMat(bitmap, mat)
        // 2. 转换为 OpenCV 算法首选的 BGR 格式
        val bgrMat = Mat()
        Imgproc.cvtColor(mat, bgrMat, Imgproc.COLOR_RGBA2BGR)
        mat.release()
        return bgrMat
    }

    /**
     * Mat 转 Bitmap (BGR -> RGBA) 用于调试显示
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val rgbaMat = Mat()
        Imgproc.cvtColor(mat, rgbaMat, Imgproc.COLOR_BGR2RGBA)
        val bitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbaMat, bitmap)
        rgbaMat.release()
        return bitmap
    }

    /**
     * 安全释放多个 Mat 对象
     */
    fun releaseMats(vararg mats: Mat?) {
        mats.forEach { it?.release() }
    }
}

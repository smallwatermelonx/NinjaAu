package com.example.ninjaau.core.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager

class ScreenCapture private constructor(context: Context) {

    private val screenWidth: Int
    private val screenHeight: Int
    private val densityDpi: Int

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var isInitialized = false
    private var currentProjection: MediaProjection? = null
    private val initLock = Any()

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        densityDpi = metrics.densityDpi
    }

    companion object {
        @Volatile
        private var INSTANCE: ScreenCapture? = null

        fun getInstance(context: Context): ScreenCapture {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCapture(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * 确保截图资源就绪。
     * 每次 capture() 前自动调用，检测投影变化并重建资源。
     */
    private fun ensureReady(): Boolean {
        // 快速路径：已初始化且投影未变，跳过锁
        if (isInitialized && PermissionManager.mediaProjection === currentProjection) return true
        synchronized(initLock) {
            val mp = PermissionManager.mediaProjection

            // 投影不存在 → 释放旧资源，等待
            if (mp == null) {
                if (isInitialized) {
                    LogUtil.w("ScreenCapture", "MediaProjection 已失效，释放资源")
                    releaseResources()
                }
                return false
            }

            // 投影对象变了（stop→restart） → 释放旧资源，重建
            if (isInitialized && mp !== currentProjection) {
                LogUtil.i("ScreenCapture", "MediaProjection 已更换，重新初始化")
                releaseResources()
            }

            if (isInitialized) return true

            // 初始化
            currentProjection = mp
            try {
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
                virtualDisplay = mp.createVirtualDisplay(
                    "ScreenCapture", screenWidth, screenHeight, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, null
                )
                isInitialized = true
                LogUtil.i("ScreenCapture", "截图资源初始化成功")
                return true
            } catch (e: Exception) {
                LogUtil.e("ScreenCapture", "初始化截图资源失败: ${e.message}", e)
                releaseResources()
                return false
            }
        }
    }

    private fun releaseResources() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
        isInitialized = false
        currentProjection = null
    }

    fun capture(): Bitmap? {
        if (!ensureReady()) return null
        return try {
            val image = imageReader!!.acquireLatestImage() ?: return null
            val bitmap = imageToBitmap(image)
            image.close()
            bitmap
        } catch (e: Exception) {
            LogUtil.e("ScreenCapture", "截图失败: ${e.message}", e)
            null
        }
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        val result = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        bitmap.recycle()
        return result
    }
}

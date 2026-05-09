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
    private val mediaProjection: MediaProjection? = PermissionManager.mediaProjection

    @Volatile
    private var isInitialized = false
    private val initLock = Any()

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        densityDpi = metrics.densityDpi
        initResources()
    }

    companion object {
        @Volatile
        private var INSTANCE: ScreenCapture? = null

        fun getInstance(context: Context): ScreenCapture {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCapture(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun release() {
            synchronized(this) {
                INSTANCE?.virtualDisplay?.release()
                INSTANCE?.imageReader?.close()
                INSTANCE = null
                LogUtil.i("ScreenCapture", "截图资源已释放")
            }
        }
    }

    private fun initResources() {
        synchronized(initLock) {
            if (isInitialized || mediaProjection == null) return
            try {
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture", screenWidth, screenHeight, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, null
                )
                isInitialized = true
                LogUtil.i("ScreenCapture", "截图资源初始化成功")
            } catch (e: Exception) {
                LogUtil.e("ScreenCapture", "初始化截图资源失败", e)
                releaseResources()
            }
        }
    }

    private fun releaseResources() {
        synchronized(initLock) {
            virtualDisplay?.release()
            imageReader?.close()
            virtualDisplay = null
            imageReader = null
            isInitialized = false
        }
    }

    fun capture(): Bitmap? {
        if (!isInitialized || imageReader == null) {
            LogUtil.w("ScreenCapture", "截图资源未初始化")
            return null
        }
        return try {
            val image = imageReader!!.acquireLatestImage() ?: return null
            val bitmap = imageToBitmap(image)
            image.close()
            bitmap
        } catch (e: Exception) {
            LogUtil.e("ScreenCapture", "截图失败", e)
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
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}

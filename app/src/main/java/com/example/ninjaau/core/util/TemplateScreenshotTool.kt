package com.example.ninjaau.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 模板截图与加载工具
 */
class TemplateScreenshotTool(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val windowManager: WindowManager
) {
    private val templateDir = File(context.getExternalFilesDir(null), "templates")
    
    private val MAX_RETRY_COUNT = 3
    private val RETRY_INTERVAL = 200L
    private val RENDER_DELAY = 300L

    init {
        if (!templateDir.exists()) {
            templateDir.mkdirs()
        }
    }

    /**
     * 核心修复：执行截图并保存到文件，返回绝对路径
     */
    fun captureFullScreen(templateName: String): String {
        val bitmap = captureFullScreenBitmap() ?: return "截图失败:无法获取Bitmap"
        val templateFile = File(templateDir, "${templateName}.png")
        return try {
            saveBitmap(bitmap, templateFile)
            bitmap.recycle() // 及时回收
            templateFile.absolutePath
        } catch (e: Exception) {
            "保存失败: ${e.message}"
        }
    }

    /**
     * 从 Assets 目录加载 Bitmap
     */
    fun loadBitmapFromAssets(path: String): Bitmap? {
        return try {
            context.assets.open(path).use { 
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            LogUtil.e("TemplateScreenshotTool", "从Assets加载图片失败: $path, ${e.message}")
            null
        }
    }

    /**
     * 获取全图截图 Bitmap
     */
    fun captureFullScreenBitmap(): Bitmap? {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "Screenshot",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        try { Thread.sleep(RENDER_DELAY) } catch (e: Exception) {}

        var image: Image? = null
        var retryCount = 0
        while (image == null && retryCount < MAX_RETRY_COUNT) {
            image = imageReader.acquireLatestImage()
            if (image == null) {
                retryCount++
                try { Thread.sleep(RETRY_INTERVAL) } catch (e: Exception) {}
            }
        }

        if (image == null) {
            virtualDisplay.release()
            imageReader.close()
            return null
        }

        val bitmap = imageToBitmap(image)
        image.close()
        virtualDisplay.release()
        imageReader.close()
        return bitmap
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        bitmap.recycle()
        return finalBitmap
    }

    fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
    }
}

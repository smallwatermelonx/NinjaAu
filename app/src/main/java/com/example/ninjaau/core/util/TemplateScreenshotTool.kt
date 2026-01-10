package com.example.ninjaau.core.util

import android.content.Context
import android.graphics.Bitmap
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
 * 模板截图工具（修复Bitmap获取失败问题）
 */
class TemplateScreenshotTool(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val windowManager: WindowManager
) {
    // 使用应用私有目录，避免 Android 11+ 的分区存储权限问题
    private val templateDir = File(context.getExternalFilesDir(null), "templates")
    
    // 截图重试次数和间隔（核心优化）
    private val MAX_RETRY_COUNT = 3
    private val RETRY_INTERVAL = 200L // 每次重试间隔200ms
    private val RENDER_DELAY = 300L // 虚拟显示渲染延迟（延长到300ms）

    init {
        if (!templateDir.exists()) {
            templateDir.mkdirs()
        }
    }

    /**
     * 获取全图截图 Bitmap（添加重试机制，核心修复）
     */
    fun captureFullScreenBitmap(): Bitmap? {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // 核心优化1：增加ImageReader缓冲区数量（从1改为2），防止获取图片时缓冲区被占用
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

        // 核心优化2：先等待虚拟显示渲染完成
        try {
            Thread.sleep(RENDER_DELAY)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        var image: Image? = null
        var retryCount = 0

        // 核心优化3：添加重试机制，确保在渲染完成后能拿到帧数据
        while (image == null && retryCount < MAX_RETRY_COUNT) {
            image = imageReader.acquireLatestImage()
            if (image == null) {
                retryCount++
                try {
                    Thread.sleep(RETRY_INTERVAL)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }

        // 依然获取失败，释放资源并返回null
        if (image == null) {
            virtualDisplay.release()
            imageReader.close()
            return null
        }

        // 生成Bitmap并执行裁剪填充逻辑
        val bitmap = imageToBitmap(image)
        
        // 释放资源（先关image，再关display，最后关reader）
        image.close()
        virtualDisplay.release()
        imageReader.close()

        return bitmap
    }

    /**
     * 保存全图并返回路径
     */
    fun captureFullScreen(templateName: String): String {
        val bitmap = captureFullScreenBitmap() ?: return "截图失败:无法获取Bitmap"
        val templateFile = File(templateDir, "${templateName}.png")
        return try {
            saveBitmap(bitmap, templateFile)
            templateFile.absolutePath
        } catch (e: Exception) {
            "保存失败: ${e.message}"
        }
    }

    /**
     * 区域截图
     */
    fun captureArea(templateName: String, rect: Rect): String {
        val fullBitmap = captureFullScreenBitmap() ?: return "截图失败:无法获取Bitmap"
        
        val left = rect.left.coerceAtLeast(0)
        val top = rect.top.coerceAtLeast(0)
        val width = (rect.right - rect.left).coerceAtMost(fullBitmap.width - left)
        val height = (rect.bottom - rect.top).coerceAtMost(fullBitmap.height - top)

        // 边界校验，避免创建Bitmap失败
        if (width <= 0 || height <= 0) {
            fullBitmap.recycle()
            return "区域截图失败:无效的区域尺寸"
        }

        return try {
            val areaBitmap = Bitmap.createBitmap(fullBitmap, left, top, width, height)
            val templateFile = File(templateDir, "${templateName}.png")
            saveBitmap(areaBitmap, templateFile)
            
            // 回收临时Bitmap，避免内存泄漏
            fullBitmap.recycle()
            areaBitmap.recycle()
            
            templateFile.absolutePath
        } catch (e: Exception) {
            fullBitmap.recycle()
            "区域截图失败: ${e.message}"
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        // 创建临时Bitmap来承载缓冲区数据
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // 核心修复：裁剪掉由于内存对齐产生的多余padding，返回精准的屏幕图像
        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        
        // 回收包含padding的中间对象
        bitmap.recycle()
        return finalBitmap
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
    }
}

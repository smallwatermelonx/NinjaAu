package com.example.ninjaau.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 文件操作工具类：负责图片的读取与保存
 */
object FileUtil {

    /**
     * 从文件路径读取 Bitmap
     */
    fun bitmapFromPath(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存 Bitmap 到指定路径
     */
    fun saveBitmap(bitmap: Bitmap, path: String): Boolean {
        val file = File(path)
        file.parentFile?.mkdirs()
        return try {
            file.outputStream().use { 
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

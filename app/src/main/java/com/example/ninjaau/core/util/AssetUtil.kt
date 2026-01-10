package com.example.ninjaau.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 资产工具类：从 assets 目录无损读取图片
 */
object AssetUtil {
    private const val TAG = "AssetUtil"

    fun loadBitmapFromAssets(context: Context, path: String): Bitmap? {
        return try {
            context.assets.open(path).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "读取Assets图片失败: $path | ${e.message}")
            null
        }
    }
}

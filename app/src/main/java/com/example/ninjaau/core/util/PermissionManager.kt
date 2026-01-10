package com.example.ninjaau.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService

/**
 * 权限管理单例：全局保存MediaProjection授权数据与实例复用
 */
object PermissionManager {
    // 授权原始数据
    var mResultCode: Int = -1
    var mProjectionIntent: Intent? = null

    // 核心改进：缓存全局唯一的 MediaProjection 实例
    private var _mediaProjection: MediaProjection? = null
    val mediaProjection: MediaProjection? get() = _mediaProjection

    /**
     * 判断是否已有有效的截图授权数据
     */
    fun hasProjectionPermission(): Boolean {
        return mResultCode == Activity.RESULT_OK && mProjectionIntent != null
    }

    /**
     * 初始化 MediaProjection（只创建一次，全程复用）
     */
    fun initMediaProjection(context: Context): Boolean {
        if (_mediaProjection != null) return true
        if (!hasProjectionPermission()) {
            LogUtil.e("PermissionManager", "尚未获得授权数据，无法初始化")
            return false
        }

        return try {
            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            _mediaProjection = mpm.getMediaProjection(mResultCode, mProjectionIntent!!)
            _mediaProjection != null
        } catch (e: Exception) {
            LogUtil.e("PermissionManager", "创建 MediaProjection 失败: ${e.message}")
            false
        }
    }

    /**
     * 释放资源（仅在停止脚本或 App 退出时调用）
     */
    fun releaseMediaProjection() {
        _mediaProjection?.stop()
        _mediaProjection = null
        LogUtil.i("PermissionManager", "MediaProjection 已正式释放")
    }

    /**
     * 检查无障碍服务是否开启
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/${NinjaAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(serviceName)
    }
}

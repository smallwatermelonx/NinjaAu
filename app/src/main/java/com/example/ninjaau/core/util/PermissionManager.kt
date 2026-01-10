package com.example.ninjaau.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService

/**
 * 权限管理单例：全局保存MediaProjection授权数据
 */
object PermissionManager {
    // 全局保存授权结果和Intent
    var mResultCode: Int = -1
    var mProjectionIntent: Intent? = null

    /**
     * 判断是否已有有效的截图授权
     */
    fun hasProjectionPermission(): Boolean {
        return mResultCode == Activity.RESULT_OK && mProjectionIntent != null
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

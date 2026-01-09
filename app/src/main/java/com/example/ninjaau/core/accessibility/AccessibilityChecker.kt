package com.example.ninjaau.core.accessibility

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils

/**
 * 无障碍服务状态检测工具类
 */
object AccessibilityChecker {
    /**
     * 检测指定无障碍服务是否开启
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        // 1. 获取系统已开启的无障碍服务列表
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // 2. 拼接当前应用的无障碍服务完整名称（包名 + 服务类名）
        val packageName = context.packageName
        val serviceName = "$packageName/${serviceClass.name}"

        // 3. 判断当前服务是否在开启列表中
        return enabledServices.split(":").any {
            TextUtils.equals(it.trim(), serviceName)
        }
    }

    /**
     * 检测忍三无障碍服务是否开启（简化调用）
     */
    fun isNinjaAccessibilityEnabled(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context, NinjaAccessibilityService::class.java)
    }
}
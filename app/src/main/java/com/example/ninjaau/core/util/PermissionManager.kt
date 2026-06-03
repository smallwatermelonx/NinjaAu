package com.example.ninjaau.core.util

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService

/**
 * 权限管理单例：全局保存MediaProjection授权数据与实例复用
 *
 * 设计原则：
 * 1. MediaProjection token 在进程重启后必然失效，不做 SharedPreferences 持久化
 * 2. 仅在当前进程生命周期内缓存授权数据
 * 3. 线程安全（同步锁）
 * 4. Android 12+ 前台服务类型强制检查
 * 5. 上下文安全（仅用ApplicationContext）
 */
object PermissionManager {
    private val lock = Any()

    // 授权原始数据（仅当前进程生命周期内有效）
    var mResultCode: Int = -1
    var mProjectionIntent: Intent? = null

    // 缓存全局唯一的 MediaProjection 实例
    private var _mediaProjection: MediaProjection? = null
    val mediaProjection: MediaProjection? get() = _mediaProjection

    /** 系统回收 MediaProjection 时置 true，脚本应立即暂停 */
    @Volatile
    var isProjectionLost = false
        private set

    // SharedPreferences 键名（仅用于清理旧数据）
    private const val PREFS_NAME = "ninja_au_prefs"
    private const val KEY_RESULT_CODE = "media_projection_result_code"
    private const val KEY_INTENT_URI = "media_projection_intent_uri"

    /**
     * 清理 SharedPreferences 中的旧授权数据（迁移用，防止旧版本残留脏数据）
     * 应在 Application.onCreate 或首次启动时调用一次
     */
    fun cleanStaleData(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_RESULT_CODE) || prefs.contains(KEY_INTENT_URI)) {
                prefs.edit().remove(KEY_RESULT_CODE).remove(KEY_INTENT_URI).apply()
                LogUtil.i("PermissionManager", "已清理 SharedPreferences 中的旧授权数据")
            }
        } catch (e: Exception) {
            LogUtil.e("PermissionManager", "清理旧数据失败", e)
        }
    }

    // 暂停时调用：仅置空但不释放（保留权限）
    fun pauseMediaProjection() {
        synchronized(lock) {
            _mediaProjection = null
            isProjectionLost = false
            LogUtil.i("PermissionManager", "MediaProjection暂停（未释放权限）")
        }
    }

    // 恢复时重新初始化（复用权限）
    fun resumeMediaProjection(context: Context): Boolean {
        synchronized(lock) {
            return initMediaProjection(context)
        }
    }

    /**
     * 判断是否有有效的截图授权数据
     * 仅检查内存中的数据，不读 SharedPreferences
     */
    fun hasProjectionPermission(): Boolean {
        val isCodeValid = mResultCode == Activity.RESULT_OK
        val isIntentValid = mProjectionIntent != null

        if (!isCodeValid || !isIntentValid) {
            if (mResultCode == -1 && mProjectionIntent == null) {
                LogUtil.w("PermissionManager", "尚未申请截图授权（正常状态，点击Link Start后会引导授权）")
            } else {
                LogUtil.e("PermissionManager", "截图授权数据异常: resultCode=$mResultCode, intent=${mProjectionIntent != null}")
            }
            return false
        }

        LogUtil.i("PermissionManager", "截图授权数据有效")
        return true
    }

    /**
     * 设置授权数据（由 CapturePermissionActivity 回调调用）
     */
    fun setProjectionPermission(resultCode: Int, data: Intent?) {
        synchronized(lock) {
            mResultCode = resultCode
            mProjectionIntent = data
            LogUtil.i("PermissionManager", "授权数据已设置: resultCode=$resultCode, hasData=${data != null}")
        }
    }

    /**
     * 初始化 MediaProjection（核心：适配 Android 12+ 安全规范 + 线程安全）
     * @return 是否初始化成功
     */
    fun initMediaProjection(context: Context): Boolean {
        synchronized(lock) {
            // 已有有效实例，直接返回成功
            if (_mediaProjection != null) {
                LogUtil.i("PermissionManager", "MediaProjection已存在，无需重复初始化")
                return true
            }

            // 前置校验：授权数据无效直接返回
            if (!hasProjectionPermission()) {
                LogUtil.e("PermissionManager", "初始化失败：尚未获得有效授权数据")
                return false
            }

            // Android 12+ (API 31+) 强制检查：使用MediaProjection的服务必须声明mediaProjection类型
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isServiceContext = context is Service
                if (isServiceContext) {
                    val service = context as Service
                    val serviceType = service.foregroundServiceType
                    val hasMediaProjectionType = (serviceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) != 0
                    if (!hasMediaProjectionType) {
                        LogUtil.e("PermissionManager", "初始化失败：Android 12+ 服务未声明 foregroundServiceType=mediaProjection")
                        clearProjectionPermission()
                        return false
                    }
                } else {
                    LogUtil.w("PermissionManager", "警告：Android 12+ 建议在前台Service中初始化MediaProjection")
                }
            }

            // 核心逻辑：创建MediaProjection
            return try {
                val appContext = context.applicationContext
                val mpm = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

                _mediaProjection = mpm.getMediaProjection(mResultCode, mProjectionIntent!!)

                _mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        LogUtil.w("PermissionManager", "MediaProjection被系统停止，释放资源")
                        synchronized(lock) {
                            _mediaProjection = null
                        }
                        isProjectionLost = true
                    }
                }, null)

                val createSuccess = _mediaProjection != null
                if (createSuccess) {
                    LogUtil.i("PermissionManager", "MediaProjection初始化成功")
                } else {
                    LogUtil.e("PermissionManager", "初始化失败：MediaProjection创建返回null（token可能已失效）")
                    clearProjectionPermission()
                }
                createSuccess
            } catch (e: Exception) {
                LogUtil.e("PermissionManager", "初始化失败：创建MediaProjection抛出异常", e)
                clearProjectionPermission()
                false
            }
        }
    }

    /**
     * 释放MediaProjection资源（线程安全）
     */
    fun releaseMediaProjection() {
        synchronized(lock) {
            _mediaProjection?.stop()
            _mediaProjection = null
            LogUtil.i("PermissionManager", "MediaProjection已释放")
        }
    }

    /**
     * 清空截图授权数据 + 清理 SharedPreferences
     */
    fun clearProjectionPermission(context: Context? = null) {
        mResultCode = -1
        mProjectionIntent = null
        isProjectionLost = false
        // 同时清理 SharedPreferences 防止下次启动读到脏数据
        if (context != null) {
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(KEY_RESULT_CODE).remove(KEY_INTENT_URI).apply()
            } catch (_: Exception) {}
        }
        LogUtil.i("PermissionManager", "截图授权数据已清空")
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

        val isEnabled = enabledServices.contains(serviceName)
        if (!isEnabled) {
            LogUtil.w("PermissionManager", "无障碍服务未开启：\n预期服务名=$serviceName\n已开启服务=$enabledServices")
        } else {
            LogUtil.i("PermissionManager", "无障碍服务已开启")
        }
        return isEnabled
    }

}

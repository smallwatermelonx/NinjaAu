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
 * 完善点：
 * 1. 线程安全（同步锁）
 * 2. 增强授权数据有效性校验
 * 3. Android 12+ 前台服务类型强制检查
 * 4. 详细日志+脏数据清理
 * 5. 上下文安全（仅用ApplicationContext）
 * 6. SharedPreferences 持久化授权数据，重启应用无需重新授权
 */
object PermissionManager {
    // 线程同步锁：解决多线程同时初始化/释放MediaProjection的问题
    private val lock = Any()

    // 授权原始数据（仅保存，不直接用于初始化，需校验）
    var mResultCode: Int = -1
    var mProjectionIntent: Intent? = null

    // 缓存全局唯一的 MediaProjection 实例
    private var _mediaProjection: MediaProjection? = null
    private var isReleased = false // 标记是否彻底释放
    val mediaProjection: MediaProjection? get() = _mediaProjection

    // SharedPreferences 持久化键名
    private const val PREFS_NAME = "ninja_au_prefs"
    private const val KEY_RESULT_CODE = "media_projection_result_code"
    private const val KEY_INTENT_URI = "media_projection_intent_uri"

    /**
     * 保存授权数据到 SharedPreferences（进程重启后恢复用）
     */
    fun saveProjectionPermission(context: Context) {
        synchronized(lock) {
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt(KEY_RESULT_CODE, mResultCode)
                    .putString(KEY_INTENT_URI, mProjectionIntent?.toUri(Intent.URI_INTENT_SCHEME))
                    .apply()
                LogUtil.i("PermissionManager", "授权数据已持久化")
            } catch (e: Exception) {
                LogUtil.e("PermissionManager", "持久化授权数据失败", e)
            }
        }
    }

    /**
     * 从 SharedPreferences 恢复授权数据（进程重启后调用）
     * @return 是否有有效数据被恢复
     */
    fun restoreProjectionPermission(context: Context): Boolean {
        synchronized(lock) {
            // 内存中已有有效数据，无需恢复
            if (mResultCode == Activity.RESULT_OK && mProjectionIntent != null) return true

            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedCode = prefs.getInt(KEY_RESULT_CODE, -1)
                val savedUri = prefs.getString(KEY_INTENT_URI, null)

                if (savedCode == Activity.RESULT_OK && savedUri != null) {
                    val intent = Intent.parseUri(savedUri, Intent.URI_INTENT_SCHEME)
                    mResultCode = savedCode
                    mProjectionIntent = intent
                    LogUtil.i("PermissionManager", "已从本地恢复授权数据")
                    return true
                }
            } catch (e: Exception) {
                LogUtil.e("PermissionManager", "恢复授权数据失败", e)
                // 恢复失败时清空脏数据
                mResultCode = -1
                mProjectionIntent = null
            }
            return false
        }
    }


    // 暂停时调用：仅置空但不释放（保留权限）
    fun pauseMediaProjection() {
        synchronized(lock) {
            _mediaProjection = null
            LogUtil.i("PermissionManager", "MediaProjection暂停（未释放权限）")
        }
    }

    // 恢复时重新初始化（复用权限）
    fun resumeMediaProjection(context: Context): Boolean {
        synchronized(lock) {
            if (isReleased) {
                // 彻底释放后需要重新授权
                return false
            }
            // 复用已有权限重新初始化
            return initMediaProjection(context)
        }
    }



    /**
     * 增强版：判断是否有有效的截图授权数据
     * 核心优化：区分「首次启动未授权」（警告）和「授权后数据无效」（错误）
     */
    fun hasProjectionPermission(): Boolean {
        // 1. 校验resultCode是否为成功状态
        val isCodeValid = mResultCode == Activity.RESULT_OK
        // 2. 校验Intent非空且包含核心数据（系统授权的Intent必须有extras）
        val isIntentValid = mProjectionIntent != null

        // 3. 核心区分逻辑：
        val isFirstLaunch = (mResultCode == -1) && (mProjectionIntent == null) // 首次启动未授权
        val isAuthDataInvalid = !isFirstLaunch && !isCodeValid // 授权过但数据无效（真错误）

        // 4. 分场景打日志
        if (isFirstLaunch) {
            // ✅ 首次启动未授权：警告级别，友好提示
            LogUtil.w("PermissionManager", "首次启动：尚未申请截图授权（正常状态，点击Link Start后会引导授权）")
            return false
        } else if (isAuthDataInvalid) {
            // ❌ 授权后数据无效：错误级别（真问题）
            val errorMsg = buildString {
                append("截图授权数据异常（已授权但数据无效）：")
                append("resultCode=$mResultCode（预期=${Activity.RESULT_OK}），")
                append("Intent是否为空=${mProjectionIntent == null}，")
                append("Intent是否有数据=${mProjectionIntent?.extras != null}")
            }
            LogUtil.e("PermissionManager", errorMsg)
            return false
        } else {
            // ✅ 授权有效
            LogUtil.i("PermissionManager", "截图授权数据有效")
            return true
        }
    }

    /**
     * 初始化 MediaProjection（核心：适配 Android 12+ 安全规范 + 线程安全 + 上下文安全）
     * @param context 任意上下文（内部自动转为ApplicationContext，避免泄漏）
     * @return 是否初始化成功
     */
    fun initMediaProjection(context: Context): Boolean {
        // 1. 线程同步锁：防止多线程同时创建MediaProjection导致崩溃
        synchronized(lock) {
            // 2. 已有有效实例，直接返回成功
            if (_mediaProjection != null) {
                LogUtil.i("PermissionManager", "MediaProjection已存在，无需重复初始化")
                return true
            }

            // 3. 前置校验：授权数据无效直接返回
            if (!hasProjectionPermission()) {
                LogUtil.e("PermissionManager", "初始化失败：尚未获得有效授权数据")
                return false
            }

            // 4. Android 12+ (API 31+) 强制检查：使用MediaProjection的服务必须声明mediaProjection类型
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 判断当前上下文是否是Service，且是否声明了正确的前台类型
                val isServiceContext = context is Service
                if (isServiceContext) {
                    val service = context as Service
                    val serviceType = service.foregroundServiceType
                    val hasMediaProjectionType = (serviceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) != 0
                    if (!hasMediaProjectionType) {
                        LogUtil.e("PermissionManager", "初始化失败：Android 12+ 服务未声明 foregroundServiceType=mediaProjection")
                        // 清空脏数据，避免后续重复校验失败
                        clearProjectionPermission()
                        return false
                    }
                } else {
                    LogUtil.w("PermissionManager", "警告：Android 12+ 建议在前台Service中初始化MediaProjection（当前上下文=${context::class.java.simpleName}）")
                }
            }

            // 5. 核心逻辑：创建MediaProjection（去掉克隆，直接用原Intent！）
            return try {
                val appContext = context.applicationContext
                val mpm = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

                // 关键修复：删掉克隆，直接传原mProjectionIntent
                _mediaProjection = mpm.getMediaProjection(mResultCode, mProjectionIntent!!)

                // ✅ 核心修复1：注册MediaProjection回调（Android 10+强制要求）
                _mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        // MediaProjection被系统停止（比如用户手动关闭权限），及时释放资源
                        LogUtil.w("PermissionManager", "MediaProjection被系统停止，释放资源")
                        releaseMediaProjection()
                        // 不清空授权数据，允许后续重新初始化（无需再次请求权限）
                        isReleased = true
                    }
                }, null)

                // 6. 校验创建结果（只释放实例，不清空数据！）
                val createSuccess = _mediaProjection != null
                if (createSuccess) {
                    LogUtil.i("PermissionManager", "MediaProjection初始化成功")
                } else {
                    LogUtil.e("PermissionManager", "初始化失败：MediaProjection创建返回null")
                    releaseMediaProjection() // 只释放实例，去掉clearProjectionPermission()！
                }
                createSuccess
            } catch (e: Exception) {
                // 捕获所有异常（比如Intent失效、系统权限回收等）
                LogUtil.e("PermissionManager", "初始化失败：创建MediaProjection抛出异常", e)
                // 异常后清空所有相关数据，避免脏数据导致后续问题
                clearProjectionPermission()
                return false
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
     * 清空截图授权数据（脏数据/授权失败时调用）
     */
    fun clearProjectionPermission() {
        mResultCode = -1
        mProjectionIntent = null
        LogUtil.i("PermissionManager", "截图授权数据已清空")
    }

    /**
     * 检查无障碍服务是否开启
     * 增强：增加日志输出，方便排查无障碍服务未开启的原因
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

    /**
     * 检查悬浮窗权限（新增：统一权限检查入口）
     */
    fun hasOverlayPermission(context: Context): Boolean {
        val hasPermission = Settings.canDrawOverlays(context)
        if (!hasPermission) {
            LogUtil.e("PermissionManager", "悬浮窗权限未开启")
        }
        return hasPermission
    }
}
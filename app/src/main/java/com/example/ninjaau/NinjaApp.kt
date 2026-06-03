package com.example.ninjaau

import android.app.Application
import com.example.ninjaau.core.GameManager
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager

/**
 * 全局 Application 类
 * 用于初始化全局组件和工具类
 */
class NinjaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LogUtil.init(this)
        // 清理旧版本 SharedPreferences 中残留的失效 MediaProjection 数据
        PermissionManager.cleanStaleData(this)
        GameManager.loadInviteCheckSetting(this)
        GameManager.loadPersonalBountySetting(this)
        LogUtil.i("NinjaApp", "NinjaApp 初始化完成")
    }
}
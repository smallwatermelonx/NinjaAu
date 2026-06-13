package com.example.ninjaau

import android.app.Application
import com.example.ninjaau.core.config.ScriptConfigRepository
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager

class NinjaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogUtil.init(this)
        PermissionManager.cleanStaleData(this)
        ScriptConfigRepository.init(this)
        LogUtil.i("NinjaApp", "NinjaApp 初始化完成")
    }
}

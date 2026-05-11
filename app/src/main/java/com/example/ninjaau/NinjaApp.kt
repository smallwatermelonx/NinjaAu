package com.example.ninjaau

import android.app.Application
import com.example.ninjaau.core.util.LogUtil

/**
 * 全局 Application 类
 * 用于初始化全局组件和工具类
 */
class NinjaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 1. 先初始化日志工具
        LogUtil.init(this)

        // 2. OpenCV 由各模块使用时按需初始化，避免冷启动耗时长
        LogUtil.i("NinjaApp", "NinjaApp 初始化完成")
    }
}
package com.example.ninjaau.core

import android.app.Application
import com.example.ninjaau.core.util.LogUtil
import org.opencv.android.OpenCVLoader

/**
 * 全局 Application 类
 * 用于初始化全局组件和工具类
 */
class NinjaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 1. 初始化日志工具
        LogUtil.init(this)
        
        // 2. 初始化 OpenCV
        if (OpenCVLoader.initDebug()) {
            LogUtil.i("NinjaApp", "OpenCV 库加载成功")
        } else {
            LogUtil.e("NinjaApp", "OpenCV 库加载失败")
        }
    }
}

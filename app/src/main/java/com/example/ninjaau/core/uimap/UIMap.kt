package com.example.ninjaau.core.uimap

import android.graphics.RectF

enum class TemplateType { ICON, AREA, FULL }

data class TemplateConfig(
    val templateName: String,      // 模板文件名（比如 tap_to_start.png）
    val module: String,            // 模块名（system/login）
    val type: TemplateType,        // 类型（图标/区域/全屏）
    val recognizeArea: RectF,      // 识别区域（相对坐标 0-1）
    val threshold: Float = 0.8f,   // 匹配阈值
    val isFromAssets: Boolean = false // 登录模板是用户截图（非assets）
) {
    // 读取【项目assets内的模板路径】（对应你项目里的main/assets/templates）
    fun getAssetsPath(): String {
        return "templates/$module/${type.name.lowercase()}/${templateName}.png"
    }
}

object UIMap {
    // 登录页配置（核心）
    object Login {
        // 1. Tap to Start 区域（点击目标）
        val TAP_TO_START = TemplateConfig(
            templateName = "tap_to_start",
            module = "login",
            type = TemplateType.AREA,
            recognizeArea = RectF(0.2f, 0.7f, 0.8f, 0.9f), // 屏幕中间偏下
            threshold = 0.75f
        )

        // 2. 账号图标（右上角）
        val ACCOUNT_ICON = TemplateConfig(
            templateName = "account_icon",
            module = "login",
            type = TemplateType.ICON,
            recognizeArea = RectF(0.9f, 0.55f, 0.98f, 0.66f),
            threshold = 0.8f
        )

        // 3. 更多功能图标（账号下方）
        val MORE_ICON = TemplateConfig(
            templateName = "more_icon",
            module = "login",
            type = TemplateType.ICON,
            recognizeArea = RectF(0.9f, 0.68f, 0.98f, 0.8f),
            threshold = 0.8f
        )
    }

    // 大厅配置（暂时注释，后续用）
    object Hall {
        val HALL_MAIN = TemplateConfig(
            templateName = "hall_main",
            module = "hall",
            type = TemplateType.FULL,
            recognizeArea = RectF(0f, 0f, 1f, 1f),
            threshold = 0.8f
        )
    }
}
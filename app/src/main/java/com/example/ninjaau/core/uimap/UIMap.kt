package com.example.ninjaau.core.uimap

/**
 * 极简版模板配置：专为 OpenCV 全局识别设计
 */
data class TemplateConfig(
    val templateName: String,      // 模板文件名
    val module: String,            // 所属模块 (login/hall/bounty等)
    val threshold: Float = 0.8f,   // 匹配阈值
    val isFromAssets: Boolean = true
) {
    fun getFileName() = "$templateName.png"
    
    // 简化后的路径：templates/模块/名称.png
    fun getAssetsPath(): String = "templates/$module/${getFileName()}"
    
    fun getExternalPath(context: android.content.Context): String {
        return "${context.getExternalFilesDir(null)}/templates/$module/${getFileName()}"
    }
}

object UIMap {
    object Login {
        // 核心修复：文件名必须与 assets 中的真实文件名完全一致
        val TAP_TO_START = TemplateConfig("tap_to_start", "login", 0.75f)
        val ACCOUNT_ICON = TemplateConfig("user_info", "login", 0.8f) // 之前写成了 account_icon
    }

    object Hall {
        val HALL_MAIN = TemplateConfig("hall_main", "hall", 0.8f)
    }
}

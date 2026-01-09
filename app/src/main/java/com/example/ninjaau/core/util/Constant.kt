package com.example.ninjaau.core.util

/**
 * 全局常量管理类
 * 原则：
 * 1. 所有硬编码的固定值（包名、ID、路径、默认参数等）均放此处
 * 2. 按模块分组（应用、无障碍、悬浮窗、文件、监控、识别），便于查找
 * 3. 命名规范：全大写+下划线分隔，模块前缀区分
 */
object Constant {

    // ====================== 1. 应用核心常量（已用） ======================
    /** 忍三游戏包名（核心，后续识别/启动都依赖） */
    const val NINJA_GAME_PACKAGE = "com.pandadagames.ninja.global"
    /** APP自身包名（可选，避免硬编码） */
    const val APP_PACKAGE = "com.example.ninjaau"
    /** APP名称（用于日志/通知） */
    const val APP_NAME = "忍三自动化脚本"

    // ====================== 2. 无障碍服务常量（后续必用） ======================
    /** 无障碍服务类名（用于权限检测） */
    const val ACCESSIBILITY_SERVICE_CLASS = "$APP_PACKAGE.core.accessibility.NinjaAccessibilityService"
    /** 无障碍服务配置文件名（res/xml/下） */
    const val ACCESSIBILITY_CONFIG_FILE = "accessibility_service_config.xml"
    /** 无障碍服务描述（和strings.xml对应） */
    const val ACCESSIBILITY_SERVICE_DESC = "accessibility_service_desc"

    // ====================== 3. 悬浮窗常量（后续必用） ======================
    /** 悬浮窗通知渠道ID（Android 8.0+） */
    const val FLOAT_BALL_CHANNEL_ID = "float_ball_channel"
    /** 悬浮窗通知渠道名称 */
    const val FLOAT_BALL_CHANNEL_NAME = "悬浮窗服务"
    /** 悬浮窗前台服务通知ID */
    const val FLOAT_BALL_NOTIFICATION_ID = 1
    /** 悬浮球默认尺寸（dp） */
    const val FLOAT_BALL_SIZE_DP = 50
    /** 悬浮球拖动贴边阈值（dp） */
    const val FLOAT_BALL_EDGE_THRESHOLD_DP = 10

    // ====================== 4. 文件路径常量（和FileUtil对应） ======================
    /** 日志目录名（内部存储下） */
    const val DIR_NAME_LOGS = "ninja_logs"
    /** 模板图片目录名（uimap） */
    const val DIR_NAME_TEMPLATE = "uimap"
    /** 配置文件目录名 */
    const val DIR_NAME_CONFIG = "config"
    /** 截图目录名 */
    const val DIR_NAME_SCREENSHOTS = "screenshots"
    /** 默认配置文件名 */
    const val CONFIG_FILE_NAME = "script_config.txt"

    // ====================== 5. 监控服务常量（已用/后续扩展） ======================
    /** 监控服务默认间隔（秒） */
    const val MONITOR_DEFAULT_INTERVAL = 30
    /** 监控服务前台通知ID */
    const val MONITOR_NOTIFICATION_ID = 2
    /** 监控服务通知渠道ID */
    const val MONITOR_CHANNEL_ID = "app_monitor_channel"
    /** 监控服务通知渠道名称 */
    const val MONITOR_CHANNEL_NAME = "应用监控服务"

    // ====================== 6. 识别模块常量（后续必用） ======================
    /** 模板图片默认后缀 */
    const val TEMPLATE_IMAGE_SUFFIX = ".png"
    /** 模板匹配默认阈值（0-1，越高越精准） */
    const val TEMPLATE_MATCH_THRESHOLD = 0.85f
    /** 识别超时时间（毫秒） */
    const val RECOGNIZE_TIMEOUT_MS = 3000

    // ====================== 7. 日志常量（和LogUtil对应） ======================
    /** 单个日志文件最大大小（5MB） */
    const val LOG_MAX_FILE_SIZE = 5 * 1024 * 1024L
    /** 日志文件保留天数 */
    const val LOG_KEEP_DAYS = 7

    // ====================== 8. 权限请求常量（后续扩展） ======================
    /** 悬浮窗权限请求码 */
    const val REQUEST_CODE_FLOAT_WINDOW = 1001
    /** 截图权限请求码（若需要） */
    const val REQUEST_CODE_SCREENSHOT = 1002
}
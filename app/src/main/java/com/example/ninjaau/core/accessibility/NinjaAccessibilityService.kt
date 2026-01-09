package com.example.ninjaau.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ninjaau.core.util.LogUtil

/**
 * 忍三自动化核心无障碍服务
 * 功能：监听UI事件、查找界面元素、模拟点击/返回等手势操作
 * 注意：需在系统设置中开启无障碍权限才能生效
 */
class NinjaAccessibilityService : AccessibilityService() {
    private val TAG = "NinjaAccessibilityService"

    // 单例实例（方便全局调用）
    companion object {
        private var instance: NinjaAccessibilityService? = null

        // 获取服务实例（判断服务是否已启动）
        fun getInstance(): NinjaAccessibilityService? {
            return instance
        }

        // 判断服务是否可用（已启动+有权限）
        fun isServiceEnabled(): Boolean {
            return instance != null
        }
    }

    /**
     * 服务创建时初始化（保存实例）
     */
    override fun onCreate() {
        super.onCreate()
        instance = this
        LogUtil.i(TAG, "无障碍服务已创建")
    }

    /**
     * 处理UI事件（比如界面跳转、元素点击、文本变化等）
     * 核心：通过event感知游戏UI状态变化
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // 打印事件类型（调试用，可根据需求过滤事件）
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "界面跳转"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "元素被点击"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "文本变化"
            else -> "其他事件(${event.eventType})"
        }
        LogUtil.d(TAG, "检测到UI事件：$eventType | 包名：${event.packageName}")

        // 示例：如果是忍三的界面，执行自定义逻辑
        if (event.packageName == "com.pandadagames.ninja.global") {
            handleNinjaUiEvent(event)
        }
    }

    /**
     * 处理忍三专属UI事件（可扩展）
     */
    private fun handleNinjaUiEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            // 检测到忍三界面跳转时，可初始化元素查找
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                LogUtil.d(TAG, "忍三界面跳转，开始扫描界面元素")
            }
        }
    }

    /**
     * 无障碍服务被中断时调用（比如用户手动关闭权限）
     */
    override fun onInterrupt() {
        LogUtil.w(TAG, "无障碍服务被中断")
        instance = null
    }

    /**
     * 服务销毁时清理
     */
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        LogUtil.i(TAG, "无障碍服务已销毁")
    }

    // ====================== 核心工具方法：元素查找 + 模拟操作 ======================
    /**
     * 按文本查找界面元素（支持模糊匹配）
     */
    fun findNodeByText(text: String, fuzzy: Boolean = true): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: run {
            LogUtil.e(TAG, "无法获取根节点（服务未就绪）")
            return null
        }

        val nodeList = if (fuzzy) {
            rootNode.findAccessibilityNodeInfosByText(text)
        } else {
            val list = mutableListOf<AccessibilityNodeInfo>()
            traverseNode(rootNode) { node ->
                if (node.text?.toString() == text) {
                    list.add(node)
                }
            }
            list
        }

        return if (nodeList.isNotEmpty()) {
            nodeList[0]
        } else {
            null
        }
    }

    /**
     * 按资源ID查找界面元素
     */
    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        val nodeList = rootNode.findAccessibilityNodeInfosByViewId(id)
        return if (nodeList.isNotEmpty()) nodeList[0] else null
    }

    /**
     * 模拟点击元素
     */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        return try {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                true
            } else {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                clickByCoordinate(rect.centerX(), rect.centerY())
            }
        } catch (e: Exception) {
            false
        } finally {
            node.recycle()
        }
    }

    /**
     * 按坐标模拟点击
     */
    fun clickByCoordinate(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val gestureBuilder = GestureDescription.Builder()
            val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            return dispatchGesture(gestureBuilder.build(), null, null)
        }
        return false
    }

    /**
     * 模拟返回键
     */
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, action: (AccessibilityNodeInfo) -> Unit) {
        node ?: return
        action(node)
        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), action)
        }
    }

    /**
     * 跳转到系统无障碍设置页面
     */
    fun openAccessibilitySetting() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}

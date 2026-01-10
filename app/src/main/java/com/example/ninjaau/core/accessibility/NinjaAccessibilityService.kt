package com.example.ninjaau.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ninjaau.core.util.LogUtil

/**
 * 继承系统AccessibilityService，重写核心生命周期方法；
 * 监听忍三的 UI 事件（界面跳转、元素点击等）；
 * 提供单例实例，方便全局调用（比如GestureExecutor用它执行点击）；
 * 预留扩展接口（比如遍历界面节点、处理忍三专属事件）。
 */
class NinjaAccessibilityService : AccessibilityService() {
    private val TAG = "NinjaAccessibilityService"

    // 单例实例（方便全局调用）
    companion object {
        private var instance: NinjaAccessibilityService? = null
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


    private fun traverseNode(node: AccessibilityNodeInfo?, action: (AccessibilityNodeInfo) -> Unit) {
        node ?: return
        action(node)
        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), action)
        }
    }
}

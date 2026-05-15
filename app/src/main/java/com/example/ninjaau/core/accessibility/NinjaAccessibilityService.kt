package com.example.ninjaau.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.example.ninjaau.core.util.LogUtil

/**
 * 自动化核心无障碍服务
 */
class NinjaAccessibilityService : AccessibilityService() {
    private val TAG = "NinjaAccessibilityService"

    companion object {
        private var instance: NinjaAccessibilityService? = null
        fun getInstance(): NinjaAccessibilityService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LogUtil.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * 核心：在指定屏幕坐标执行点击
     */
    fun clickAt(x: Float, y: Float): Boolean {
        LogUtil.d(TAG, "尝试点击坐标: ($x, $y)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x, y)
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            return dispatchGesture(builder.build(), null, null)
        }
        return false
    }
}

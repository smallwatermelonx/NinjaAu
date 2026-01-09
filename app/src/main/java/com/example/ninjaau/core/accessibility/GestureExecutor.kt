package com.example.ninjaau.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo

class GestureExecutor(private val service: AccessibilityService) {

    fun performClick(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long) {
        val path = Path()
        path.moveTo(fromX, fromY)
        path.lineTo(toX, toY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
}

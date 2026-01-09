package com.example.ninjaau.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class ElementFinder(private val service: AccessibilityService) {

    fun findElementByText(text: String): AccessibilityNodeInfo? {
        val rootNode = service.rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }

    fun findElementById(viewId: String): AccessibilityNodeInfo? {
        val rootNode = service.rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        return nodes.firstOrNull()
    }
}

package com.liss.smartsos

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoclickerService : AccessibilityService() {

    private var isPanicPressed = false
    private val handler = Handler()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isPanicPressed && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d("Autoclicker","En la pantalla principal de 911")
            handler.postDelayed({
                rootInActiveWindow?.let { rootNode ->
                    val targetNode = findNodeByText(rootNode, "Pánico")
                    targetNode?.let { node ->
                        performClick(node)
                        node.recycle()
                        isPanicPressed = true
                        Log.d("Autoclicker", "Boton con texto de Panico encontrado")
                    }
                }
            }, 3000)
        }
        if(isPanicPressed && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d("Autoclicker","En el boton de panico")
            disableSelf()
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == targetText) {
            return node
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            val targetNode = findNodeByText(childNode, targetText)
            if (targetNode != null) {
                return targetNode
            }
        }
        node.recycle()
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            performClick(childNode)
        }
        node.recycle()
    }

    override fun onInterrupt() {}
}

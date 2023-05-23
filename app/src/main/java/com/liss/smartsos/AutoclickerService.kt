package com.liss.smartsos

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoclickerService : AccessibilityService() {

    private var isClickPerformed = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isClickPerformed && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Handler().postDelayed({
                rootInActiveWindow?.let { rootNode ->
                    val targetNode = findNodeByText(rootNode, "Pánico")
                    targetNode?.let { node ->
                        performClick(node)
                        node.recycle()
                    }
                    isClickPerformed = true
                    disableSelf()
                    Log.d("SERVICIO","FINALIZADO")
                }
            }, 3000)
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == targetText) {
            return node
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            val targetNode = findNodeByText(childNode, targetText)
            childNode.recycle()
            if (targetNode != null) {
                return targetNode
            }
        }
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
            childNode.recycle()
        }
    }


    override fun onInterrupt() {}
}

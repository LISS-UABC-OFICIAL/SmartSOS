package com.liss.smartsos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.postDelayed
import java.util.*

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
            handler.postDelayed({
                rootInActiveWindow?.let { rootNode ->
                    Log.d("123", findNodeByText(rootNode, "Tijuana").toString())
                    val rButtonTij = findNodeByText(rootNode, "Tijuana").toString()
                    if (rButtonTij != null) {
                        val targetNode = findNodeByText(rootNode, "Tijuana")
                        targetNode?.let { node ->
                            performSelect(node)
                            node.recycle()
                            Log.d("Autoclicker", "Click")
                        }
                    }
                    handler.postDelayed({
                        Log.d("321", findFirstImageView(rootNode).toString())
                        performLongPressAtCenter()
                        disableSelf()
                    },2000)
                }
            }, 1000)
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

    //Codificacion para el dialogo de alerta si se muestra
    private fun performSelect(node: AccessibilityNodeInfo){
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        node.recycle()
    }

    //Codificacion para la pantalla de boton de panico
    private fun findFirstImageView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.ImageView") {
            return node
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                val targetNode = findFirstImageView(childNode)
                if (targetNode != null) {
                    return targetNode
                }
            }
        }
        node.recycle()
        return null
    }

    private fun performLongPressAtCenter() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        Log.d("333w", screenWidth.toString())
        Log.d("333h", screenWidth.toString())

        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        val path = Path().apply {
            moveTo(centerX.toFloat(), centerY.toFloat())
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 1000)

        val gestureBuilder = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val gestureResultCallback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                // Pulsación larga completada
                Log.d("ezpz", "ezpz")
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                // Pulsación larga cancelada
                Log.d("oof", "oof")
            }
        }

        dispatchGesture(gestureBuilder, gestureResultCallback, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        disableSelf()
    }
}

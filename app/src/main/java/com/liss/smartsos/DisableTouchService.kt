package com.liss.smartsos

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class DisableTouchService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var transparentView: View
    private val handler = Handler()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        disableTouch()
        //Agregar un delay de 10s para rehabilitar el touch en caso de que se quede atascado el servicio por alguna razon
        handler.postDelayed({
            reEnableTouch()
        },10000)
    }

    fun disableTouch() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            transparentView = View(this).apply {
                setBackgroundColor(0x88000000.toInt()) // Ajusta el color y la opacidad de la vista transparente
            }
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            windowManager.addView(transparentView, layoutParams)
        } catch (e: WindowManager.BadTokenException) {
            // Manejar la excepción de tipo BadTokenException
            Log.d("DisableTouchService","disableTouch(): Error deshabilitando touch")
            Log.e("TAG", "Error al agregar la vista transparente: ${e.message}")
        }
    }

    fun reEnableTouch(){
        try {
            windowManager.removeView(transparentView)
        } catch (e: IllegalArgumentException) {
            // Manejar la excepción de tipo IllegalArgumentException
            Log.e("TAG", "Error al eliminar la vista transparente: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(transparentView)
        } catch (e: IllegalArgumentException) {
            // Manejar la excepción de tipo IllegalArgumentException
            Log.e("TAG", "Error al eliminar la vista transparente: ${e.message}")
        }
    }
}
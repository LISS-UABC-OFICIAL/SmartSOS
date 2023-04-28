package com.liss.smartsos

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

//importaciones relacionadas a la conectividad bluetooth
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket

//importaciones necesarias
import android.widget.Button
import android.widget.Toast
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatDelegate
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.gsm.SmsManager

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.*
import android.os.Looper
import android.os.Handler

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Forzar que el modo oscuro se deshabilite
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContentView(R.layout.activity_main)

        //Boton de prueba para ejecutar la aplicacion 911
        val buttonTest = findViewById<Button>(R.id.button)
        buttonTest.setOnClickListener {
            //Ejecuta la funcion al pulsar el boton
            //exec911()
            //sendSMS("6645333103", "Mensaje de prueba",this)
            sendSMS("6643538663", "Mensaje de prueba 123 hola",this)
        }

        //BT
        /*
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth no está habilitado", Toast.LENGTH_LONG).show()
            return
        }

        bluetoothAdapter.getProfileProxy(this, mHeadsetServiceListener, BluetoothProfile.HEADSET)

        */

        //TEST
    }

    //Ejecutar aplicacion 911
    private fun exec911() {
        //Nombre interno de la aplicacion 911
        val launchIntent = packageManager.getLaunchIntentForPackage("com.c4bc.alerta066m")
        if (launchIntent != null) {
            launchIntent.setClassName("com.c4bc.alerta066m", "com.c4bc.alerta066m.activities.Splash")
            startActivity(launchIntent)
        } else {
            //Envia mensaje cuando no se pueda encontrar la aplicacion
            Toast.makeText(this@MainActivity, "La aplicacion no se encuentra o no esta instalada en el dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun execMedTrack() {
        //Nombre interno de la aplicacion MedTrack
        val launchIntent2 = packageManager.getLaunchIntentForPackage("med.track.med")
        if (launchIntent2 != null) {
            launchIntent2.setClassName("med.track.med", "io.ionic.starter.MainActivity")
            startActivity(launchIntent2)
        } else {
            //Envia mensaje cuando no se pueda encontrar la aplicacion
            Toast.makeText(this@MainActivity, "La aplicacion no se encuentra o no esta instalada en el dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    //Mostrar el boton de configuracion
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings,menu)
        return true
    }

    //Detectar pulsaciones en el boton de configuracion
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> {
                //Acciones a realizar al presionar el boton
                Toast.makeText(this, "Boton de configuracion presionado", Toast.LENGTH_LONG).show()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    //SMS
    private fun sendSMS(phoneNumber: String, message: String, context: Context) {
        val sentIntent = Intent("SMS_SENT").let { sentIntent ->
            PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
    }

    /*
    //Codificacion relacionada con la conectividad Bluetooth
    private var mBluetoothHeadset: BluetoothHeadset? = null
    private val mHeadsetServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, headset: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = headset as BluetoothHeadset
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = null
            }
        }
    }
    */

    override fun onDestroy() {
        super.onDestroy()
        /*
        mBluetoothHeadset?.let {
            BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, it)
        }
        */
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            Toast.makeText(this, "KeyCode: $keyCode", Toast.LENGTH_SHORT).show()
            //return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_START-> {
                Toast.makeText(this, "Pulsación de boton detectada", Toast.LENGTH_LONG).show()
                exec911()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                Toast.makeText(this, "Pulsación de bluetooth detectada", Toast.LENGTH_LONG).show()
                exec911()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY-> {
                Toast.makeText(this, "Pulsación de bluetooth detectada", Toast.LENGTH_LONG).show()
                exec911()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                Toast.makeText(this, "Pulsación de bluetooth detectada", Toast.LENGTH_LONG).show()
                exec911()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                Toast.makeText(this, "Pulsación de boton detectada", Toast.LENGTH_LONG).show()
                execMedTrack()
                return true
            }
            KeyEvent.KEYCODE_1 -> {
                Toast.makeText(this, "Pulsación de boton detectada", Toast.LENGTH_LONG).show()
                exec911()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
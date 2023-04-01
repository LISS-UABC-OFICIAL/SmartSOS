package com.liss.smartsos

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

//importaciones relacionadas a la conectividad bluetooth
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile

//importaciones necesarias
import android.widget.Button
import android.widget.Toast
import android.view.KeyEvent

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Boton de prueba para ejecutar la aplicacion 911
        val buttonTest = findViewById<Button>(R.id.button)
        buttonTest.setOnClickListener {
            //Ejecuta la funcion al pulsar el boton
            exec911()
        }

        //BT
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth no está habilitado", Toast.LENGTH_LONG).show()
            return
        }

        bluetoothAdapter.getProfileProxy(this, mHeadsetServiceListener, BluetoothProfile.HEADSET)
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
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        mBluetoothHeadset?.let {
            BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, it)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                Toast.makeText(this, "Pulsación de Play/Pause detectada", Toast.LENGTH_LONG).show()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                Toast.makeText(this, "Pulsación de Siguiente detectada", Toast.LENGTH_LONG).show()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                Toast.makeText(this, "Pulsación de Anterior detectada", Toast.LENGTH_LONG).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
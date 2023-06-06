package com.liss.smartsos

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

//importaciones relacionadas a la conectividad bluetooth
import android.bluetooth.*
import android.os.AsyncTask

//importaciones relacionadas a la obtencion de contacto de confianza
import android.provider.ContactsContract

//importaciones relacionadas al envio de SMS
import android.telephony.gsm.SmsManager

//Importaciones relacionadas a la ubicacion
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener

//Importaciones relacionadas a las pulsaciones automaticas
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityManager
import android.content.ComponentName
import android.provider.Settings

//importaciones necesarias
import android.widget.Button
import android.widget.Toast
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatDelegate
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    //Variables de la interfaz
    lateinit var uiPulseraEstado: Button
    lateinit var uiPulseraNombre: TextView
    lateinit var uiPulseraDireccion: TextView

    //Configuracion para obtener valores de las preferencias
    lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Forzar que el modo oscuro se deshabilite
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)

        //Solicitando permisos iniciales
        if (permissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            // Todos los permisos están concedidos
            // Puedes realizar las operaciones relacionadas con los permisos aquí
        } else {
            // Al menos uno de los permisos no está concedido
            showPermissionDialog()
        }

        //Solicitando permisos de accesibilidad
        //Si ya se tienen los permisos no se pregunta otra vez
        if (!isAccessibilityServiceEnabled(this, AutoclickerService::class.java)) {
            showAccessibilityPermissionDialog()
        }

        //Solicitar ciudad actual al usuario (por alguna razon tiene que estar al ultimo para que salga primero..?)
        sharedPref = applicationContext.getSharedPreferences("cfgSmartSOS", Context.MODE_PRIVATE)
        //Imprimir ciudad actual en la consola
        Log.d("DEBUG: MainActivity onCreate()","Ciudad Actual - "+sharedPref.getString("ciudadActual", ""))
        val ciudadActual = sharedPref.getString("ciudadActual", "")
        // Verificar si la ciudad actual está vacía
        if (ciudadActual.isNullOrEmpty()) {
            // La ciudad actual está vacía, ejecutar la función mostrarDialogoSeleccionCiudad
            mostrarDialogoSeleccionCiudad(this, sharedPref)
        }

        //Interfaz (IMPORTANTE NO CAMBIAR DE LUGAR)
        uiPulseraEstado = findViewById(R.id.buttonPulseraEstado)
        uiPulseraEstado.text = "Esperando"
        uiPulseraEstado.setBackgroundColor(Color.GRAY)

        uiPulseraNombre = findViewById(R.id.pulseraNombre)
        uiPulseraNombre.text = "Nombre:"

        uiPulseraDireccion = findViewById(R.id.pulseraDireccion)
        uiPulseraDireccion.text = "Direccion:"

        //Boton de prueba para ejecutar la aplicacion 911
        val buttonTest = findViewById<Button>(R.id.button)
        buttonTest.setOnClickListener {
            //Ejecuta la funcion al pulsar el boton
            //Toast.makeText(this, getLocationLink(this), Toast.LENGTH_LONG).show()
            execSOS()
        }

        //Boton para activar la deteccion de pulsaciones
        val buttonSerialScan = findViewById<Button>(R.id.buttonActivar)
        buttonSerialScan.setOnClickListener{
            //Ejecuta el escaneo del boton bluetooth
            serialScan()
        }
    }

    //Variables globales
    //Chequeo para evitar que la deteccion de bluetooth se ejecute multiples veces
    private var isSerialScanRunning = false
    //Localizacion actual
    var ubi = ""
    // Array de permisos a solicitar
    val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    //Pedir al usuario su ciudad actual
    private fun mostrarDialogoSeleccionCiudad(context: Context, sharedPref: SharedPreferences) {
        val ciudades = arrayOf("Mexicali", "Tecate", "Tijuana", "Ensenada", "Rosarito")
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Selecciona tu ciudad")
        builder.setItems(ciudades) { dialog, which ->
            val ciudadSeleccionada = ciudades[which]
            sharedPref.edit().putString("ciudadActual", ciudadSeleccionada).apply()
            Log.d("mostrarDialogoSeleccionCiudad()", "Ciudad actualizada: " + sharedPref.getString("ciudadActual", ""))
            dialog.dismiss()
        }
        builder.show()
    }

    //Solicitando permisos
    private fun requestPermissions(){
        // Comprobar si se tienen todos los permisos necesarios
        val permissionsToRequest = mutableListOf<String>()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        // Si se tienen todos los permisos, se puede continuar con la ejecución del código que los requiere, si no se tienen todos los permisos, se solicitan al usuario
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        }

        // Verificamos si el permiso de ubicación está concedido para actualizar la ubicacion
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Obtenemos el enlace de ubicación
            ubi = getLocationLink(this)
        }
    }

    // Función para mostrar un diálogo de permisos
    private fun showPermissionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setMessage("Esta aplicación necesita los permisos siguientes para funcionar correctamente.")
            setPositiveButton("Aceptar") { _, _ ->
                requestPermissions()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    //Pulsaciones automaticas
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val colonSplitter = enabledServices?.split(':') ?: return false
        return colonSplitter.any { componentNameString ->
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            enabledComponent != null && enabledComponent.packageName == context.packageName && enabledComponent.className == service.name
        }
    }

    private fun showAccessibilityPermissionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setMessage("Esta aplicación requiere permisos de accesibilidad para funcionar correctamente.")
            setPositiveButton("Aceptar") { _, _ ->
                requestAccessibilityPermission()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        this.startActivity(intent)
    }

    private fun startAutoclickerService(context: Context) {
        if (isAccessibilityServiceEnabled(context, AutoclickerService::class.java)) {
            val intent = Intent(context, AutoclickerService::class.java)
            context.startService(intent)
        } else {
            // El servicio de accesibilidad no está habilitado, muestra un mensaje de error o solicita al usuario que lo habilite.
        }
    }


    //Ejecutar aplicacion 911
    private fun exec911() {
        //Nombre interno de la aplicacion 911
        val launchIntent = packageManager.getLaunchIntentForPackage("com.c4bc.alerta066m")
        if (launchIntent != null) {
            launchIntent.setClassName("com.c4bc.alerta066m", "com.c4bc.alerta066m.activities.Splash")
            startActivity(launchIntent)
            startAutoclickerService(getApplicationContext())
        } else {
            //Envia mensaje cuando no se pueda encontrar la aplicacion
            Toast.makeText(this@MainActivity, "La aplicacion no se encuentra o no esta instalada en el dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun execSOS(){
        ubi = getLocationLink(this)
        val cc = getSelectedContactNumber()
        if (cc != "error")
        {
            sendSMS(cc, "Mensaje de prueba 123"+" "+ubi,this)
            Toast.makeText(this, "OOF"+ubi, Toast.LENGTH_LONG).show()
        }
        else{
            Toast.makeText(this, "Se necesita indicar un contacto de confianza para enviar mensajes", Toast.LENGTH_LONG).show()
        }
        exec911()
    }

    //Ejecutar aplicacion MedTrak
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
            R.id.contactSelect -> {
                getContactInfo()
                return true
            }
            R.id.contactView -> {
                getSelectedContactInfo()
                return true
            }
            R.id.citySelect -> {
                mostrarDialogoSeleccionCiudad(this, sharedPref)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    //Obtener contacto de confianza
    // Crear una variable para almacenar el ID del contacto seleccionado
    var contactId: String? = null

    // Crear una función para obtener el nombre y número de teléfono del contacto seleccionado
    fun getContactInfo() {
        // Verificar si tenemos permiso de lectura de contactos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // Si no tenemos permiso, se muestra un mensaje explicando que se requiere el permiso
            Toast.makeText(this, "Se requiere permiso de contactos para obtener el contacto de confianza", Toast.LENGTH_SHORT).show()
        } else {
            // Si tenemos permiso, abrir la lista de contactos
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            startActivityForResult(intent, 1)
        }
    }

    // Crear una función para obtener el nombre y número de teléfono del contacto seleccionado usando el ID
    fun getSelectedContactInfo() {
        // Verificar si tenemos el ID del contacto seleccionado
        if (contactId == null) {
            // Si no tenemos el ID, mostrar un mensaje de error
            val contactMsgShow = AlertDialog.Builder(this)
            contactMsgShow .setTitle("Hubo un problema")
            contactMsgShow .setMessage("Aun no se ha asignado un contacto de confianza")
            contactMsgShow .setPositiveButton("Aceptar") { dialog, which ->
                // Acción que se realiza al pulsar el botón "Aceptar"
            }
            contactMsgShow .setNegativeButton("Asignar contacto") { dialog, which ->
                // Acción que se realiza al pulsar el botón "Editar"
                getContactInfo()
            }
            val dialog = contactMsgShow .create()
            dialog.show()
        } else {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(contactId),
                null
            )
            cursor?.let {
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    // Usar el nombre y número de teléfono como sea necesario

                    //Mostrar un mensaje con el nombre y numeros obtenidos
                    val contactMsgShow = AlertDialog.Builder(this)
                    contactMsgShow .setTitle("Contacto de confianza actual")
                    contactMsgShow .setMessage("$name $phoneNumber")
                    contactMsgShow .setPositiveButton("Aceptar") { dialog, which ->
                        // Acción que se realiza al pulsar el botón "Aceptar"
                    }
                    contactMsgShow .setNegativeButton("Editar contacto") { dialog, which ->
                        // Acción que se realiza al pulsar el botón "Editar"
                        getContactInfo()
                    }
                    val dialog = contactMsgShow .create()
                    dialog.show()

                }
                cursor.close()
            }
        }
    }

    fun getSelectedContactNumber(): String {
        // Verificar si tenemos el ID del contacto seleccionado
        if (contactId == null) {
            // Si no tenemos el ID, mostrar un mensaje de error
            return "error"
        } else {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(contactId),
                null
            )
            cursor?.let {
                if (cursor.moveToFirst()) {
                    val phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    return phoneNumber
                }
                cursor.close()
            }
            return "error"
        }
    }

    // Sobrescribir el método onActivityResult para obtener el ID del contacto seleccionado
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            val contactUri = data?.data
            contactUri?.let {
                val cursor = contentResolver.query(it, null, null, null, null)
                cursor?.let {
                    if (cursor.moveToFirst()) {
                        contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                    }
                    cursor.close()
                    Toast.makeText(this, "Contacto de confianza guardado: "+getSelectedContactNumber(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    //Enviar SMS
    private fun sendSMS(phoneNumber: String, message: String, context: Context) {
        // Comprobamos si tenemos permiso para enviar SMS
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            // Si no tenemos permiso, mostramos un mensaje al usuario
            Toast.makeText(context, "Se requiere permiso para enviar SMS", Toast.LENGTH_SHORT).show()
            return
        }

        // Creamos el PendingIntent con la flag FLAG_IMMUTABLE
        val sentIntent = Intent("SMS_SENT").let { sentIntent ->
            PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        // Enviamos el mensaje
        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
    }

    //Obtener la ubicacion para SMS
    fun getLocationLink(context: Context): String {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Verificamos si el permiso de ubicación está concedido
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Definimos un LocationListener para obtener actualizaciones de ubicación
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // Cuando se obtiene una nueva ubicación, se crea el enlace de Google Maps con las coordenadas
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val mapsLink = "Actualizacion https://www.google.com/maps?q=$latitude,$longitude"
                    // Imprimimos el enlace a la consola para verificar que se está actualizando correctamente
                    Log.d("getLocationLink()","Localizacion inicial: "+mapsLink)
                }

                override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Registramos el LocationListener para recibir actualizaciones de ubicación cada 5 segundos
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0f, locationListener)

            // Solicitamos una sola actualizacion de ubicación para ahorrar bateria
            //locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)

            // Si se obtuvo la ubicación previa, se crea el enlace de Google Maps con las coordenadas
            val lastLocation: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation != null) {
                val latitude = lastLocation.latitude
                val longitude = lastLocation.longitude
                val mapsLink = "Ultima localizacion registrada: https://www.google.com/maps?q=$latitude,$longitude"
                // Imprimimos el enlace a la consola para verificar que se está obteniendo correctamente
                Log.d("getLocationLink()","Localizacion inicial: "+mapsLink)
                return "https://www.google.com/maps?q=$latitude,$longitude"
            }
        }

        // Si no se pudo obtener la ubicación o el permiso de ubicación no está concedido, regresamos un espacio vacío
        return ""
    }

    //Deteccion Bluetooth Serial
    private inner class BluetoothReadTask : AsyncTask<String, String, Void>() {
        private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        private var socket: BluetoothSocket? = null

        override fun doInBackground(vararg params: String?): Void? {
            val address = params[0]

            val device = bluetoothAdapter.getRemoteDevice(address)
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            try {
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()

                val inputStream = socket?.inputStream
                val buffer = ByteArray(1024)
                var bytes: Int

                Log.d("serialScan()","Conectado con el boton")
                runOnUiThread {
                    uiPulseraEstado.text = "Conectado"
                    uiPulseraEstado.setBackgroundColor(Color.GREEN)

                    uiPulseraNombre.text = "Nombre: SmartSOS"
                    uiPulseraDireccion.text = "Direccion: "+address
                }

                while (true) {
                    if (isCancelled) {
                        // La tarea ha sido cancelada, cerramos el socket y salimos del loop
                        socket?.close()
                        break
                    }

                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes != -1) {
                        val message = String(buffer, 0, bytes)
                        publishProgress(message)
                    }
                }
            } catch (e: IOException) {
                // Error al establecer la conexión Bluetooth
                e.printStackTrace()
                Log.d("serialScan()","Error: No se puede emparejar con el dispositivo")
                cancel(true)
            }
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            isSerialScanRunning = true
            Log.d("serialScan()","Ejecutando la deteccion serial...")
            uiPulseraEstado.text = "Emparejando"
            uiPulseraEstado.setBackgroundColor(Color.BLUE)

            uiPulseraNombre.text = "Nombre: Recibiendo..."
            uiPulseraDireccion.text = "Direccion: Recibiendo..."
        }

        override fun onProgressUpdate(vararg values: String?) {
            val message = values[0]
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            if (message == "1") {
                execSOS()
            }
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            isSerialScanRunning = false
            Log.d("serialScan()","Deteccion serial ejecutada correctamente")
        }

        override fun onCancelled() {
            isSerialScanRunning = false
            socket?.close()
            Log.d("serialScan()","Conexion cerrada")
            uiPulseraEstado.text = "Esperando"
            uiPulseraEstado.setBackgroundColor(Color.GRAY)

            uiPulseraNombre.text = "Nombre:"
            uiPulseraDireccion.text = "Direccion:"
        }

        override fun onCancelled(result: Void?) {
            isSerialScanRunning = false
            socket?.close()
            Log.d("serialScan()","Conexion cerrada")
            uiPulseraEstado.text = "Esperando"
            uiPulseraEstado.setBackgroundColor(Color.GRAY)

            uiPulseraNombre.text = "Nombre:"
            uiPulseraDireccion.text = "Direccion:"
        }
    }

    //Comunicacion Bluetooth Serial
    fun serialScan() {
        if (isSerialScanRunning) {
            // La función ya está en curso, no se ejecuta nuevamente
            Log.d("serialScan()","Ya se esta ejecutando la deteccion serial, no se necesita ejecutar de nuevo")
            return
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // El dispositivo no admite Bluetooth
            // Se muestra al usuario el mensaje correspondiente
            Toast.makeText(this, "El dispositivo no admite bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled()) {
            // El Bluetooth no está activado, se puede solicitar al usuario que lo active
            // Se muestra al usuario el mensaje correspondiente
            Toast.makeText(this, "Se requiere activar el Bluetooth del dispositivo", Toast.LENGTH_LONG).show()
            return
        }

        val bondedDevices = bluetoothAdapter.bondedDevices
        var address: String? = null
        for (device in bondedDevices) {
            if (device.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.UNCATEGORIZED
                && device.name == "SmartSOS") {
                // Se ha encontrado el dispositivo deseado
                address = device.address
                break
            }
        }
        if (address == null) {
            // El dispositivo no está emparejado o no se ha encontrado el dispositivo deseado
            Toast.makeText(this, "Se requiere emparejar la pulsera antes de usar la aplicacion", Toast.LENGTH_LONG).show()
            return
        }

        val readTask = BluetoothReadTask()
        readTask.execute(address)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            Toast.makeText(this, "KeyCode: $keyCode", Toast.LENGTH_SHORT).show()
            //return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_START-> {
                Toast.makeText(this, "Pulsación de boton detectada", Toast.LENGTH_LONG).show()
                execSOS()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                Toast.makeText(this, "Pulsación de boton detectada", Toast.LENGTH_LONG).show()
                execMedTrack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
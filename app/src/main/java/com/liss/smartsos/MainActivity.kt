package com.liss.smartsos

import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

//importaciones relacionadas a la conectividad bluetooth
import android.bluetooth.*

//importaciones relacionadas a la obtencion de contacto de confianza
import android.provider.ContactsContract

//importaciones relacionadas al envio de SMS
import android.telephony.gsm.SmsManager

//Importaciones relacionadas a la ubicacion
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener

//Importaciones relacionadas al encendido de pantalla
import android.view.WindowManager

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
import android.net.Uri
import android.os.*
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

        //Solicitando permisos para mostrar sobre apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                // El permiso ya ha sido concedido
                // Puedes realizar las acciones correspondientes aquí
            } else {
                // El permiso no ha sido concedido
                showOverlayPermissionDialog()
            }
        } else {
            // Versiones de Android anteriores a Marshmallow (API nivel 23)
            // El permiso se considera concedido
            // Puedes realizar las acciones correspondientes aquí
        }

        //Solicitando permisos de accesibilidad
        //Si ya se tienen los permisos no se pregunta otra vez
        if (!isAccessibilityServiceEnabled(this, AutoclickerService::class.java)) {
            showAccessibilityPermissionDialog()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_DENIED) {
                showLocationPermanentPermissionDialog()
            } else {
                // El permiso ya ha sido concedido
            }
        } else {
            // El permiso no es necesario en versiones anteriores a Android Q
        }

        //Solicitando permisos iniciales
        if (permissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            // Todos los permisos están concedidos
            // Puedes realizar las operaciones relacionadas con los permisos aquí
            ubi = getLocationLink(this)
        } else {
            // Al menos uno de los permisos no está concedido
            showPermissionDialog()
        }

        //Solicitar ciudad actual al usuario (por alguna razon tiene que estar al ultimo para que salga primero..?)
        sharedPref = applicationContext.getSharedPreferences("cfgSmartSOS", Context.MODE_PRIVATE)
        Log.d("DEBUG: MainActivity onCreate()","Automatizacion ejecutandose? - "+sharedPref.getString("autoExec", "no"))
        sharedPref.edit().putString("autoExec", "no").apply()
        Log.d("DEBUG: MainActivity onCreate()","ID de contacto guardado - "+sharedPref.getString("contactId", "error"))
        Log.d("DEBUG: MainActivity onCreate()","Numero de contacto guardado - "+getSelectedContactNumber())
        //Imprimir ciudad actual en la consola
        Log.d("DEBUG: MainActivity onCreate()","Modo activo - "+sharedPref.getString("modoActivo", "911MovilBC"))
        Log.d("DEBUG: MainActivity onCreate()","Ciudad guardada - "+sharedPref.getString("ciudadActual", ""))
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
            ejecutarConDelay(this)
            //execSOS()
        }

        //Boton para activar la deteccion de pulsaciones
        val buttonSerialScan = findViewById<Button>(R.id.buttonActivar)
        buttonSerialScan.setOnClickListener{
            //Ejecuta el escaneo del boton bluetooth
            serialScan()
        }

        //Boton para elegir app a utilizar
        val buttonAppSelect = findViewById<Button>(R.id.buttonElegirApp)
        buttonAppSelect.setOnClickListener{
            //Ejecuta el escaneo del boton bluetooth
            mostrarDialogoSeleccionAplicacion(this,sharedPref)
        }
    }

    //Variables globales
    //Chequeo para evitar que la deteccion de bluetooth se ejecute multiples veces
    private var isSerialScanRunning = false
    //Chequeo para evitar que la deteccion de la pulsacion se detecte multiples veces, interrumpiendo el proceso automatico
    private var isButtonPressDetected = false
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

    //Pedir al usuario la aplicacion a utilizar
    private fun mostrarDialogoSeleccionAplicacion(context: Context, sharedPref: SharedPreferences) {
        val modos = arrayOf("911MovilBC", "Boton Emergencia Tijuana","Med-Track", "Enviar SMS solamente")
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Selecciona aplicacion a utilizar")
        builder.setItems(modos) { dialog, which ->
            val modoSeleccionado = modos[which]
            sharedPref.edit().putString("modoActivo", modoSeleccionado).apply()
            Log.d("mostrarDialogoSeleccionAplicacion()", "Modo actualizado: " + sharedPref.getString("modoActivo", "911MovilBC"))
            dialog.dismiss()
        }
        builder.show()
    }

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

    // Función para mostrar un diálogo cuando no se aceptaron los permisos previamente
    private fun showPermissionErrorDialog() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle("Hubo un problema")
            setMessage("Uno de los permisos no se ha aceptado anteriormente, es necesario aceptar los permisos manualmente y reiniciar la aplicacion. De lo contrario no se puede garantizar un correcto funcionamiento de la aplicacion.")
            setPositiveButton("Aceptar") { _, _ ->
                // Abrir la configuración de la aplicación para que el usuario pueda otorgar los permisos manualmente
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    // Función para manejar la respuesta de la solicitud de permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // El permiso de ubicación fue concedido, llamamos a getLocationLink()
                ubi = getLocationLink(this)
            }
            else{
                // Comprobamos por lo menos se obtuvo el permiso de ubicación y llamamos a getLocationLink()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    ubi = getLocationLink(this)
                }
                showPermissionErrorDialog()
            }
        }
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

    //Pregunta y pide el permiso de localizacion permanente
    private fun showLocationPermanentPermissionDialog(){
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setMessage("Es necesario que el permiso de ubicacion se encuentre en Permitir todo el tiempo para que la aplicacion funcione correctamente.")
            setPositiveButton("Aceptar") { _, _ ->
                requestLocationPermanent()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun requestLocationPermanent(){
        requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 33)
    }

    //Codificacion necesaria en la deshabilitacion de pulsaciones por parte del usuario
    private fun showOverlayPermissionDialog(){
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setMessage("Es necesario que el permiso de Mostrar sobre otras apps se active para que la aplicacion funcione correctamente.")
            setPositiveButton("Aceptar") { _, _ ->
                requestOverlayPermission()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }
    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivityForResult(intent, 343)
    }

    //Iniciar el servicio autoclicker
    private fun startAutoclickerService(context: Context) {
        if (isAccessibilityServiceEnabled(context, AutoclickerService::class.java)) {
            val intent = Intent(context, AutoclickerService::class.java)
            context.startService(intent)
        } else {
            // El servicio de accesibilidad no está habilitado, muestra un mensaje de error o solicita al usuario que lo habilite.
            Toast.makeText(context, "El servicio de accesibilidad no esta habilitado, no se puede hacer la ejecucion automatica correctamente", Toast.LENGTH_SHORT).show()
            stopService(Intent(applicationContext, DisableTouchService::class.java))
        }
    }


    //Ejecutar aplicacion 911
    private fun exec911() {
        //Nombre interno de la aplicacion 911
        val launchIntent = packageManager.getLaunchIntentForPackage("com.c4bc.alerta066m")
        if (launchIntent != null) {
            sharedPref.edit().putString("autoExec", "si").apply()
            launchIntent.setClassName("com.c4bc.alerta066m", "com.c4bc.alerta066m.activities.Splash")
            //Ejecuta la app 911MovilBC
            startActivity(launchIntent)
            //Ejecuta el servicio que deshabilita las pulsaciones del usuario en caso de que el telefono movil se encuentre en el bolsillo
            startService(Intent(this, DisableTouchService::class.java))
            //Ejecuta el servicio encargado de las pulsaciones automaticas
            startAutoclickerService(getApplicationContext())
        } else {
            //Envia mensaje cuando no se pueda encontrar la aplicacion
            Toast.makeText(this@MainActivity, "La aplicacion no se encuentra o no esta instalada en el dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun execSOS(){
        ubi = getLocationLink(this)
        val cc = getSelectedContactNumber()
        val ma = sharedPref.getString("modoActivo", "911MovilBC")
        if (cc != "error")
        {
            sendSMS(cc, "¡Necesito Ayuda!"+" "+ubi,this)
            //Toast.makeText(this, "OOF"+ubi, Toast.LENGTH_LONG).show()
        }
        else{
            Toast.makeText(this, "Se necesita indicar un contacto de confianza para enviar mensajes", Toast.LENGTH_LONG).show()
        }
        //Hacer un chequeo de opcion aqui?
        if(ma=="911MovilBC"){
            exec911()
        }
        if(ma=="Med-Track"){
            execMedTrack()
        }
        if(ma=="Boton Emergencia Tijuana"){
            exec911Tijuana()
        }
    }

    //Ejecutar aplicacion MedTrak
    private fun execMedTrack() {
        //Nombre interno de la aplicacion MedTrack
        val launchIntent2 = packageManager.getLaunchIntentForPackage("med.track.med")
        if (launchIntent2 != null) {
            sharedPref.edit().putString("autoExec", "si").apply()
            launchIntent2.setClassName("med.track.med", "io.ionic.starter.MainActivity")
            startActivity(launchIntent2)
            
            //Ejecuta el servicio encargado de las pulsaciones automaticas
            startAutoclickerService(getApplicationContext())
        } else {
            //Envia mensaje cuando no se pueda encontrar la aplicacion
            Toast.makeText(this@MainActivity, "La aplicacion no se encuentra o no esta instalada en el dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    //Ejecutar aplicacion Boton Emergencia Tijuana
    private fun exec911Tijuana() {
        //Nombre interno de la aplicacion Boton Emergencia Tijuana
        val launchIntent3 = packageManager.getLaunchIntentForPackage("com.tijuana.emergencia")
        if (launchIntent3 != null) {
            sharedPref.edit().putString("autoExec", "si").apply()
            launchIntent3.setClassName("com.tijuana.emergencia", "com.tijuana.emergencia.MainActivity")
            startActivity(launchIntent3)

            //Ejecuta el servicio encargado de las pulsaciones automaticas
            startAutoclickerService(getApplicationContext())
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
    fun ejecutarConDelay(context: Context) {
        val handler = Handler()
        handler.postDelayed({
            desbloquearPantalla(context)
            execSOS()
        }, 7000) // Delay de 5000 milisegundos (5 segundos)
    }

    fun desbloquearPantalla(context: Context) {
        // Verificar si la pantalla ya está encendida
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            // Encender la pantalla
            encenderPantalla(context)
        }
    }

    //Encender la pantalla
    fun encenderPantalla(context: Context) {
        // Obtener el servicio de administración de energía
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Verificar si la pantalla ya está encendida
        if (!powerManager.isInteractive) {
            // Obtener una referencia al administrador de ventanas
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Crear una nueva ventana para encender la pantalla
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SmartSOS:SmartSOSWakeLock"
            )

            // Adquirir el bloqueo de la pantalla
            wakeLock.acquire()

            // Liberar el bloqueo después de un corto período de tiempo (por ejemplo, 5 segundos)
            wakeLock.release(5000)

            // Encender la pantalla estableciendo el brillo a 0
            val activity = context as? Activity
            activity?.window?.let { window ->
                val layoutParams = window.attributes
                layoutParams.screenBrightness = 0f
                window.attributes = layoutParams
            }
        }
    }

    //Obtener contacto de confianza
    // Crear una variable para almacenar el ID del contacto seleccionado
    var contactId: String? = null

    // Guardar el contactId en SharedPreferences
    fun saveContactId(contactId: String?) {
        val editor = sharedPref.edit()
        editor.putString("contactId", contactId)
        editor.apply()
    }

    // Cargar el contactId desde SharedPreferences
    fun loadContactId(): String {
        return sharedPref.getString("contactId", "error") ?: "error"
    }

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
        // Cargar el contactId desde SharedPreferences
        contactId = loadContactId()

        // Verificar si tenemos el ID del contacto seleccionado
        if (contactId == "error") {
            // Si no tenemos el ID, mostrar un mensaje de error
            val contactMsgShow = AlertDialog.Builder(this)
            contactMsgShow.setTitle("Hubo un problema")
            contactMsgShow.setMessage("Aun no se ha asignado un contacto de confianza")
            contactMsgShow.setPositiveButton("Aceptar") { dialog, which ->
                // Acción que se realiza al pulsar el botón "Aceptar"
            }
            contactMsgShow.setNegativeButton("Asignar contacto") { dialog, which ->
                // Acción que se realiza al pulsar el botón "Editar"
                getContactInfo()
            }
            val dialog = contactMsgShow.create()
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

                    // Mostrar un mensaje con el nombre y números obtenidos
                    val contactMsgShow = AlertDialog.Builder(this)
                    contactMsgShow.setTitle("Contacto de confianza actual")
                    contactMsgShow.setMessage("$name $phoneNumber")
                    contactMsgShow.setPositiveButton("Aceptar") { dialog, which ->
                        // Acción que se realiza al pulsar el botón "Aceptar"
                    }
                    contactMsgShow.setNegativeButton("Editar contacto") { dialog, which ->
                        // Acción que se realiza al pulsar el botón "Editar"
                        getContactInfo()
                    }
                    val dialog = contactMsgShow.create()
                    dialog.show()
                }
                cursor.close()
            }
        }
    }

    fun getSelectedContactNumber(): String {
        // Cargar el contactId desde SharedPreferences
        contactId = loadContactId()

        // Verificar si tenemos el ID del contacto seleccionado
        if (contactId == "error") {
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
                    cursor.close()
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
                        // Guardar el contactId en SharedPreferences
                        saveContactId(contactId)
                    }
                    cursor.close()
                    Toast.makeText(this, "Contacto de confianza guardado: " + getSelectedContactNumber(), Toast.LENGTH_LONG).show()
                }
            }
        }

        //Mostrar sobre otras apps
        if (requestCode == 343) {
            if (Settings.canDrawOverlays(this)) {
                // El permiso ha sido concedido por el usuario
            } else {
                // El permiso ha sido denegado por el usuario
                showPermissionErrorDialog()
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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0f, locationListener)

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
            if (message == "1" && isButtonPressDetected == false) {
                desbloquearPantalla(this@MainActivity)
                execSOS()
                //Actualmente el boton manda multiples 1, solo hacemos caso a uno
                isButtonPressDetected = true


                //Comentar el codigo de abajo en caso de que no se quieran permitir multiples pulsaciones (de por si se supone que no es un juguete)
                val handler = Handler()
                handler.postDelayed({
                    //resetea la variable para que se puedan recibir pulsaciones otra vez
                    isButtonPressDetected = false
                }, 10000) // Delay de 10000 milisegundos (10 segundos)


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

        val ciudadActual = sharedPref.getString("ciudadActual", "")
        // Verificar si la ciudad actual está vacía
        if (ciudadActual.isNullOrEmpty()) {
            // La ciudad actual está vacía, ejecutar la función mostrarDialogoSeleccionCiudad
            mostrarDialogoSeleccionCiudad(this, sharedPref)
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
        sharedPref.edit().putString("autoExec", "no").apply()
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
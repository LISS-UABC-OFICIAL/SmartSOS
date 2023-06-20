package com.liss.smartsos

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BkgLocService : Service() {

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val latitude = location.latitude
                val longitude = location.longitude
                val mapsLink = "https://www.google.com/maps?q=$latitude,$longitude"
                Log.d("BkgLocService onLocationChanged()", "Localizacion inicial: Actualizacion - $mapsLink")
                sendLocationLink(mapsLink) // Envía el enlace de ubicación a la actividad
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "BkgLocServiceChannel",
                "SmartSOS - Servicio de ubicacion",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Adquiere el WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BkgLocService:WakeLock"
        )
        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1,createNotification())
        requestLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeLocationUpdates()
        // Libera el WakeLock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private fun createNotification(): Notification {
        val notificationBuilder = NotificationCompat.Builder(this, "BkgLocServiceChannel")
            .setContentTitle("SmartSOS")
            .setContentText("Obteniendo ubicacion..")

        return notificationBuilder.build()
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0f, locationListener)
        }
    }

    private fun removeLocationUpdates() {
        locationManager.removeUpdates(locationListener)
    }

    private fun sendLocationLink(link: String) {
        val intent = Intent("LOCATION_LINK_ACTION")
        intent.putExtra("locationLink", link)
        sendBroadcast(intent)
    }
}
package com.example.batterymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private val CHANNEL_ID = "BatteryMonitorChannel"
    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var batteryManager: BatteryManager

    override fun onCreate() {
        super.onCreate()
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Batarya Takibi Aktif")
            .setContentText("Anlık akım verisi okunuyor...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        acquireWakeLock()
        BatteryDataRepository.isTracking.value = true
        startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        serviceJob?.cancel()
        serviceJob = coroutineScope.launch {
            while (isActive) {
                // Önce daha stabil olan Ortalama (Average) akımı okumayı deneriz
                var currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                
                // Eğer cihaz o an Average okuyamazsa (0 dönerse), anlık değere geri düşeriz
                if (currentMicroAmps == 0) {
                    currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                }
                
                var currentMilliAmps = currentMicroAmps / 1000f 
                
                // Xiaomi için şarj yönünü sabitleme
                val isCharging = batteryManager.isCharging
                currentMilliAmps = Math.abs(currentMilliAmps)
                
                if (!isCharging) {
                    currentMilliAmps = -currentMilliAmps
                }
                
                BatteryDataRepository.addCurrentValue(currentMilliAmps)
                
                // Daha stabil bir okuma için süreyi 2 saniyeye çıkarıyoruz (2000L)
                delay(2000L) 
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryMonitor::WakeLock").apply {
            acquire(10 * 60 * 1000L /*10 Dakika*/)
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        BatteryDataRepository.isTracking.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

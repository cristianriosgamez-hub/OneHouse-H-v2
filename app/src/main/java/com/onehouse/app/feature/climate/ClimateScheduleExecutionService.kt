package com.onehouse.app.feature.climate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Ejecuta una programación de climatización como servicio foreground de corta
 * duración. Esto evita depender de la ventana temporal de un BroadcastReceiver
 * cuando Android/HyperOS ha mantenido OneHouse mucho tiempo en segundo plano.
 */
class ClimateScheduleExecutionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var runningStartId: Int = 0

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runningStartId = startId
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        val eventId = intent?.getLongExtra(EXTRA_EVENT_ID, -1L) ?: -1L
        ClimateScheduleExecutionTrace.stage(applicationContext, "SERVICE", eventId)
        if (eventId <= 0L) {
            finishExecution(startId)
            return START_NOT_STICKY
        }

        runCatching {
            ClimateScheduleKnxCommandRunner(applicationContext).execute(eventId) {
                finishExecution(startId)
            }
        }.onFailure {
            ClimateScheduleExecutionTrace.stage(applicationContext, "ERROR", eventId)
            ClimateBackgroundScheduler(applicationContext).reschedule()
            finishExecution(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        wakeLock = runCatching {
            getSystemService(PowerManager::class.java)
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OneHouse:ClimateScheduleExecution"
                )
                .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
        }.getOrNull()
    }

    @Synchronized
    private fun finishExecution(startId: Int) {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf(startId)
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Programación de climatización",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ejecución puntual de horarios KNX de climatización"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("OneHouse")
            .setContentText("Ejecutando programación de climatización")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val EXTRA_EVENT_ID = "climate_schedule_execution_event_id"
        private const val CHANNEL_ID = "onehouse_climate_schedule_execution"
        private const val NOTIFICATION_ID = 7041
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 120_000L
    }
}

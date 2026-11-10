package com.onehouse.app.knx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onehouse.app.MainActivity
import com.onehouse.app.R

/**
 * Mantiene vivo exclusivamente el proceso de carga inicial KNX mientras quedan
 * direcciones o reintentos pendientes.
 *
 * La lectura y su estado siguen perteneciendo al motor KNX (KnxBulkStateReader /
 * KnxLoadProgressRepository). Este servicio no inicia una segunda carga ni guarda
 * una copia del porcentaje: solo evita que Android congele el trabajo cuando
 * OneHouse pasa a segundo plano porque el usuario abre otra aplicación.
 */
class KnxInitialLoadForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "knx_initial_load"
        private const val NOTIFICATION_ID = 1305

        fun start(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                val serviceIntent = Intent(appContext, KnxInitialLoadForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, KnxInitialLoadForegroundService::class.java)
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("OneHouse")
            .setContentText("Cargando estados KNX…")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Carga KNX",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activa la carga inicial de estados KNX en segundo plano"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }
}

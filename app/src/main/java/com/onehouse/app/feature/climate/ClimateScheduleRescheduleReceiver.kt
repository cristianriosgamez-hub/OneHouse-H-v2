package com.onehouse.app.feature.climate

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reconstruye la siguiente alarma de climatización después de reiniciar el
 * teléfono, actualizar OneHouse o cambiar la hora/zona horaria del sistema.
 */
class ClimateScheduleRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                ClimateBackgroundScheduler(context.applicationContext).reschedule()
            }
        }
    }
}

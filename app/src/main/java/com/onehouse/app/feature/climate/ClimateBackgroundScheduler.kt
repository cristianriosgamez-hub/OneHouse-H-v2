package com.onehouse.app.feature.climate

import com.onehouse.app.feature.h08.H08Safety

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

/**
 * Programa únicamente la siguiente orden de climatización.
 *
 * REV2: AlarmManager entrega la alarma directamente al ForegroundService mediante
 * un PendingIntent de servicio. Esto elimina el salto intermedio por un
 * BroadcastReceiver para el caso en que el proceso de OneHouse ya no exista.
 * Las alarmas exactas iniciadas por el sistema pueden levantar el servicio y el
 * propio servicio adquiere un PARTIAL_WAKE_LOCK antes de ejecutar KNX.
 */
class ClimateBackgroundScheduler(
    private val context: Context,
    private val repository: ClimateScheduleRepository =
        SharedPreferencesClimateScheduleRepository(context)
) {
    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun reschedule() {
        if (H08Safety.READ_ONLY) return
        cancel()

        val next = ClimateScheduleEngine.nextExecution(repository.load()) ?: return
        val triggerAtMillis = next.executionTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pendingIntent = executionPendingIntent(next.event.id)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            // Mientras no exista permiso de alarma exacta conservamos un fallback.
            // La pantalla de Programación solicitará el acceso especial y, al
            // volver, reprogramará inmediatamente como exacta.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        ClimateScheduleExecutionTrace.scheduled(
            appContext,
            next.event.id,
            triggerAtMillis,
            exact
        )
    }

    fun cancel() {
        // PendingIntent.getForegroundService/getService mantienen la misma
        // identidad aunque cambie el extra eventId. Por eso eventId=0 cancela
        // exactamente la alarma previamente registrada.
        alarmManager.cancel(executionPendingIntent(0L))
        ClimateScheduleExecutionTrace.cancelled(appContext)
    }

    private fun executionPendingIntent(eventId: Long): PendingIntent {
        val intent = Intent(appContext, ClimateScheduleExecutionService::class.java)
            .setAction(ACTION_EXECUTE_CLIMATE_SCHEDULE)
            .putExtra(ClimateScheduleExecutionService.EXTRA_EVENT_ID, eventId)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                appContext,
                REQUEST_CODE,
                intent,
                flags
            )
        } else {
            @Suppress("DEPRECATION")
            PendingIntent.getService(
                appContext,
                REQUEST_CODE,
                intent,
                flags
            )
        }
    }

    private companion object {
        const val REQUEST_CODE = 7040
        const val ACTION_EXECUTE_CLIMATE_SCHEDULE =
            "com.onehouse.app.action.EXECUTE_CLIMATE_SCHEDULE"
    }
}

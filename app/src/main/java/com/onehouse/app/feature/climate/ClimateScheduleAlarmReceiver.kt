package com.onehouse.app.feature.climate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Punto de entrada de las alarmas exactas de climatización.
 *
 * REV1: la orden KNX se inicia directamente desde el BroadcastReceiver mediante
 * goAsync() y un PARTIAL_WAKE_LOCK acotado. En algunos dispositivos HyperOS,
 * startForegroundService() puede aceptar la petición sin arrancar el servicio
 * inmediatamente mientras la aplicación está dormida. Eso deja la programación
 * pendiente hasta que el proceso vuelve a primer plano.
 *
 * Ejecutar desde el receiver evita ese segundo salto. El runner conserva la
 * ejecución KNX secuencial y vuelve a programar el siguiente evento al terminar.
 */
class ClimateScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        ClimateScheduleExecutionTrace.stage(appContext, "RECEIVER", eventId)

        if (eventId <= 0L) {
            ClimateScheduleExecutionTrace.stage(appContext, "ERROR", eventId)
            ClimateBackgroundScheduler(appContext).reschedule()
            return
        }

        executeInsideReceiver(appContext, eventId)
    }

    private fun executeInsideReceiver(context: Context, eventId: Long) {
        val pendingResult = goAsync()
        val wakeLock = runCatching {
            context.getSystemService(PowerManager::class.java)
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OneHouse:ClimateScheduleAlarm"
                )
                .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
        }.getOrNull()

        val finishOnce = object {
            private var finished = false

            @Synchronized
            fun finish() {
                if (finished) return
                finished = true
                runCatching {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
                pendingResult.finish()
            }
        }

        runCatching {
            ClimateScheduleExecutionTrace.stage(context, "DIRECT_EXECUTION", eventId)
            ClimateScheduleKnxCommandRunner(context).execute(eventId) {
                finishOnce.finish()
            }
        }.onFailure {
            ClimateScheduleExecutionTrace.stage(context, "ERROR", eventId)
            ClimateBackgroundScheduler(context).reschedule()
            finishOnce.finish()
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "climate_schedule_event_id"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 120_000L
    }
}

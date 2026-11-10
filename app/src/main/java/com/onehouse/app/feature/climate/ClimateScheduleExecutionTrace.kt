package com.onehouse.app.feature.climate

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trazabilidad ligera de la programación, separada del backup funcional. */
object ClimateScheduleExecutionTrace {
    private const val PREFS = "onehouse_climate_schedule_trace"
    private const val KEY_NEXT = "next_alarm_at"
    private const val KEY_EXACT = "next_alarm_exact"
    private const val KEY_LAST_STAGE = "last_stage"
    private const val KEY_LAST_STAGE_AT = "last_stage_at"
    private const val KEY_LAST_EVENT = "last_event_id"

    fun scheduled(context: Context, eventId: Long, atMillis: Long, exact: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_NEXT, atMillis)
            .putBoolean(KEY_EXACT, exact)
            .putLong(KEY_LAST_EVENT, eventId)
            .apply()
    }

    fun cancelled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_NEXT).remove(KEY_EXACT).apply()
    }

    fun stage(context: Context, stage: String, eventId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_STAGE, stage)
            .putLong(KEY_LAST_STAGE_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_EVENT, eventId)
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            nextAlarmAt = p.getLong(KEY_NEXT, 0L).takeIf { it > 0L },
            exact = p.getBoolean(KEY_EXACT, false),
            lastStage = p.getString(KEY_LAST_STAGE, null),
            lastStageAt = p.getLong(KEY_LAST_STAGE_AT, 0L).takeIf { it > 0L }
        )
    }

    data class Snapshot(
        val nextAlarmAt: Long?,
        val exact: Boolean,
        val lastStage: String?,
        val lastStageAt: Long?
    ) {
        fun nextLabel(): String = nextAlarmAt?.let(::format) ?: "---"
        fun lastLabel(): String = lastStageAt?.let { "${stageLabel(lastStage)} · ${format(it)}" } ?: "---"
    }

    private fun format(value: Long): String =
        SimpleDateFormat("dd/MM HH:mm:ss", Locale("es", "ES")).format(Date(value))

    private fun stageLabel(stage: String?): String = when (stage) {
        "ALARM_REGISTERED" -> "Alarma registrada"
        "RECEIVER" -> "Alarma recibida"
        "DIRECT_EXECUTION" -> "Ejecución directa iniciada"
        "SERVICE" -> "Servicio iniciado"
        "KNX_START" -> "Comando KNX iniciado"
        "KNX_FINISH" -> "Ejecución finalizada"
        "FALLBACK" -> "Fallback receiver"
        "ERROR" -> "Error de ejecución"
        else -> stage ?: "---"
    }
}

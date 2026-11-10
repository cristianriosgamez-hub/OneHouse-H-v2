package com.onehouse.app.feature.climate

import android.content.Context
import java.time.DayOfWeek

/**
 * Persistencia local ligera, sin dependencias externas.
 *
 * Los horarios viven dentro de OneHouse. En una futura migración a Room,
 * esta interfaz puede mantenerse y sustituirse únicamente la implementación.
 */
interface ClimateScheduleRepository {
    fun load(): ClimateScheduleState
    fun save(state: ClimateScheduleState)
}

class SharedPreferencesClimateScheduleRepository(
    context: Context
) : ClimateScheduleRepository {

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): ClimateScheduleState {
        val enabled = preferences.getBoolean(KEY_GLOBAL_ENABLED, false)
        val serializedEvents = preferences.getStringSet(KEY_EVENTS, emptySet()).orEmpty()

        val events = serializedEvents.mapNotNull(::decodeEvent)
            .sortedWith(
                compareBy<ClimateScheduleEvent>(
                    { it.dayOfWeek.value },
                    { it.hour },
                    { it.minute }
                )
            )

        return ClimateScheduleState(
            globallyEnabled = enabled,
            events = events
        )
    }

    override fun save(state: ClimateScheduleState) {
        preferences.edit()
            .putBoolean(KEY_GLOBAL_ENABLED, state.globallyEnabled)
            .putStringSet(KEY_EVENTS, state.events.map(::encodeEvent).toSet())
            .apply()
    }

    private fun encodeEvent(event: ClimateScheduleEvent): String =
        listOf(
            event.id,
            event.dayOfWeek.value,
            event.hour,
            event.minute,
            event.enabled,
            event.powerOn,
            event.targetTemperature,
            event.mode.name,
            event.fanSpeed.name
        ).joinToString(SEPARATOR)

    private fun decodeEvent(value: String): ClimateScheduleEvent? {
        return runCatching {
            val parts = value.split(SEPARATOR)
            ClimateScheduleEvent(
                id = parts[0].toLong(),
                dayOfWeek = DayOfWeek.of(parts[1].toInt()),
                hour = parts[2].toInt(),
                minute = parts[3].toInt(),
                enabled = parts[4].toBoolean(),
                powerOn = parts[5].toBoolean(),
                targetTemperature = parts[6].toFloat(),
                mode = ClimateMode.valueOf(parts[7]),
                fanSpeed = FanSpeed.valueOf(parts[8])
            )
        }.getOrNull()
    }

    private companion object {
        const val PREFERENCES_NAME = "onehouse_climate_schedule"
        const val KEY_GLOBAL_ENABLED = "global_enabled"
        const val KEY_EVENTS = "events"
        const val SEPARATOR = "|"
    }
}

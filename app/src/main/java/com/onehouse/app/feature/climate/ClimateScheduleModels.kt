package com.onehouse.app.feature.climate

import java.time.DayOfWeek
import java.time.LocalDateTime

data class ClimateScheduleEvent(
    val id: Long = System.currentTimeMillis(),
    val dayOfWeek: DayOfWeek,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val powerOn: Boolean = true,
    val targetTemperature: Float = 22.5f,
    val mode: ClimateMode = ClimateMode.COLD,
    val fanSpeed: FanSpeed = FanSpeed.MEDIUM
) {
    val minutesOfDay: Int
        get() = hour * 60 + minute

    fun formattedTime(): String =
        "%02d:%02d".format(hour, minute)
}

data class ClimateScheduleState(
    val globallyEnabled: Boolean = false,
    val events: List<ClimateScheduleEvent> = emptyList()
)

data class NextClimateSchedule(
    val event: ClimateScheduleEvent,
    val executionTime: LocalDateTime
)

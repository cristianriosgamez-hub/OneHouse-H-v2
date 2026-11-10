package com.onehouse.app.feature.climate

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

object ClimateScheduleEngine {

    fun nextExecution(
        state: ClimateScheduleState,
        from: LocalDateTime = LocalDateTime.now()
    ): NextClimateSchedule? {
        if (!state.globallyEnabled) return null

        return state.events
            .asSequence()
            .filter { it.enabled }
            .map { event ->
                NextClimateSchedule(
                    event = event,
                    executionTime = nextDateTime(event, from)
                )
            }
            .minByOrNull { it.executionTime }
    }

    private fun nextDateTime(
        event: ClimateScheduleEvent,
        from: LocalDateTime
    ): LocalDateTime {
        val candidateDate = from.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(event.dayOfWeek))

        var candidate = LocalDateTime.of(
            candidateDate,
            LocalTime.of(event.hour, event.minute)
        )

        if (!candidate.isAfter(from)) {
            candidate = candidate.plusWeeks(1)
        }

        return candidate
    }
}

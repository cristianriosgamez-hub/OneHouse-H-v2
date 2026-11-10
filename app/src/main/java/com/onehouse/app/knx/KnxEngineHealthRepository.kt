package com.onehouse.app.knx

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Estado de salud calculado del motor KNX central.
 *
 * No abre conexiones ni genera tráfico de bus. Se limita a observar la caché
 * compartida y ofrece una lectura homogénea para diagnóstico, mantenimiento y
 * futuras alertas internas.
 */
class KnxEngineHealthRepository internal constructor(
    private val stateRepository: KnxStateRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {

    enum class Status {
        /** Todavía no se ha recibido ningún telegrama válido. */
        NO_DATA,

        /** Se han recibido telegramas recientemente. */
        ACTIVE,

        /** Hay datos válidos, pero el bus lleva un tiempo sin actividad. */
        IDLE,

        /** Solo quedan estados antiguos respecto al umbral de vigilancia. */
        STALE
    }

    data class Snapshot(
        val status: Status,
        val totalStates: Int,
        val freshStates: Int,
        val staleStates: Int,
        val lastTelegramAtMillis: Long?,
        val millisecondsSinceLastTelegram: Long?
    ) {
        val hasData: Boolean
            get() = totalStates > 0
    }

    /** Flujo derivado de la caché central, sin encuestas ni lecturas extra. */
    val healthFlow: Flow<Snapshot> = stateRepository.stateFlow
        .map(::buildSnapshot)
        .distinctUntilChanged()

    /** Instantánea inmediata del estado de salud actual. */
    fun snapshot(): Snapshot = buildSnapshot(stateRepository.snapshot())

    private fun buildSnapshot(states: Map<String, KnxStateRepository.State>): Snapshot {
        val now = nowProvider()
        val timestamps = states.values
            .map { state -> state.timestampMillis }
            .filter { timestamp -> timestamp > 0L }

        val lastTelegramAtMillis = timestamps.maxOrNull()
        val millisecondsSinceLastTelegram = lastTelegramAtMillis
            ?.let { timestamp -> (now - timestamp).coerceAtLeast(0L) }

        val freshStates = states.values.count { state ->
            isFresh(state = state, nowMillis = now)
        }
        val staleStates = (states.size - freshStates).coerceAtLeast(0)

        val status = when {
            states.isEmpty() || lastTelegramAtMillis == null -> Status.NO_DATA
            millisecondsSinceLastTelegram != null &&
                millisecondsSinceLastTelegram <= ACTIVE_WINDOW_MILLIS -> Status.ACTIVE
            freshStates > 0 -> Status.IDLE
            else -> Status.STALE
        }

        return Snapshot(
            status = status,
            totalStates = states.size,
            freshStates = freshStates,
            staleStates = staleStates,
            lastTelegramAtMillis = lastTelegramAtMillis,
            millisecondsSinceLastTelegram = millisecondsSinceLastTelegram
        )
    }

    companion object {
        /**
         * Un estado se considera reciente durante algo más de dos ciclos de la
         * lectura periódica actual (60 s), evitando falsos avisos puntuales.
         */
        const val FRESH_STATE_WINDOW_MILLIS = 150_000L
        const val ACTIVE_WINDOW_MILLIS = 15_000L

        fun isFresh(
            state: KnxStateRepository.State,
            nowMillis: Long = System.currentTimeMillis()
        ): Boolean = state.timestampMillis > 0L &&
            nowMillis - state.timestampMillis in 0..FRESH_STATE_WINDOW_MILLIS
    }
}

package com.onehouse.app.knx

import android.content.Context

/**
 * Contadores persistentes de diagnóstico del túnel KNX/IP.
 *
 * Son deliberadamente pequeños y acumulativos: permiten comprobar durante
 * pruebas reales si el gateway acepta telegramas, si aparecen paquetes fuera
 * de secuencia y cuál es la latencia del último ACK.
 */
class KnxSessionStatisticsRepository(context: Context) {
    data class Snapshot(
        val connections: Long,
        val connectionErrors: Long,
        val telegramsSent: Long,
        val gatewayAcks: Long,
        val busTelegrams: Long,
        val operationErrors: Long,
        val ignoredAcks: Long,
        val invalidPackets: Long,
        val duplicateIncoming: Long,
        val retransmissions: Long,
        val lastAckMillis: Long?,
        val averageAckMillis: Long?
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            connections = preferences.getLong(KEY_CONNECTIONS, 0),
            connectionErrors = preferences.getLong(KEY_CONNECTION_ERRORS, 0),
            telegramsSent = preferences.getLong(KEY_TELEGRAMS_SENT, 0),
            gatewayAcks = preferences.getLong(KEY_GATEWAY_ACKS, 0),
            busTelegrams = preferences.getLong(KEY_BUS_TELEGRAMS, 0),
            operationErrors = preferences.getLong(KEY_OPERATION_ERRORS, 0),
            ignoredAcks = preferences.getLong(KEY_IGNORED_ACKS, 0),
            invalidPackets = preferences.getLong(KEY_INVALID_PACKETS, 0),
            duplicateIncoming = preferences.getLong(KEY_DUPLICATE_INCOMING, 0),
            retransmissions = preferences.getLong(KEY_RETRANSMISSIONS, 0),
            lastAckMillis = preferences.getLong(KEY_LAST_ACK_MILLIS, -1).takeIf { it >= 0 },
            averageAckMillis = preferences.getLong(KEY_ACK_SAMPLE_COUNT, 0).takeIf { it > 0 }?.let { count ->
                preferences.getLong(KEY_ACK_TOTAL_MILLIS, 0) / count
            }
        )
    }

    fun recordConnectionSuccess() = increment(KEY_CONNECTIONS)
    fun recordConnectionError() = increment(KEY_CONNECTION_ERRORS)
    fun recordOperationError() = increment(KEY_OPERATION_ERRORS)

    fun recordOperation(diagnostic: KnxConnectionManager.TelegramDiagnostic, receivedFromBus: Boolean) {
        synchronized(lock) {
            preferences.edit()
                .putLong(KEY_TELEGRAMS_SENT, preferences.getLong(KEY_TELEGRAMS_SENT, 0) + 1)
                .putLong(KEY_GATEWAY_ACKS, preferences.getLong(KEY_GATEWAY_ACKS, 0) + if (diagnostic.gatewayAcknowledged) 1 else 0)
                .putLong(KEY_BUS_TELEGRAMS, preferences.getLong(KEY_BUS_TELEGRAMS, 0) + if (receivedFromBus) 1 else 0)
                .putLong(KEY_IGNORED_ACKS, preferences.getLong(KEY_IGNORED_ACKS, 0) + diagnostic.ignoredAckCount)
                .putLong(KEY_INVALID_PACKETS, preferences.getLong(KEY_INVALID_PACKETS, 0) + diagnostic.invalidPacketCount)
                .putLong(KEY_DUPLICATE_INCOMING, preferences.getLong(KEY_DUPLICATE_INCOMING, 0) + diagnostic.duplicateIncomingCount)
                .putLong(KEY_RETRANSMISSIONS, preferences.getLong(KEY_RETRANSMISSIONS, 0) + (diagnostic.transmissionAttempts - 1).coerceAtLeast(0))
                .apply {
                    diagnostic.gatewayRoundTripMillis?.let { elapsed ->
                        putLong(KEY_LAST_ACK_MILLIS, elapsed)
                        putLong(KEY_ACK_TOTAL_MILLIS, preferences.getLong(KEY_ACK_TOTAL_MILLIS, 0) + elapsed)
                        putLong(KEY_ACK_SAMPLE_COUNT, preferences.getLong(KEY_ACK_SAMPLE_COUNT, 0) + 1)
                    }
                }
                .apply()
        }
    }

    fun clear() = synchronized(lock) { preferences.edit().clear().apply() }

    private fun increment(key: String) = synchronized(lock) {
        preferences.edit().putLong(key, preferences.getLong(key, 0) + 1).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "knx_session_statistics"
        const val KEY_CONNECTIONS = "connections"
        const val KEY_CONNECTION_ERRORS = "connection_errors"
        const val KEY_TELEGRAMS_SENT = "telegrams_sent"
        const val KEY_GATEWAY_ACKS = "gateway_acks"
        const val KEY_BUS_TELEGRAMS = "bus_telegrams"
        const val KEY_OPERATION_ERRORS = "operation_errors"
        const val KEY_IGNORED_ACKS = "ignored_acks"
        const val KEY_INVALID_PACKETS = "invalid_packets"
        const val KEY_DUPLICATE_INCOMING = "duplicate_incoming"
        const val KEY_RETRANSMISSIONS = "retransmissions"
        const val KEY_LAST_ACK_MILLIS = "last_ack_millis"
        const val KEY_ACK_TOTAL_MILLIS = "ack_total_millis"
        const val KEY_ACK_SAMPLE_COUNT = "ack_sample_count"
        val lock = Any()
    }
}

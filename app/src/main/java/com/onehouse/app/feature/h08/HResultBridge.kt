package com.onehouse.app.feature.h08

import com.onehouse.app.knx.KnxConnectionManager
import com.onehouse.app.knx.KnxGroupAddress
import com.onehouse.app.knx.KnxStateRepository
import com.onehouse.knxlab.knx.GaStatus
import com.onehouse.knxlab.knx.RunSnapshot

/**
 * Sólo estados procedentes de GroupValueResponse identificadas por H.
 * No transforma ausencia de eventos en valores de lluvia.
 */
internal object HResultBridge {
    fun mergeReadOnly(run: RunSnapshot, repository: KnxStateRepository): Int {
        if (run.cancelled || run.connectionError != null) return 0
        var imported = 0
        run.states.forEach { measurement ->
            if (measurement.status != GaStatus.RECEIVED || measurement.groupRequests == 0) return@forEach
            // Sensor de lluvia: OneHouse lo presenta por eventos. No forzamos su estado
            // desde las respuestas de diagnóstico del laboratorio.
            if (measurement.address == "15/0/15" || measurement.address == "15/0/13") return@forEach
            val raw = measurement.rawHex
            if (raw.isBlank() || raw.length % 2 != 0) return@forEach
            val payload = runCatching { raw.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
                .getOrNull() ?: return@forEach
            val destination = runCatching { KnxGroupAddress.parse(measurement.address) }
                .getOrNull() ?: return@forEach
            val bool = if (payload.size == 1 && (payload[0].toInt() and 0xFF) in 0..1) {
                payload[0].toInt() == 1
            } else null
            val telegram = KnxConnectionManager.IncomingGroupTelegram(
                kind = KnxConnectionManager.IncomingGroupTelegram.Kind.RESPONSE,
                sourceAddress = measurement.source,
                destination = destination,
                booleanValue = bool,
                payload = payload,
                messageCode = 0x29,
                apci = "GroupValueResponse",
                cemiHex = ""
            )
            if (repository.record(telegram)) imported++
        }
        return imported
    }
}

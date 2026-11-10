package com.onehouse.app.feature.more

/** Presentation only: never changes KNX reads, stored values or load progress. */
internal enum class KnxDiagnosticReadPolicy(val missingLabel: String, val explanation: String) {
    STATE("○ SIN RESPUESTA", "Estado/lectura: se espera respuesta a la lectura KNX."),
    COMMAND("○ MANDO", "Mando/escritura: puede no responder a una lectura; no indica un fallo."),
    EVENT("○ ESPERANDO EVENTO", "Sensor por evento: la ausencia de valor inicial no indica un fallo. Esperando un cambio de estado.");

    fun label(hasValue: Boolean): String = if (hasValue) "● VALOR RECIBIDO" else missingLabel

    fun readWithoutValueMessage(): String = when (this) {
        STATE -> "Lectura enviada; sin respuesta confirmada"
        COMMAND -> "Lectura enviada a mando; no se requiere respuesta"
        EVENT -> "Lectura enviada; sensor a la espera de evento"
    }
}

internal fun diagnosticReadPolicy(
    kind: String,
    address: String,
    eventAddresses: Set<String>
): KnxDiagnosticReadPolicy = when {
    // Only explicitly known event addresses. Do not exempt all sensors/alarms.
    address in eventAddresses -> KnxDiagnosticReadPolicy.EVENT
    // Mixed read/write objects still have a readable state (e.g. Bloqueo PIR).
    (kind.contains("mando", ignoreCase = true) || kind.contains("escritura", ignoreCase = true)) &&
        !kind.contains("lectura", ignoreCase = true) &&
        !kind.contains("estado", ignoreCase = true) -> KnxDiagnosticReadPolicy.COMMAND
    else -> KnxDiagnosticReadPolicy.STATE
}

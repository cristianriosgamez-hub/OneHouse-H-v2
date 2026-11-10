package com.onehouse.app.knx

/**
 * Estado conocido de un objeto KNX. La fuente permite diferenciar valores
 * confirmados por el bus de estados asumidos tras un envío local.
 */
data class KnxDeviceState(
    val deviceId: String,
    val value: String?,
    val source: Source,
    val updatedAtMillis: Long
) {
    enum class Source {
        UNKNOWN,
        LOCAL_COMMAND,
        BUS_RESPONSE
    }

    val displayValue: String
        get() = value ?: "Sin estado"
}

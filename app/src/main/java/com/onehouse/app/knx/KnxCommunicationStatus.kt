package com.onehouse.app.knx

/** Calidad estimada del último estado conocido de un dispositivo. */
enum class KnxCommunicationStatus(val displayName: String) {
    NEVER_READ("Nunca consultado"),
    PENDING("Pendiente de confirmar"),
    ONLINE("Confirmado por el bus"),
    STALE("Estado antiguo")
}

fun KnxDeviceState.communicationStatus(
    nowMillis: Long = System.currentTimeMillis(),
    freshWindowMillis: Long = 2 * 60 * 1000L
): KnxCommunicationStatus = when (source) {
    KnxDeviceState.Source.UNKNOWN -> KnxCommunicationStatus.NEVER_READ
    KnxDeviceState.Source.LOCAL_COMMAND -> KnxCommunicationStatus.PENDING
    KnxDeviceState.Source.BUS_RESPONSE -> {
        if (nowMillis - updatedAtMillis <= freshWindowMillis) {
            KnxCommunicationStatus.ONLINE
        } else {
            KnxCommunicationStatus.STALE
        }
    }
}

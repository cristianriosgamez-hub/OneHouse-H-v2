package com.onehouse.app.data.knx

data class KnxSettings(
    val localIp: String = "192.168.1.10",
    val localPort: String = "3671",
    val remoteIp: String = "",
    val remotePort: String = "3671",
    val autoReconnect: Boolean = true,
    val lastUpdatedEpochMillis: Long = 0L,
    val lastTestEpochMillis: Long = 0L,
    val lastConnectionStatus: KnxConnectionStatus = KnxConnectionStatus.NOT_TESTED,
    val lastStatusMessage: String = "Sin comprobar",
    val lastTestEndpoint: String = ""
)

enum class KnxConnectionStatus {
    NOT_TESTED,
    TESTING,
    CONNECTED,
    FAILED
}

data class KnxSettingsValidation(
    val localIpError: String? = null,
    val localPortError: String? = null,
    val remoteIpError: String? = null,
    val remotePortError: String? = null
) {
    val isValid: Boolean
        get() = localIpError == null && localPortError == null &&
            remoteIpError == null && remotePortError == null
}

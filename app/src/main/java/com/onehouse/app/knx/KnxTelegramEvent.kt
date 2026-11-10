package com.onehouse.app.knx

/** Registro visible de una operación KNX realizada por OneHouse. */
data class KnxTelegramEvent(
    val id: Long,
    val timestampMillis: Long,
    val direction: Direction,
    val kind: Kind,
    val groupAddress: String?,
    val value: String?,
    val status: Status,
    val detail: String? = null
) {
    enum class Direction { OUTGOING, INCOMING, SYSTEM }
    enum class Kind { CONNECT, GROUP_VALUE_READ, GROUP_VALUE_WRITE, GROUP_VALUE_RESPONSE, DISCONNECT }
    enum class Status { PENDING, CONFIRMED, RECEIVED, ERROR }
}

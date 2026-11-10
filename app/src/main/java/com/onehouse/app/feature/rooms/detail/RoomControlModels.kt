package com.onehouse.app.feature.rooms.detail

enum class RoomType {
    ENTRANCE,
    HALLWAY,
    STORAGE,
    BATHROOM,
    KITCHEN,
    BEDROOM_1,
    DINING_ROOM,
    SUITE
}

enum class BlindCommand {
    UP,
    STOP,
    DOWN
}

data class RoomControlState(
    val mainLightOn: Boolean = false,
    val secondaryLightOn: Boolean = false,
    val tertiaryLightOn: Boolean = false,
    val temperatureCelsius: Float? = null,
    val pirBlocked: Boolean = false,
    val floodDetected: Boolean = false,
    val lastBlindCommand: BlindCommand = BlindCommand.STOP,
    val blindPositionPercent: Float? = null
)

/**
 * Puerta de enlace preparada para KNX.
 * Sustituir la implementación provisional por el gateway real.
 */
interface RoomKnxGateway {
    fun setMainLight(room: RoomType, enabled: Boolean)
    fun setSecondaryLight(room: RoomType, enabled: Boolean)
    fun setTertiaryLight(room: RoomType, enabled: Boolean)
    fun setPirBlocked(room: RoomType, blocked: Boolean)
    fun moveBlind(room: RoomType, command: BlindCommand)
}

object RoomKnxGatewayProvider {
    var gateway: RoomKnxGateway = object : RoomKnxGateway {
        override fun setMainLight(room: RoomType, enabled: Boolean) = Unit
        override fun setSecondaryLight(room: RoomType, enabled: Boolean) = Unit
        override fun setTertiaryLight(room: RoomType, enabled: Boolean) = Unit
        override fun setPirBlocked(room: RoomType, blocked: Boolean) = Unit
        override fun moveBlind(room: RoomType, command: BlindCommand) = Unit
    }
}

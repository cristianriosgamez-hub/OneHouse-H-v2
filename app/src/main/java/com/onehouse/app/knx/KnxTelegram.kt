package com.onehouse.app.knx

/** Telegramas de grupo soportados por OneHouse. */
sealed interface KnxTelegram {
    val destination: KnxGroupAddress

    data class GroupValueRead(
        override val destination: KnxGroupAddress
    ) : KnxTelegram

    data class GroupValueWriteBoolean(
        override val destination: KnxGroupAddress,
        val value: Boolean
    ) : KnxTelegram

    /** DPT 5.001: porcentaje normalizado 0..100, enviado como 0..255. */
    data class GroupValueWritePercent(
        override val destination: KnxGroupAddress,
        val percent: Double
    ) : KnxTelegram

    /** DPT 9.001: temperatura en coma flotante KNX de 16 bits. */
    data class GroupValueWriteTemperature(
        override val destination: KnxGroupAddress,
        val celsius: Double
    ) : KnxTelegram

    /** DPT 20.x y otros valores enumerados de un byte. */
    data class GroupValueWriteByte(
        override val destination: KnxGroupAddress,
        val value: Int
    ) : KnxTelegram
}

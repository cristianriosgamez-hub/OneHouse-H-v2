package com.onehouse.app.knx

import kotlin.math.roundToInt

/** Codificación cEMI de los telegramas de grupo soportados por OneHouse. */
internal object KnxTelegramEncoder {
    private const val CEMI_L_DATA_REQ = 0x11
    private const val CONTROL_1_STANDARD_FRAME = 0xBC
    private const val CONTROL_2_GROUP_HOP_COUNT_6 = 0xE0

    fun encode(telegram: KnxTelegram): ByteArray {
        val destination = telegram.destination.toBytes()
        val apdu = when (telegram) {
            is KnxTelegram.GroupValueRead -> byteArrayOf(0x00, 0x00)
            is KnxTelegram.GroupValueWriteBoolean -> byteArrayOf(
                0x00,
                (0x80 or if (telegram.value) 0x01 else 0x00).toByte()
            )
            is KnxTelegram.GroupValueWritePercent -> byteArrayOf(
                0x00,
                0x80.toByte(),
                ((telegram.percent.coerceIn(0.0, 100.0) * 255.0 / 100.0).roundToInt()).toByte()
            )
            is KnxTelegram.GroupValueWriteTemperature -> {
                val encoded = encodeKnxFloat16(telegram.celsius)
                byteArrayOf(0x00, 0x80.toByte(), (encoded shr 8).toByte(), encoded.toByte())
            }
            is KnxTelegram.GroupValueWriteByte -> byteArrayOf(
                0x00, 0x80.toByte(), telegram.value.coerceIn(0, 255).toByte()
            )
        }

        return byteArrayOf(
            CEMI_L_DATA_REQ.toByte(),
            0x00,
            CONTROL_1_STANDARD_FRAME.toByte(),
            CONTROL_2_GROUP_HOP_COUNT_6.toByte(),
            0x00, 0x00,
            destination[0], destination[1],
            (apdu.size - 1).toByte()
        ) + apdu
    }

    /** KNX DPT9: valor = 0,01 × mantisa × 2^exponente. */
    private fun encodeKnxFloat16(value: Double): Int {
        var mantissa = (value * 100.0).roundToInt()
        var exponent = 0
        while (mantissa !in -2048..2047 && exponent < 15) {
            mantissa = mantissa shr 1
            exponent++
        }
        require(mantissa in -2048..2047) { "Valor DPT 9 fuera de rango: $value" }
        val sign = if (mantissa < 0) 1 else 0
        val mantissaBits = mantissa and 0x07FF
        return (sign shl 15) or (exponent shl 11) or mantissaBits
    }
}

/** Formato hexadecimal estable para diagnóstico y comparación con ETS/Wireshark. */
internal object KnxHex {
    fun format(bytes: ByteArray): String = bytes.joinToString(" ") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}

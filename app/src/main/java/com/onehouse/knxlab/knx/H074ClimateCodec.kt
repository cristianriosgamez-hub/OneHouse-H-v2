package com.onehouse.knxlab.knx

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** H07.4: codificador/decodificador DPT 9.001, sin dependencias Android.
 * Para la prueba de consigna se admiten SOLO valores positivos entre 16,5 y 34,0 ºC.
 */
object H074ClimateCodec {
    const val COMMAND_GA = "5/2/1"
    const val STATE_GA = "5/2/6"
    const val MIN_C = 16.5
    const val MAX_C = 34.0
    const val STEP_C = 0.5

    fun decode(raw: ByteArray): Double? {
        if (raw.size != 2) return null
        val word = ((raw[0].toInt() and 255) shl 8) or (raw[1].toInt() and 255)
        val exponent = (word ushr 11) and 0x0F
        val mantissa = if ((word and 0x8000) != 0) ((word and 0x07FF) or -0x800) else (word and 0x07FF)
        val value = mantissa * 0.01 * 2.0.pow(exponent)
        return value.takeIf { it.isFinite() }
    }

    fun decodeHex(hex: String): Double? = runCatching {
        if (!Regex("[0-9a-fA-F]{4}").matches(hex)) return null
        decode(byteArrayOf(hex.substring(0, 2).toInt(16).toByte(), hex.substring(2, 4).toInt(16).toByte()))
    }.getOrNull()

    fun encode(value: Double): ByteArray {
        require(value.isFinite() && value in MIN_C..MAX_C) { "Consigna fuera del rango H07.4" }
        var mantissa = (value * 100).roundToInt()
        var exponent = 0
        while (mantissa !in -2048..2047 && exponent < 15) {
            mantissa = mantissa shr 1
            exponent++
        }
        require(mantissa in -2048..2047)
        val sign = if (mantissa < 0) 1 else 0
        val word = (sign shl 15) or (exponent shl 11) or (mantissa and 0x07FF)
        return byteArrayOf((word ushr 8).toByte(), word.toByte())
    }

    fun validBaseline(value: Double?): Boolean = value != null && value.isFinite() &&
        value in MIN_C..MAX_C && abs(value * 2 - (value * 2).roundToInt()) < 0.15

    fun validStep(baseline: Double, target: Double): Boolean = validBaseline(baseline) &&
        target in MIN_C..MAX_C && abs(abs(target - baseline) - STEP_C) < 0.01

    fun matches(observed: Double?, target: Double): Boolean =
        observed != null && observed.isFinite() && abs(observed - target) <= 0.15

    fun text(value: Double?): String = value?.let { java.lang.String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "---"
    fun hex(raw: ByteArray?): String = raw?.joinToString("") { "%02X".format(it.toInt() and 255) } ?: "---"
}

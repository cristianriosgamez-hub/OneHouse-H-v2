package com.onehouse.knxlab.knx

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** H07.7.2: permitir ordenar BAJA->MEDIA tambien con climatizador OFF sin enviar ON.
 *  El valor de mando MEDIO sigue siendo 37% (0x5E) segun configuracion de OneHouse.
 *  En la prueba fisica H07.7, 5/3/5 devolvio 0x80 tras aplicar dicha orden y el
 *  usuario confirmo velocidad MEDIA en el equipo. Solo se reconoce como estado
 *  MEDIA el dato observado 0x80 (+/-1 LSB), no se presupone que la respuesta
 *  repita el payload de mando. La interpretacion de ALTA sigue sin validar.
 */
object H077ClimateFanCodec {
    const val COMMAND_GA = "5/2/5"
    const val STATE_GA = "5/3/5"
    const val LOW_PERCENT = 25
    const val MEDIUM_PERCENT = 37
    const val HIGH_PERCENT = 100
    const val LOW_RAW = 64     // round(25*255/100)
    const val MEDIUM_RAW = 94 // VALOR DE MANDO: round(37*255/100) = 0x5E
    const val MEDIUM_STATE_RAW = 128 // ESTADO OBSERVADO 5/3/5: 0x80 = 50,2% bruto
    const val TOLERANCE_RAW = 1
    fun encode(percent: Int): Int {
        require(percent in 0..100)
        return (percent * 255.0 / 100.0).roundToInt().coerceIn(0,255)
    }
    fun decode(raw: ByteArray): Int? = if (raw.size == 1) raw[0].toInt() and 0xFF else null
    fun decodeHex(hex: String): Int? = if (hex.length == 2) hex.toIntOrNull(16)?.takeIf { it in 0..255 } else null
    fun percent(raw: Int?): Double? = raw?.takeIf { it in 0..255 }?.let { it*100.0/255.0 }
    fun matchesLow(raw: Int?) = raw != null && abs(raw-LOW_RAW) <= TOLERANCE_RAW
    /** Solo para valores de 5/3/5; no usar MEDIUM_RAW de 5/2/5. */
    fun matchesMedium(raw: Int?) = raw != null && abs(raw-MEDIUM_STATE_RAW) <= TOLERANCE_RAW
    /** 5/2/5 envia 37% aunque 5/3/5 informe el 50,2% bruto al seleccionar MEDIA. */
    fun commandName(raw: Int?): String = when (raw) {
        MEDIUM_RAW -> "MEDIA (mando 37 %, 0x5E)"
        LOW_RAW -> "BAJA (mando 25 %, 0x40)"
        255 -> "ALTA (mando 100 %, no ensayado)"
        else -> "Mando desconocido"
    }
    fun name(raw: Int?): String = when {
        matchesLow(raw) -> "BAJA (estado 0x40)"
        matchesMedium(raw) -> "MEDIA (estado 0x80)"
        raw == 255 -> "ALTA (0xFF, pendiente de validar)"
        raw == null -> "---"
        else -> "NO RECONOCIDA"
    }
    /** Requiere lectura inequívoca ON u OFF; solo elimina la exigencia de ON. */
    fun canSetMedium(raw: Int?, powerOn: Boolean?, mode: Int?): Boolean =
        matchesLow(raw) && powerOn != null && mode in setOf(0,9)
    fun hex(raw: Int?): String = if (raw == null) "---" else "%02X".format(Locale.ROOT,raw)
}

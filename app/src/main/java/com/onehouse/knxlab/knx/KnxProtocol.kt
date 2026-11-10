package com.onehouse.knxlab.knx

import java.net.Inet4Address

/** Codec KNXnet/IP del piloto. La lectura H solo emite GroupValueRead.
 * H07.2 permite GroupValueWrite DPT 1.001 para los mandos explícitos
 * de las dos luces de Cocina (1/1/6 y 1/1/5) y DPT 5.001 para posición
 * absoluta de persiana Cocina (2/1/6), tras confirmación y whitelist en el ejecutor.
 * La lista blanca se valida en H07LightControlRunner.Light; sin automatismos.
 */
internal object KnxProtocol {
    const val MAX_PACKET = 512
    const val CONNECT_RES = 0x0206
    const val DISCONNECT_RES = 0x020A
    const val CONNECTIONSTATE_RES = 0x0208
    const val TUNNELLING_REQ = 0x0420
    const val TUNNELLING_ACK = 0x0421

    private fun uint(data: ByteArray, offset: Int) = data[offset].toInt() and 0xFF
    private fun word(data: ByteArray, offset: Int) = (uint(data, offset) shl 8) or uint(data, offset + 1)

    fun gaToRaw(address: String): Int {
        val parts = address.split('/').map { it.toInt() }
        require(parts.size == 3 && parts[0] in 0..31 && parts[1] in 0..7 && parts[2] in 0..255) { "GA inválida: $address" }
        return (parts[0] shl 11) or (parts[1] shl 8) or parts[2]
    }
    private fun rawToGa(raw: Int) = "${(raw ushr 11) and 31}/${(raw ushr 8) and 7}/${raw and 255}"
    fun hex(data: ByteArray): String = data.joinToString(" ") { "%02X".format(it.toInt() and 255) }

    private fun hpai(ip: Inet4Address, port: Int, nat: Boolean): ByteArray =
        byteArrayOf(8, 1) + (if (nat) byteArrayOf(0, 0, 0, 0) else ip.address) +
            byteArrayOf((if (nat) 0 else port).shr(8).toByte(), (if (nat) 0 else port).toByte())

    fun connectRequest(ip: Inet4Address, port: Int, nat: Boolean): ByteArray =
        byteArrayOf(6, 0x10, 2, 5, 0, 0x1A) + hpai(ip, port, nat) + hpai(ip, port, nat) +
            byteArrayOf(4, 4, 2, 0)

    fun disconnectRequest(channel: Int, ip: Inet4Address, port: Int, nat: Boolean): ByteArray =
        byteArrayOf(6, 0x10, 2, 9, 0, 0x10, channel.toByte(), 0) + hpai(ip, port, nat)

    fun connectionStateRequest(channel: Int, ip: Inet4Address, port: Int, nat: Boolean): ByteArray =
        byteArrayOf(6, 0x10, 2, 7, 0, 0x10, channel.toByte(), 0) + hpai(ip, port, nat)

    fun tunnelReadRequest(channel: Int, sequence: Int, address: String): ByteArray {
        val raw = gaToRaw(address)
        // Misma trama cEMI L_Data.req utilizada en OneHouse, solo GroupValueRead.
        val cemi = byteArrayOf(0x11, 0, 0xBC.toByte(), 0xE0.toByte(), 0, 0,
            (raw ushr 8).toByte(), raw.toByte(), 1, 0, 0)
        val len = 10 + cemi.size
        return byteArrayOf(6, 0x10, 4, 0x20, (len ushr 8).toByte(), len.toByte(),
            4, channel.toByte(), sequence.toByte(), 0) + cemi
    }

    /** Paquete de escritura DPT 1.001; no modifica la lectura H.
     * Solo invocable desde el control del piloto y tras verificar destino autorizado.
     */
    fun tunnelWriteBooleanRequest(channel: Int, sequence: Int, address: String, on: Boolean): ByteArray {
        val raw = gaToRaw(address)
        val cemi = byteArrayOf(0x11, 0, 0xBC.toByte(), 0xE0.toByte(), 0, 0,
            (raw ushr 8).toByte(), raw.toByte(), 1, 0, (0x80 or (if (on) 1 else 0)).toByte())
        val length = 10 + cemi.size
        return byteArrayOf(6, 0x10, 4, 0x20, (length ushr 8).toByte(), length.toByte(),
            4, channel.toByte(), sequence.toByte(), 0) + cemi
    }

    /** DPT 5.001 (escala 0..100 %): 1 byte de datos fuera del APCI.
     * Validación de GA mediante allow-list en H072BlindControlRunner (2/1/6).
     */
    fun tunnelWriteScaleRequest(channel: Int, sequence: Int, address: String, percent: Int): ByteArray {
        require(percent in 0..100) { "Porcentaje DPT 5.001 fuera de rango" }
        val raw = gaToRaw(address)
        val encoded = kotlin.math.round(percent * 255.0 / 100.0).toInt().coerceIn(0, 255)
        val cemi = byteArrayOf(0x11, 0, 0xBC.toByte(), 0xE0.toByte(), 0, 0,
            (raw ushr 8).toByte(), raw.toByte(), 2, 0, 0x80.toByte(), encoded.toByte())
        val length = 10 + cemi.size
        return byteArrayOf(6, 0x10, 4, 0x20, (length ushr 8).toByte(), length.toByte(),
            4, channel.toByte(), sequence.toByte(), 0) + cemi
    }

    /** H07.4: una escritura de consigna DPT 9.001 (dos bytes), solo 5/2/1.
     * La lectura H y sus temporizaciones permanecen inalteradas.
     */
    fun tunnelWriteClimateSetpointRequest(channel: Int, sequence: Int, celsius: Double): ByteArray {
        val address = H074ClimateCodec.COMMAND_GA
        val raw = gaToRaw(address)
        val encoded = H074ClimateCodec.encode(celsius)
        val cemi = byteArrayOf(0x11, 0, 0xBC.toByte(), 0xE0.toByte(), 0, 0,
            (raw ushr 8).toByte(), raw.toByte(), 3, 0, 0x80.toByte()) + encoded
        val length = 10 + cemi.size
        return byteArrayOf(6, 0x10, 4, 0x20, (length ushr 8).toByte(), length.toByte(),
            4, channel.toByte(), sequence.toByte(), 0) + cemi
    }

    /** H07.6: un unico valor DPT 20.105 (1 byte), GA fija 5/2/4.
     *  Esta función NO modifica el motor de lectura H ni las temporizaciones.
     */
    fun tunnelWriteClimateModeAutoRequest(channel: Int, sequence: Int): ByteArray {
        require(channel in 1..255 && sequence in 0..255)
        val ga = gaToRaw("5/2/4")
        val cemi = byteArrayOf(0x11, 0, 0xBC.toByte(), 0xE0.toByte(), 0, 0,
            (ga ushr 8).toByte(), ga.toByte(), 2, 0, 0x80.toByte(), 0x00)
        val length = 10 + cemi.size
        return byteArrayOf(6, 0x10, 4, 0x20, (length ushr 8).toByte(), length.toByte(),
            4, channel.toByte(), sequence.toByte(), 0) + cemi
    }

    /** H07.7: SOLO cambio BAJA->MEDIA DPT 5.001, 37 % (~0x5E), GA fija 5/2/5.
     *  Solamente un GroupValueWrite; SIN repeticiones. No usado por el motor H.
     */
    fun tunnelWriteClimateFanMediumRequest(channel: Int, sequence: Int): ByteArray {
        require(channel in 1..255 && sequence in 0..255)
        val ga = gaToRaw(H077ClimateFanCodec.COMMAND_GA)
        val encoded = H077ClimateFanCodec.encode(H077ClimateFanCodec.MEDIUM_PERCENT)
        check(encoded == H077ClimateFanCodec.MEDIUM_RAW)
        val cemi = byteArrayOf(0x11, 0, 0xBC.toByte(), 0xE0.toByte(), 0, 0,
            (ga ushr 8).toByte(), ga.toByte(), 2, 0, 0x80.toByte(), encoded.toByte())
        val length = 10 + cemi.size
        return byteArrayOf(6, 0x10, 4, 0x20, (length ushr 8).toByte(), length.toByte(),
            4, channel.toByte(), sequence.toByte(), 0) + cemi
    }

    fun tunnelAck(channel: Int, sequence: Int): ByteArray =
        byteArrayOf(6, 0x10, 4, 0x21, 0, 10, 4, channel.toByte(), sequence.toByte(), 0)

    fun service(data: ByteArray, length: Int): Int? {
        if (length < 6 || uint(data, 0) != 6 || uint(data, 1) != 0x10 || word(data, 4) != length) return null
        return word(data, 2)
    }

    sealed interface ConnectAnswer {
        data class Accepted(val channel: Int) : ConnectAnswer
        data class Rejected(val code: Int) : ConnectAnswer
        data object Invalid : ConnectAnswer
    }
    fun parseConnect(data: ByteArray, length: Int): ConnectAnswer {
        if (service(data, length) != CONNECT_RES || length < 8) return ConnectAnswer.Invalid
        val channel = uint(data, 6)
        val status = uint(data, 7)
        return if (status == 0 && channel != 0) ConnectAnswer.Accepted(channel)
        else ConnectAnswer.Rejected(status)
    }

    /** Endpoint KNXnet/IP anunciado en CONNECT_RESPONSE: solo para diagnóstico. */
    fun connectDataEndpoint(data: ByteArray, length: Int): String? {
        if (service(data, length) != CONNECT_RES || length < 16 ||
            (data[8].toInt() and 0xFF) != 8 || (data[9].toInt() and 0xFF) != 1) return null
        val address = (10..13).joinToString(".") { (data[it].toInt() and 0xFF).toString() }
        val port = ((data[14].toInt() and 0xFF) shl 8) or (data[15].toInt() and 0xFF)
        return "$address:$port"
    }

    /** DISCONNECT_RESPONSE KNXnet/IP (0x020A): channel-id and status. */
    data class DisconnectAnswer(val channel: Int, val status: Int)
    fun parseDisconnectAnswer(data: ByteArray, length: Int): DisconnectAnswer? {
        if (service(data, length) != DISCONNECT_RES || length != 8) return null
        return DisconnectAnswer(uint(data, 6), uint(data, 7))
    }

    data class GatewayAck(val channel: Int, val sequence: Int, val status: Int)
    fun parseGatewayAck(data: ByteArray, length: Int): GatewayAck? {
        if (service(data, length) != TUNNELLING_ACK || length != 10 || uint(data, 6) != 4) return null
        return GatewayAck(uint(data, 7), uint(data, 8), uint(data, 9))
    }

    data class TunnelMeta(val channel: Int, val sequence: Int)
    fun incomingMeta(data: ByteArray, length: Int): TunnelMeta? {
        if (service(data, length) != TUNNELLING_REQ || length < 10 || uint(data, 6) != 4 || uint(data, 9) != 0) return null
        return TunnelMeta(uint(data, 7), uint(data, 8))
    }

    data class BusFrame(
        val channel: Int,
        val sequence: Int,
        val source: String,
        val destination: String,
        val isGroupResponse: Boolean,
        val isGroupWrite: Boolean,
        val payload: ByteArray,
        val messageCode: Int
    )

    fun parseBusFrame(data: ByteArray, length: Int): BusFrame? {
        if (service(data, length) != TUNNELLING_REQ || length < 21 || uint(data, 6) != 4 || uint(data, 9) != 0) return null
        val channel = uint(data, 7)
        val sequence = uint(data, 8)
        val cemiOffset = 10
        val code = uint(data, cemiOffset)
        // 0x2E L_Data.con es confirmación de envío, no una lectura confirmada.
        if (code != 0x29 && code != 0x2E) return null
        val frameOffset = cemiOffset + 2 + uint(data, cemiOffset + 1)
        if (frameOffset + 8 >= length) return null
        val control2 = uint(data, frameOffset + 1)
        if (control2 and 0x80 == 0) return null  // Solo destinos grupo.
        val sourceRaw = word(data, frameOffset + 2)
        val destRaw = word(data, frameOffset + 4)
        val dataLength = uint(data, frameOffset + 6)
        if (dataLength < 1 || frameOffset + 8 + dataLength > length) return null
        val apduFirst = uint(data, frameOffset + 7)
        val apduSecond = uint(data, frameOffset + 8)
        val apciCode = ((apduFirst and 3) shl 2) or ((apduSecond ushr 6) and 3)
        val payload = if (dataLength == 1) byteArrayOf((apduSecond and 0x3F).toByte())
        else data.copyOfRange(frameOffset + 9, frameOffset + 8 + dataLength)
        return BusFrame(
            channel = channel,
            sequence = sequence,
            source = "${(sourceRaw ushr 12) and 15}.${(sourceRaw ushr 8) and 15}.${sourceRaw and 255}",
            destination = rawToGa(destRaw),
            isGroupResponse = code == 0x29 && apciCode == 1,
            isGroupWrite = code == 0x29 && apciCode == 2,
            payload = payload,
            messageCode = code
        )
    }

    fun statusDescription(status: Int): String = when (status) {
        0x21 -> "ID de conexión inválido (0x21)"
        0x23 -> "Opción de conexión no admitida (0x23)"
        0x24 -> "Sin túneles KNX/IP libres (0x24); cierre otros clientes o espere a que la interfaz libere sesiones"
        0x26 -> "Opción no admitida (0x26)"
        0x27 -> "Error en la conexión al bus KNX (0x27)"
        0x29 -> "Capa de túnel no admitida (0x29)"
        0x2D -> "Error de datos KNX (0x2D)"
        else -> "Código 0x%02X".format(status)
    }
}

package com.onehouse.knxlab.knx

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** H07.1 — Control experimental de DOS luces de Cocina, seleccionables una a una.
 * Misma secuencia KNX/IP de H07: abre un túnel, envía como máximo UN GroupValueWrite,
 * procesa ACK y telegramas espontáneos en el mismo hilo, y cierra el túnel.
 * No pide lecturas ni reintenta escrituras cuando falta ACK.
 * SOLO permite los dos destinos definidos en [Light]; nunca direcciones libres.
 */
class H07LightControlRunner {
    companion object {
        // Compatibilidad de constantes H07 usadas en validaciones existentes.
        const val LABEL = "Luz Cocina"
        const val COMMAND_GA = "1/1/6"
        const val STATUS_GA = "1/2/6"
    }

    enum class Light(val label: String, val objectName: String,
                     val commandGa: String, val statusGa: String) {
        KITCHEN("Luz Cocina", "Luz", "1/1/6", "1/2/6"),
        FLUORESCENT("Fluorescente Cocina", "Fluorescente", "1/1/5", "1/2/5")
    }

    data class Detail(val elapsedMs: Long, val event: String, val description: String)
    data class Result(
        val startedAtMs: Long,
        val light: Light,
        val wantedOn: Boolean,
        val durationMs: Long,
        val connectMs: Long?,
        val channel: Int?,
        val writeCount: Int,
        val gatewayAck: Boolean,
        val gatewayAckMs: Long?,
        val observedState: Boolean?,
        val confirmedByState: Boolean,
        val disconnectStatus: String,
        val error: String?,
        val trace: List<Detail>
    ) {
        val summary: String get() = when {
            error != null -> "${light.label}: ERROR: $error"
            !gatewayAck -> "${light.label}: sin ACK; no reenviar orden automáticamente"
            confirmedByState -> "${light.label}: ACK y estado KNX confirmado (verificar físicamente)"
            else -> "${light.label}: ACK recibido; estado físico NO confirmado. Ejecuta otra lectura H."
        }
        fun csv(): String = buildString {
            fun cell(s: Any?) = "\"" + (s?.toString() ?: "").replace("\"", "\"\"") + "\""
            fun row(vararg values: Any?) = values.joinToString(";") { cell(it) }
            appendLine("ONEHOUSE_H071_CONTROL_EXPRESO_DOS_LUCES")
            appendLine("fecha;dispositivo;accion;destino;ga_estado;escrituras;ack_gateway;ack_ms;estado_observado;estado_confirmado;canal;conexion_ms;duracion_ms;cierre;error")
            appendLine(row(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(startedAtMs)),
                light.label, if (wantedOn) "ENCENDER" else "APAGAR", light.commandGa, light.statusGa, writeCount,
                gatewayAck, gatewayAckMs, observedState?.let { if (it) "ON" else "OFF" } ?: "---",
                confirmedByState, channel, connectMs, durationMs, disconnectStatus, error ?: ""))
            appendLine("TRAZA_H071")
            appendLine("offset_ms;evento;detalle")
            trace.forEach { appendLine(row(it.elapsedMs, it.event, it.description)) }
            appendLine("AVISO;ACK KNX/IP no confirma accion fisica;se requiere comprobar el estado real")
        }
    }

    private val busy = AtomicBoolean(false)
    private val cancel = AtomicBoolean(false)
    fun stop() { cancel.set(true) }

    // Mantener la firma original de H07 para sus pruebas y llamadas existentes.
    fun execute(host: String, port: Int, on: Boolean, onFinished: (Result) -> Unit): Boolean =
        execute(host, port, on, Light.KITCHEN, onFinished)

    fun execute(
        host: String,
        port: Int,
        on: Boolean,
        light: Light,
        onFinished: (Result) -> Unit
    ): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        cancel.set(false)
        Thread({
            val start = System.currentTimeMillis()
            val startNanos = System.nanoTime()
            fun elapsed() = (System.nanoTime() - startNanos) / 1_000_000L
            val log = mutableListOf<Detail>()
            fun trace(event: String, detail: String = "") { log += Detail(elapsed(), event, detail) }
            var udp: DatagramSocket? = null
            var channel: Int? = null
            var local: Inet4Address? = null
            var connectMs: Long? = null
            var gatewayAck = false
            var ackMs: Long? = null
            var writeCount = 0
            var observed: Boolean? = null
            var confirmed = false
            var disconnect = "NO_ACTIVE_TUNNEL"
            var failure: String? = null
            fun poll(timeoutMs: Int): ByteArray? {
                val sock = udp ?: error("Socket no abierto")
                sock.soTimeout = timeoutMs.coerceIn(1, 100)
                val packet = DatagramPacket(ByteArray(KnxProtocol.MAX_PACKET), KnxProtocol.MAX_PACKET)
                return try {
                    sock.receive(packet)
                    packet.data.copyOf(packet.length)
                } catch (_: SocketTimeoutException) { null }
            }
            fun send(data: ByteArray) {
                val sock = udp ?: error("Socket no abierto")
                sock.send(DatagramPacket(data, data.size))
            }
            try {
                require(port in 1..65535) { "Puerto inválido" }
                check(!cancel.get()) { "Cancelada antes de conectar" }
                val gateway = InetAddress.getByName(host) as? Inet4Address
                    ?: error("Solo IPv4; WiFi local")
                udp = DatagramSocket()
                udp.connect(InetSocketAddress(gateway, port))
                local = udp.localAddress as? Inet4Address ?: error("IP local no disponible")
                trace("CONNECT_TX", "mismo socket conectado UDP que H;puerto_local=${udp.localPort}")
                send(KnxProtocol.connectRequest(local, udp.localPort, false))
                val connectDeadline = elapsed() + 4_000L
                while (channel == null && elapsed() < connectDeadline && !cancel.get()) {
                    val packet = poll(100) ?: continue
                    if (KnxProtocol.service(packet, packet.size) != KnxProtocol.CONNECT_RES) continue
                    when (val r = KnxProtocol.parseConnect(packet, packet.size)) {
                        is KnxProtocol.ConnectAnswer.Accepted -> channel = r.channel
                        is KnxProtocol.ConnectAnswer.Rejected ->
                            error("CONNECT rechazado ${KnxProtocol.statusDescription(r.code)}")
                        KnxProtocol.ConnectAnswer.Invalid -> trace("CONNECT_INVALIDO")
                    }
                }
                val connectedChannel = channel ?: error(if (cancel.get()) "Cancelada" else "Sin CONNECT_RESPONSE en 4 segundos")
                connectMs = elapsed()
                trace("CONNECT_OK", "canal=$connectedChannel;ms=$connectMs")
                check(!cancel.get()) { "Cancelada antes de escribir" }

                // Un único envío permitido; jamás reintentar GROUP_VALUE_WRITE.
                val sentAt = elapsed()
                val frame = KnxProtocol.tunnelWriteBooleanRequest(connectedChannel, 0, light.commandGa, on)
                send(frame)
                writeCount = 1
                trace("WRITE_TX", "dispositivo=${light.label};destino=${light.commandGa};" +
                    "DPT=1.001;valor=${if (on) 1 else 0};seq=0;max_escrituras=1")
                val ackDeadline = sentAt + 1_000L
                val passiveDeadline = sentAt + 1_800L
                while (!cancel.get() && elapsed() < passiveDeadline) {
                    if (!gatewayAck && elapsed() >= ackDeadline) break
                    val packet = poll(60) ?: continue
                    when (KnxProtocol.service(packet, packet.size)) {
                        KnxProtocol.TUNNELLING_ACK -> {
                            val ack = KnxProtocol.parseGatewayAck(packet, packet.size)
                            if (ack != null && ack.channel == connectedChannel && ack.sequence == 0) {
                                if (ack.status != 0) error("ACK rechazado ${KnxProtocol.statusDescription(ack.status)}")
                                if (!gatewayAck) {
                                    gatewayAck = true
                                    ackMs = elapsed() - sentAt
                                    trace("WRITE_ACK", "ack_ms=$ackMs;solo_confirma_tunel_no_efecto_fisico")
                                }
                            } else trace("ACK_AJENO", "ack_no_corresponde_a_esta_orden")
                        }
                        KnxProtocol.TUNNELLING_REQ -> {
                            val meta = KnxProtocol.incomingMeta(packet, packet.size)
                            if (meta != null && meta.channel == connectedChannel) {
                                send(KnxProtocol.tunnelAck(meta.channel, meta.sequence))
                                val telegram = KnxProtocol.parseBusFrame(packet, packet.size)
                                if (telegram?.destination == light.statusGa &&
                                    (telegram.isGroupWrite || telegram.isGroupResponse) &&
                                    telegram.payload.isNotEmpty()) {
                                    observed = (telegram.payload[0].toInt() and 1) == 1
                                    confirmed = observed == on
                                    trace("STATE_EVENT", "ga=${light.statusGa};estado=$observed;" +
                                        "esperado=$on;coincide=$confirmed;origen=${telegram.source}")
                                }
                            }
                        }
                    }
                }
                if (!gatewayAck) failure = "Sin ACK de escritura: NO se reenvía la orden"
                else trace("CONTROL_END", "estado_confirmado=$confirmed;sin_lecturas_automaticas")
            } catch (e: Exception) {
                failure = e.message ?: e.javaClass.simpleName
                trace("ERROR", failure ?: "desconocido")
            } finally {
                val id = channel
                val sock = udp
                val bind = local
                if (id != null && sock != null && bind != null && !sock.isClosed) {
                    try {
                        trace("DISCONNECT_TX", "canal=$id;espera_max_ms=600")
                        send(KnxProtocol.disconnectRequest(id, bind, sock.localPort, false))
                        val deadline = elapsed() + 600L
                        while (elapsed() < deadline) {
                            val datagram = poll(min(100L, (deadline - elapsed()).coerceAtLeast(1L)).toInt()) ?: continue
                            val reply = KnxProtocol.parseDisconnectAnswer(datagram, datagram.size)
                            if (reply != null && reply.channel == id) {
                                disconnect = if (reply.status == 0) "DISCONNECT_OK" else "DISCONNECT_REJECT"
                                trace(disconnect, "codigo=${reply.status}")
                                break
                            }
                            if (KnxProtocol.service(datagram, datagram.size) == KnxProtocol.TUNNELLING_REQ) {
                                val meta = KnxProtocol.incomingMeta(datagram, datagram.size)
                                if (meta != null && meta.channel == id) send(KnxProtocol.tunnelAck(meta.channel, meta.sequence))
                            }
                        }
                        if (disconnect == "NO_ACTIVE_TUNNEL") disconnect = "DISCONNECT_NO_RESPONSE"
                    } catch (e: Exception) {
                        disconnect = "DISCONNECT_ERROR"
                        trace(disconnect, "${e.javaClass.simpleName}: ${e.message}")
                    }
                }
                sock?.close()
                if (cancel.get() && failure == null) failure = "Cancelación solicitada; comprobar estado físico antes de repetir"
                busy.set(false)
                onFinished(Result(start, light, on, elapsed(), connectMs, channel, writeCount,
                    gatewayAck, ackMs, observed, confirmed, disconnect, failure, log.toList()))
            }
        }, "OneHouse-H071-Light-Control").apply { isDaemon = true; start() }
        return true
    }
}

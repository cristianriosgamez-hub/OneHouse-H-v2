package com.onehouse.knxlab.knx

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * H09.1 / Entrega 35. Receptor PASIVO de dos GAs de Cocina durante <= 90 s.
 *
 * No hace GroupValueRead, NO escribe GAs y NO comparte socket con H08/H07.
 * Su coordinador solo permite arrancar DESPUÉS de una lectura H completa y
 * bloquea las lecturas y mandos mientras el túnel piloto siga abierto.
 * El listener se inicia SOLO por acción explícita del usuario en primer plano.
 */
class H091PassiveMonitorRunner {
    companion object {
        const val MAX_MONITOR_MS: Long = 90_000L
        val PILOT_ADDRESSES = setOf("1/2/6", "1/2/5")
        private const val MAX_EVENTS = 320
    }

    data class Event(val atMs: Long, val kind: String, val address: String = "", val detail: String = "")
    data class Sample(
        val address: String,
        val value: Boolean,
        val source: String,
        val receivedEpochMs: Long
    )
    data class Snapshot(
        val startedEpochMs: Long,
        val running: Boolean,
        val connected: Boolean,
        val channel: Int?,
        val acceptedWrites: Int,
        val duplicateFrames: Int,
        val unexpectedFrames: Int,
        val receivedUdp: Int,
        val stopReason: String,
        val events: List<Event>,
        val values: Map<String, Sample>,
        val disconnectedOk: Boolean = false
    )

    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    fun isRunning(): Boolean = busy.get()
    fun requestStop() { cancelled.set(true) }

    fun start(
        host: String,
        port: Int,
        socketBinder: (DatagramSocket) -> Unit,
        routeHealthCheck: () -> String?,
        onSnapshot: (Snapshot) -> Unit,
        onFinish: (Snapshot) -> Unit,
        onAcceptedWrite: (Sample) -> Unit
    ): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        cancelled.set(false)
        Thread({
            val started = System.currentTimeMillis()
            val startNs = System.nanoTime()
            fun elapsed() = (System.nanoTime() - startNs) / 1_000_000L
            val events = mutableListOf<Event>()
            val values = mutableMapOf<String, Sample>()
            var udp: DatagramSocket? = null
            var local: Inet4Address? = null
            var channel: Int? = null
            var connected = false
            var writes = 0
            var duplicates = 0
            var unexpected = 0
            var udpReceived = 0
            var lastSeq: Int? = null
            var lastPublishMs = -1_000L
            var stopReason = "FINALIZADO"
            var closeOk = false
            fun event(kind: String, address: String = "", detail: String = "") {
                if (events.size >= MAX_EVENTS) events.removeAt(0)
                events += Event(elapsed(), kind, address, detail.replace('\n', ' ').replace(';', ','))
            }
            fun snapshot(running: Boolean) = Snapshot(
                startedEpochMs = started, running = running, connected = connected,
                channel = channel, acceptedWrites = writes, duplicateFrames = duplicates,
                unexpectedFrames = unexpected, receivedUdp = udpReceived,
                stopReason = stopReason, events = events.toList(),
                values = values.toMap(), disconnectedOk = closeOk
            )
            fun publish(force: Boolean = false) {
                if (force || elapsed() - lastPublishMs >= 200L) {
                    lastPublishMs = elapsed()
                    onSnapshot(snapshot(true))
                }
            }
            fun route(stage: String) {
                val issue = try { routeHealthCheck() }
                    catch (e: Exception) { "comprobacion_${e.javaClass.simpleName}:${e.message ?: ""}" }
                if (issue != null) {
                    event("WIFI_ROUTE_LOST", detail = "$stage:$issue")
                    throw IOException("Ruta WiFi cambiada o perdida ($stage): $issue")
                }
            }
            fun send(bytes: ByteArray, stage: String) {
                route(stage)
                try { udp!!.send(DatagramPacket(bytes, bytes.size)) }
                catch (e: IOException) {
                    event("UDP_SEND_ERROR", detail = "$stage:${e.javaClass.simpleName}:${e.message ?: ""}")
                    throw e
                }
            }
            fun receive(timeoutMs: Int): ByteArray? {
                udp!!.soTimeout = timeoutMs
                val p = DatagramPacket(ByteArray(KnxProtocol.MAX_PACKET), KnxProtocol.MAX_PACKET)
                return try {
                    udp!!.receive(p)
                    udpReceived++
                    p.data.copyOf(p.length)
                } catch (_: SocketTimeoutException) { null }
            }
            try {
                require(port in 1..65535) { "Puerto KNX/IP inválido" }
                val address = InetAddress.getByName(host) as? Inet4Address
                    ?: error("La IP debe ser IPv4")
                udp = DatagramSocket()
                // Una única ruta local, idéntica al lector H08.2.2.
                socketBinder(udp)
                udp.connect(InetSocketAddress(address, port))
                local = udp.localAddress as? Inet4Address ?: error("IP local no disponible")
                event("WIFI_BIND_OK", detail = "socket_vinculado")
                send(KnxProtocol.connectRequest(local, udp.localPort, nat = false), "CONNECT")
                event("CONNECT_TX", detail = "$host:$port")
                val deadline = elapsed() + 4_000L
                while (elapsed() < deadline && channel == null && !cancelled.get()) {
                    route("CONNECT_WAIT")
                    val data = receive(100) ?: continue
                    if (KnxProtocol.service(data, data.size) != KnxProtocol.CONNECT_RES) continue
                    when (val answer = KnxProtocol.parseConnect(data, data.size)) {
                        is KnxProtocol.ConnectAnswer.Accepted -> channel = answer.channel
                        is KnxProtocol.ConnectAnswer.Rejected ->
                            error("CONNECT rechazado ${KnxProtocol.statusDescription(answer.code)}")
                        KnxProtocol.ConnectAnswer.Invalid -> unexpected++
                    }
                }
                if (cancelled.get()) {
                    stopReason = "DETENIDO_USUARIO"
                } else if (channel == null) {
                    error("CONNECT sin respuesta en 4 segundos")
                } else {
                    connected = true
                    event("CONNECT_OK", detail = "canal=$channel")
                    publish(force = true)
                    var nextKeepaliveMs = elapsed() + 30_000L
                    while (!cancelled.get() && elapsed() < MAX_MONITOR_MS) {
                        route("MONITOR")
                        if (elapsed() >= nextKeepaliveMs) {
                            send(KnxProtocol.connectionStateRequest(channel, local, udp.localPort, nat = false), "KEEPALIVE")
                            event("KEEPALIVE_TX")
                            nextKeepaliveMs = elapsed() + 30_000L
                        }
                        val data = receive(100) ?: continue
                        when (KnxProtocol.service(data, data.size)) {
                            KnxProtocol.TUNNELLING_REQ -> {
                                val meta = KnxProtocol.incomingMeta(data, data.size)
                                if (meta == null || meta.channel != channel) {
                                    unexpected++
                                    event("INVALID_TUNNEL")
                                    continue
                                }
                                // Todo telegrama entrante válido DEBE reconocerse, incluso si
                                // no es una GA piloto, se repite o no contiene GroupValueWrite.
                                send(KnxProtocol.tunnelAck(meta.channel, meta.sequence), "ACK_INCOMING")
                                if (lastSeq == meta.sequence) {
                                    duplicates++
                                    event("DUPLICATE_TUNNEL", detail = "seq=${meta.sequence}")
                                    publish()
                                    continue
                                }
                                // Una secuencia fuera de orden NO debe contaminar la caché.
                                val expected = lastSeq?.let { (it + 1) and 0xFF }
                                if (expected != null && meta.sequence != expected) {
                                    unexpected++
                                    event("SEQUENCE_GAP", detail = "esperado=$expected,recibido=${meta.sequence}")
                                    lastSeq = meta.sequence
                                    publish()
                                    continue
                                }
                                lastSeq = meta.sequence
                                val frame = KnxProtocol.parseBusFrame(data, data.size)
                                if (frame == null || !frame.isGroupWrite || frame.destination !in PILOT_ADDRESSES) {
                                    // No filtrar el ACK: solo la importación de estados.
                                    continue
                                }
                                // Sólo DPT1, un bit 0/1 confirmado mediante GroupValueWrite.
                                val raw = frame.payload.singleOrNull()?.toInt()?.and(0xFF)
                                if (raw != 0 && raw != 1) {
                                    unexpected++
                                    event("IGNORED_NON_BOOLEAN", frame.destination)
                                    publish()
                                    continue
                                }
                                val sample = Sample(frame.destination, raw == 1, frame.source, System.currentTimeMillis())
                                values[frame.destination] = sample
                                writes++
                                event("WRITE_ACCEPTED", frame.destination,
                                    "value=$raw,source=${frame.source},seq=${meta.sequence}")
                                onAcceptedWrite(sample)
                                publish(force = true)
                            }
                            KnxProtocol.CONNECTIONSTATE_RES -> {
                                if (data.size != 8 || (data[6].toInt() and 255) != channel ||
                                    (data[7].toInt() and 255) != 0) {
                                    error("KEEPALIVE rechazado o formato inválido")
                                }
                                event("KEEPALIVE_OK")
                            }
                            else -> { /* otras respuestas sin cambios de estado */ }
                        }
                    }
                    stopReason = if (cancelled.get()) "DETENIDO_USUARIO_O_SEGUNDO_PLANO" else "LIMITE_90_SEGUNDOS"
                }
            } catch (e: Exception) {
                if (cancelled.get()) stopReason = "DETENIDO_USUARIO_O_SEGUNDO_PLANO"
                else {
                    stopReason = "ERROR: ${e.javaClass.simpleName}: ${e.message ?: ""}"
                    event("MONITOR_ERROR", detail = stopReason)
                }
            } finally {
                try {
                    val sock = udp
                    val ip = local
                    val id = channel
                    if (sock != null && !sock.isClosed && ip != null && id != null) {
                        event("DISCONNECT_TX", detail = "canal=$id")
                        // Aun si se perdió la ruta se intenta una sola vez y sin reconectar.
                        val disconnect = KnxProtocol.disconnectRequest(id, ip, sock.localPort, nat = false)
                        sock.send(DatagramPacket(disconnect, disconnect.size))
                        val deadline = System.nanoTime() + 600_000_000L
                        while (System.nanoTime() < deadline && !closeOk) {
                            sock.soTimeout = 100
                            val packet = DatagramPacket(ByteArray(KnxProtocol.MAX_PACKET), KnxProtocol.MAX_PACKET)
                            try { sock.receive(packet) }
                            catch (_: SocketTimeoutException) { continue }
                            val bytes = packet.data.copyOf(packet.length)
                            val reply = KnxProtocol.parseDisconnectAnswer(bytes, bytes.size)
                            if (reply != null && reply.channel == id) {
                                closeOk = reply.status == 0
                                event(if (closeOk) "DISCONNECT_OK" else "DISCONNECT_REJECT",
                                    detail = "status=${reply.status}")
                                break
                            }
                            val meta = KnxProtocol.incomingMeta(bytes, bytes.size)
                            if (meta != null && meta.channel == id) {
                                val ack = KnxProtocol.tunnelAck(id, meta.sequence)
                                sock.send(DatagramPacket(ack, ack.size))
                            }
                        }
                        if (!closeOk) event("DISCONNECT_NOT_CONFIRMED")
                    }
                } catch (e: Exception) {
                    event("DISCONNECT_ERROR", detail = "${e.javaClass.simpleName}:${e.message ?: ""}")
                } finally {
                    udp?.close()
                    connected = false
                    busy.set(false)
                    onFinish(snapshot(false))
                }
            }
        }, "OneHouse-H091-Passive").apply { isDaemon = true; start() }
        return true
    }
}

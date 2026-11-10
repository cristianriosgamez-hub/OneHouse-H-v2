package com.onehouse.knxlab.knx

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference
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
class H091PassiveMonitorRunner internal constructor(private val keepaliveMs: Long = 30_000L, private val replyMs: Long = 4_000L) {
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
        val applied: Int, val cacheDuplicates: Int, val applyErrors: Int, val discarded: Int,
        val lostRecords: Int, val ackCount: Int, val maxAckMicros: Long, val outgoingTunnelRequests: Int,
        val connectResult: String, val firstError: String, val closeResult: String, val recoveryRequired: Boolean,
        val disconnectedOk: Boolean = false
    )

    private enum class Phase { RUNNING, STOPPING, CLOSED }
    private val phase = AtomicReference(Phase.CLOSED)
    private fun stopping() = phase.get() == Phase.STOPPING

    fun isRunning(): Boolean = phase.get() != Phase.CLOSED
    // A returned STOP is a barrier: any cache mutation already admitted has finished.
    private val admission = Any()
    fun requestStop() {
        phase.compareAndSet(Phase.RUNNING, Phase.STOPPING)
        synchronized(admission) { /* Wait for the single previously admitted cache operation. */ }
    }

    fun start(
        host: String,
        port: Int,
        socketBinder: (DatagramSocket) -> Unit,
        routeHealthCheck: () -> String?,
        onSnapshot: (Snapshot) -> Unit,
        onFinish: (Snapshot) -> Unit,
        onAcceptedWrite: (Sample) -> Boolean
    ): Boolean {
        synchronized(admission) {
            if (!phase.compareAndSet(Phase.CLOSED, Phase.RUNNING)) return false
        }
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
            var connectSent = false
            var applied = 0
            var cacheDuplicates = 0
            var applyErrors = 0
            var discarded = 0
            val diagnostics = Any()
            var lostRecords = 0
            var ackCount = 0
            var maxAckMicros = 0L
            var rxAt = 0L
            var outgoingTunnelRequests = 0
            var connectResult = "NO_ENVIADO"
            var firstError = ""
            var closeResult = "SIN_CANAL"
            var control: InetSocketAddress? = null
            var dataPeer: InetSocketAddress? = null
            fun event(kind: String, address: String = "", detail: String = "") = synchronized(diagnostics) {
                if (kind.startsWith("CONNECT_")) connectResult = "$kind $detail"
                if (kind.startsWith("DISCONNECT_")) closeResult = "$kind $detail"
                if (firstError.isEmpty() && (kind.contains("ERROR") || kind.contains("REJECT") || kind.contains("NOT_CONFIRMED") || kind == "WIFI_ROUTE_LOST")) firstError = "$kind $detail"
                if (events.size >= MAX_EVENTS) { events.removeAt(0); lostRecords++ }
                events += Event(elapsed(), kind, address, detail.replace('\n', ' ').replace(';', ','))
            }
            fun snapshot(running: Boolean) = synchronized(diagnostics) { Snapshot(
                startedEpochMs = started, running = running, connected = connected,
                channel = channel, acceptedWrites = writes, duplicateFrames = duplicates,
                unexpectedFrames = unexpected, receivedUdp = udpReceived,
                stopReason = stopReason, events = events.toList(),
                values = values.toMap(), applied = applied, cacheDuplicates = cacheDuplicates, applyErrors = applyErrors, discarded = discarded, lostRecords = lostRecords, ackCount = ackCount, maxAckMicros = maxAckMicros, outgoingTunnelRequests = outgoingTunnelRequests, connectResult = connectResult, closeResult = closeResult, firstError = firstError, recoveryRequired = connectSent && !closeOk, disconnectedOk = closeOk
            ) }
            // Only the consumer touches the Android repository. A full queue ends the pilot
            // explicitly instead of hiding lost state or blocking the next transport ACK.
            val queue = ArrayBlockingQueue<Pair<Sample,Int>>(64)
            val workerDone = AtomicBoolean(false)
            val consumerFailure = AtomicReference<Exception?>(null)
            var stoppedSamples = 0
            val consumer = Thread({
                while (!workerDone.get() || queue.isNotEmpty()) {
                    val (sample, seq) = queue.poll(25, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                    synchronized(admission) {
                        if (stopping() || workerDone.get() || consumerFailure.get() != null) {
                            synchronized(diagnostics) { stoppedSamples++; event("STOP_DISCARD", sample.address) }
                        } else try {
                            val changed = onAcceptedWrite(sample)
                            synchronized(diagnostics) {
                                if (changed) { applied++; values[sample.address] = sample; event("CACHE_APPLIED", sample.address) }
                                else { cacheDuplicates++; event("CACHE_DUPLICATE", sample.address) }
                            }
                        } catch (e: Exception) {
                            synchronized(diagnostics) { applyErrors++; event("CACHE_ERROR", sample.address, "seq=$seq:${e.message}") }
                            consumerFailure.set(e)
                        }
                    }
                }
            }, "OneHouse-H092-Cache").apply { isDaemon = true; start() }
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
            fun send(bytes: ByteArray, stage: String, peer: InetSocketAddress? = control) {
                route(stage)
                try { udp!!.send(DatagramPacket(bytes, bytes.size, peer)); if (KnxProtocol.service(bytes, bytes.size) == KnxProtocol.TUNNELLING_REQ) outgoingTunnelRequests++ }
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
                    rxAt = System.nanoTime()
                    val bytes = p.data.copyOf(p.length)
                    val expected = if (KnxProtocol.service(bytes, bytes.size) == KnxProtocol.TUNNELLING_REQ) dataPeer else control
                    if (p.socketAddress == expected) bytes else { discarded++; null }
                } catch (_: SocketTimeoutException) { null }
            }
            try {
                require(port in 1..65535) { "Puerto KNX/IP inválido" }
                val address = InetAddress.getByName(host) as? Inet4Address
                    ?: error("La IP debe ser IPv4")
                udp = DatagramSocket()
                // Una única ruta local, idéntica al lector H08.2.2.
                socketBinder(udp)
                control = InetSocketAddress(address, port)
                dataPeer = control
                udp.connect(control)
                local = udp.localAddress as? Inet4Address ?: error("IP local no disponible")
                event("WIFI_BIND_OK", detail = "socket_vinculado")
                send(KnxProtocol.connectRequest(local, udp.localPort, nat = false), "CONNECT")
                connectSent = true
                event("CONNECT_TX", detail = "$host:$port")
                var deadline = elapsed() + 4_000L
                while (elapsed() < deadline && channel == null) {
                    if (stopping()) deadline = minOf(deadline, elapsed() + 600L)
                    route("CONNECT_WAIT")
                    val data = receive(100) ?: continue
                    if (KnxProtocol.service(data, data.size) != KnxProtocol.CONNECT_RES) continue
                    when (val answer = KnxProtocol.parseConnect(data, data.size)) {
                        is KnxProtocol.ConnectAnswer.Accepted -> {
                            channel = answer.channel
                            require(data.size == 20 && data[8].toInt() == 8 && data[9].toInt() == 1 &&
                                data[16].toInt() == 4 && data[17].toInt() == 4) { "CONNECT_RESPONSE HPAI/CRD inválido" }
                            require(data.copyOfRange(10, 14).contentEquals(address.address)) { "Endpoint de datos en otra IP" }
                            val dataPort = ((data[14].toInt() and 255) shl 8) or (data[15].toInt() and 255)
                            require(dataPort > 0) { "Puerto de datos inválido" }
                            dataPeer = InetSocketAddress(address, dataPort)
                            if (dataPeer != control) udp.disconnect()
                            event("CONNECT_OK", detail = "canal=$channel;datos=$dataPort")
                        }
                        is KnxProtocol.ConnectAnswer.Rejected -> {
                            connectSent = false
                            error("CONNECT rechazado ${KnxProtocol.statusDescription(answer.code)}")
                        }
                        KnxProtocol.ConnectAnswer.Invalid -> unexpected++
                    }
                }
                if (channel == null && connectSent) event("CONNECT_NO_CONFIRMADO", detail = "asignación remota incierta")
                if (stopping()) {
                    stopReason = "DETENIDO_USUARIO"
                } else if (channel == null) {
                    error("CONNECT sin respuesta en 4 segundos")
                } else {
                    connected = true

                    publish(force = true)
                    var pendingUntil: Long? = null
                    var nextKeepaliveMs = elapsed() + keepaliveMs
                    while (!stopping() && elapsed() < MAX_MONITOR_MS) {
                        consumerFailure.get()?.let { throw it }
                        route("MONITOR")
                        check(pendingUntil?.let { elapsed() >= it } != true) { "KEEPALIVE sin respuesta" }
                        if (pendingUntil == null && elapsed() >= nextKeepaliveMs) {
                            send(KnxProtocol.connectionStateRequest(channel, local, udp.localPort, nat = false), "KEEPALIVE")
                            event("KEEPALIVE_TX")
                            pendingUntil = elapsed() + replyMs
                            nextKeepaliveMs = elapsed() + keepaliveMs
                        }
                        val data = receive(100) ?: continue
                        when (KnxProtocol.service(data, data.size)) {
                            KnxProtocol.TUNNELLING_REQ -> {
                                val meta = KnxProtocol.incomingMeta(data, data.size)
                                if (meta == null || meta.channel != channel) {
                                    unexpected++
                                    discarded++
                                    event("INVALID_TUNNEL")
                                    continue
                                }
                                // Los duplicados se reconocen sin repetir la aplicación. Un salto conserva el último número válido.
                                val expected = lastSeq?.let { (it + 1) and 255 }
                                val gap = expected != null && meta.sequence != expected && meta.sequence != lastSeq
                                if (gap) {
                                    unexpected++
                                    discarded++
                                    event("SEQUENCE_GAP", detail = "esperado=$expected,recibido=${meta.sequence}")
                                    continue
                                }
                                val ack = KnxProtocol.tunnelAck(meta.channel, meta.sequence)
                                send(ack, "ACK_INCOMING", dataPeer)
                                ackCount++
                                maxAckMicros = maxOf(maxAckMicros, (System.nanoTime() - rxAt) / 1000)
                                if (lastSeq == meta.sequence) {
                                    duplicates++
                                    discarded++
                                    event("DUPLICATE_TUNNEL", detail = "seq=${meta.sequence}")
                                    publish()
                                    continue
                                }
                                lastSeq = meta.sequence
                                val frame = KnxProtocol.parseBusFrame(data, data.size)
                                if (frame == null || !frame.isGroupWrite || frame.destination !in PILOT_ADDRESSES) {
                                    discarded++
                                    continue
                                }
                                // Sólo DPT1, un bit 0/1 confirmado mediante GroupValueWrite.
                                val raw = frame.payload.singleOrNull()?.toInt()?.and(0xFF)
                                val offset = 12 + (data[11].toInt() and 255)
                                if (data.size != offset + 9 || data[offset + 6].toInt() != 1 ||
                                    data[offset + 7].toInt() != 0 || raw !in 0..1) {
                                    unexpected++
                                    discarded++
                                    event("IGNORED_NON_BOOLEAN", frame.destination)
                                    publish()
                                    continue
                                }
                                val sample = Sample(frame.destination, raw == 1, frame.source, System.currentTimeMillis())
                                writes++
                                event("WRITE_ACCEPTED", frame.destination,
                                    "value=$raw,source=${frame.source},seq=${meta.sequence}")
                                if (stopping()) { discarded++; event("STOP_DISCARD", sample.address) }
                                else if (!queue.offer(sample to meta.sequence)) {
                                    discarded++
                                    error("Cola de caché llena (64); repita lectura H")
                                }
                                publish()
                            }
                            KnxProtocol.CONNECTIONSTATE_RES -> {
                                if (pendingUntil == null || data.size != 8 || (data[6].toInt() and 255) != channel ||
                                    (data[7].toInt() and 255) != 0) {
                                    error("KEEPALIVE rechazado o formato inválido")
                                }
                                pendingUntil = null
                                event("KEEPALIVE_OK")
                            }
                            0x0209 -> {
                                if (data.size == 16 && (data[6].toInt() and 255) == channel && data[7].toInt() == 0) {
                                    send(byteArrayOf(6,16,2,10,0,8,channel.toByte(),0), "DISCONNECT_RESPONSE")
                                    closeOk = true
                                    event("DISCONNECT_REMOTE_OK")
                                    break
                                }
                            }
                            else -> discarded++
                        }
                    }
                    stopReason = if (closeOk) "CIERRE_GATEWAY" else if (stopping()) "DETENIDO_USUARIO_O_SEGUNDO_PLANO" else "LIMITE_90_SEGUNDOS"
                }
            } catch (e: Exception) {
                stopReason = "ERROR: ${e.javaClass.simpleName}: ${e.message ?: ""}"
                event("MONITOR_ERROR", detail = stopReason)
            } finally {
                try {
                    val sock = udp
                    val ip = local
                    val id = channel
                    phase.compareAndSet(Phase.RUNNING, Phase.STOPPING)
                    workerDone.set(true)
                    if (!closeOk && sock != null && !sock.isClosed && ip != null && id != null) {
                        event("DISCONNECT_TX", detail = "canal=$id")
                        // Aun si se perdió la ruta se intenta una sola vez y sin reconectar.
                        val disconnect = KnxProtocol.disconnectRequest(id, ip, sock.localPort, nat = false)
                        sock.send(DatagramPacket(disconnect, disconnect.size, control))
                        val deadline = System.nanoTime() + 600_000_000L
                        while (System.nanoTime() < deadline && !closeOk) {
                            sock.soTimeout = 100
                            val packet = DatagramPacket(ByteArray(KnxProtocol.MAX_PACKET), KnxProtocol.MAX_PACKET)
                            try { sock.receive(packet) }
                            catch (_: SocketTimeoutException) { continue }
                            udpReceived++
                            val bytes = packet.data.copyOf(packet.length)
                            val reply = KnxProtocol.parseDisconnectAnswer(bytes, bytes.size)
                            if (packet.socketAddress == control && reply != null && reply.channel == id) {
                                closeOk = reply.status == 0
                                event(if (closeOk) "DISCONNECT_OK" else "DISCONNECT_REJECT",
                                    detail = "status=${reply.status}")
                                break
                            }
                            val meta = KnxProtocol.incomingMeta(bytes, bytes.size)
                            if (packet.socketAddress == dataPeer && meta != null && meta.channel == id) {
                                val ack = KnxProtocol.tunnelAck(id, meta.sequence)
                                val next = lastSeq?.let { (it + 1) and 255 }
                                if (next != null && meta.sequence != next && meta.sequence != lastSeq) { discarded++; continue }
                                lastSeq = meta.sequence
                                sock.send(DatagramPacket(ack, ack.size, dataPeer))
                                discarded++
                                ackCount++
                            }
                        }
                        if (!closeOk) event("DISCONNECT_NOT_CONFIRMED")
                    }
                } catch (e: Exception) {
                    event("DISCONNECT_ERROR", detail = "${e.javaClass.simpleName}:${e.message ?: ""}")
                } finally {
                    udp?.close()
                    consumer.join()
                    discarded += stoppedSamples
                    consumerFailure.get()?.let { stopReason = "ERROR_CACHE: ${it.message}" }
                    connected = false
                    phase.set(Phase.CLOSED)
                    onFinish(snapshot(false))
                }
            }
        }, "OneHouse-H091-Passive").apply { isDaemon = true; start() }
        return true
    }
}

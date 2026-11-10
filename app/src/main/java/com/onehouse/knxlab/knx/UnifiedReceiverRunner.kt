package com.onehouse.knxlab.knx

import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * H — prototipo de receptor unificado READ-ONLY (NO es el gestor de producción).
 * Un único hilo es propietario del socket; demultiplexa ACK, GVR y writes.
 * Una sola GroupValueRead pendiente, sin tuberías ni ráfagas; máximo 3 rondas.
 * Si falta ACK de transporte, como máximo reenvía el MISMO cEMI/seq una vez,
 * deteniéndose si sigue sin confirmación. No confundir ACK con respuesta a GA.
 * Las escrituras espontáneas se registran pero nunca completan una lectura.
 * H06: 15/0/13 y 15/0/15 se monitorizan sin GroupValueRead (35/35 consultables).
 * H06.1: diagnóstico PASIVO de ACK/UDP, sin cambiar envíos, pausas ni reintentos.

 */
class UnifiedReceiverRunner : Closeable {
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var worker: Thread? = null

    fun run(
        target: KnxLabRunner.Target,
        omitEventOnly: Boolean,
        onProgress: (RunSnapshot) -> Unit,
        onFinished: (RunSnapshot) -> Unit,
        // H03: conexión de diagnóstico aislada, sin ninguna petición GroupValueRead.
        diagnosticOnly: Boolean = false,
        networkContext: String = "",
        // H08.2.1: anclaje OPCIONAL del mismo socket a la WiFi activa de Android.
        // No cambia el protocolo ni crea conexiones/túneles adicionales.
        socketBinder: ((DatagramSocket) -> Unit)? = null,
        // H08.2.2: diagnostico PASIVO del estado de la ruta WiFi original.
        // null = ruta valida; texto = red cambiada/perdida. No abre sockets.
        routeHealthCheck: (() -> String?)? = null
    ): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        cancelled.set(false)
        worker = Thread({
            val startMs = System.currentTimeMillis()
            val startNs = System.nanoTime()
            fun ms(): Long = (System.nanoTime() - startNs) / 1_000_000L
            val records = if (diagnosticOnly) emptyMap<String, GaMeasurement>()
                else LabCatalog.addresses.associate { ga ->
                    ga.address to GaMeasurement(ga.address, ga.label, ga.group,
                        status = if (omitEventOnly && ga.address in setOf("15/0/13", "15/0/15"))
                            GaStatus.MONITOR_ONLY else GaStatus.PENDING)
                }
            val traces = mutableListOf<LabTrace>()
            val attempts = mutableListOf<GaAttempt>()
            var round = 0
            var channel: Int? = null
            var connectionMs: Long? = null
            var error: String? = null
            var note = "H: conectando un único receptor"
            var invalid = 0
            var ignoredAcks = 0
            var duplicates = 0
            var sequenceOut = 0
            var lastRxSequence: Int? = null
            var lastSentMs = -100_000L
            var lastKeepalive = 0L
            // Observabilidad H06.1: contadores de datagramas recibidos por el MISMO socket.
            // No se hace ninguna petición extra al bus para obtener estas métricas.
            var rxUdpTotal = 0
            var rxGatewayAckPackets = 0
            var rxTunnelPackets = 0
            var rxOtherPackets = 0
            var lastUdpRxAtMs: Long? = null
            var lastGatewayAckAtMs: Long? = null
            var lastGatewayAckSeq: Int? = null
            var lastIncomingTunnelSeq: Int? = null
            var lastRouteProbeAtMs = -100_000L
            var completed = false
            var local: Inet4Address? = null
            var udp: DatagramSocket? = null
            data class Pending(
                val ga: String,
                val seq: Int,
                val frame: ByteArray,
                val sentAt: Long,
                val sentAtNs: Long,
                val attempt: GaAttempt,
                var ackAt: Long? = null,
                var resentAt: Long = sentAt,
                var retransmits: Int = 0
            )
            var pending: Pending? = null
            fun event(name: String, ga: String = "", seq: Int? = null, detail: String = "") {
                traces += LabTrace(ms(), round + 1, ga, "H_$name", seq, detail)
            }
            fun snapshot(active: Boolean) = RunSnapshot(
                mode = LabMode.H, startedAtMs = startMs, endedAtMs = if (active) null else System.currentTimeMillis(),
                running = active, currentRound = round + 1, channel = channel,
                states = records.values.map(GaMeasurement::copy),
                invalidPackets = invalid, ignoredAck = ignoredAcks, duplicateFrames = duplicates,
                connectionError = error, comment = note, cancelled = cancelled.get(),
                omitEventOnly = omitEventOnly, connectionTimeMs = connectionMs,
                trace = traces.toList(), attempts = attempts.map(GaAttempt::copy)
            )
            var lastReport = 0L
            fun publish(force: Boolean = false) {
                if (force || ms() - lastReport >= 200L) { onProgress(snapshot(true)); lastReport = ms() }
            }
            fun ensureAlive() {
                if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException("Cancelada")
            }
            fun verifyRoute(stage: String) {
                // Android/HyperOS puede sustituir la red activa durante un tunel.
                // Detectamos el cambio ANTES de enviar: nunca abrimos otro tunel.
                val issue = try { routeHealthCheck?.invoke() }
                    catch (e: Exception) { "verificacion_fallida=${e.javaClass.simpleName}:${e.message ?: ""}" }
                if (!issue.isNullOrEmpty()) {
                    event("WIFI_ROUTE_LOST", detail = "fase=$stage;motivo=$issue;" +
                        "canal=${channel ?: "sin_canal"};ultimo_rx_ms=${lastUdpRxAtMs ?: "ninguno"};" +
                        "no_reintentar_automaticamente=true")
                    throw IOException("Ruta WiFi no disponible o cambiada ($stage): $issue")
                }
            }
            fun send(data: ByteArray, stage: String) {
                ensureAlive()
                verifyRoute(stage)
                // H04: el diagnóstico y H comparten SIEMPRE el mismo socket UDP conectado.
                // No se utiliza una segunda ruta de envío para la conexión en frío.
                try {
                    udp!!.send(DatagramPacket(data, data.size))
                } catch (e: IOException) {
                    // Un EPERM NO es falta de respuesta a una GA: es un error de
                    // envío UDP. Parada inmediata, cero reintentos nuevos.
                    val route = runCatching { routeHealthCheck?.invoke() }.getOrNull()
                    event("UDP_SEND_FAILURE", detail = "fase=$stage;" +
                        "excepcion=${e.javaClass.simpleName};detalle=${e.message ?: ""};" +
                        "ruta=${route ?: "sin_cambio_detectado"};" +
                        "canal=${channel ?: "ninguno"};udp_rx=$rxUdpTotal;" +
                        "ultimo_rx_ms=${lastUdpRxAtMs ?: "ninguno"};" +
                        "ultimo_ack_seq=${lastGatewayAckSeq ?: "ninguno"};" +
                        "sin_nuevas_lecturas=true")
                    throw e
                }
            }
            fun poll(waitMs: Int): ByteArray? {
                ensureAlive()
                udp!!.soTimeout = waitMs.coerceIn(1, 100)
                val datagram = DatagramPacket(ByteArray(KnxProtocol.MAX_PACKET), KnxProtocol.MAX_PACKET)
                return try {
                    udp!!.receive(datagram)
                    if (diagnosticOnly) {
                        val serviceType = KnxProtocol.service(datagram.data, datagram.length)
                        event("DIAG_RX_UDP", detail =
                            "origen=${datagram.address.hostAddress}:${datagram.port};" +
                            "servicio=${serviceType?.let { "0x%04X".format(it) } ?: "INVALIDO"};bytes=${datagram.length}")
                        // DatagramSocket.connect(...) aplica el mismo filtro de origen
                        // en ambos modos. Se conserva la IP/puerto observada en el CSV.
                    }
                    val service = KnxProtocol.service(datagram.data, datagram.length)
                    rxUdpTotal++
                    lastUdpRxAtMs = ms()
                    when (service) {
                        KnxProtocol.TUNNELLING_ACK -> rxGatewayAckPackets++
                        KnxProtocol.TUNNELLING_REQ -> rxTunnelPackets++
                        else -> rxOtherPackets++
                    }
                    datagram.data.copyOf(datagram.length)
                } catch (_: SocketTimeoutException) {
                    // No sondear continuamente: solo si no se recibe UDP durante
                    // >=200 ms y a lo sumo una vez cada 250 ms; sin trafico KNX.
                    if (ms() - (lastUdpRxAtMs ?: 0L) >= 200L &&
                        ms() - lastRouteProbeAtMs >= 250L) {
                        lastRouteProbeAtMs = ms()
                        verifyRoute("UDP_SILENCIO")
                    }
                    null
                } catch (e: IOException) {
                    event("UDP_RECEIVE_FAILURE", detail = "excepcion=${e.javaClass.simpleName};" +
                        "detalle=${e.message ?: ""};udp_rx=$rxUdpTotal;" +
                        "canal=${channel ?: "ninguno"}")
                    throw e
                }
            }
            fun process(data: ByteArray?) {
                if (data == null) return
                when (KnxProtocol.service(data, data.size)) {
                    KnxProtocol.TUNNELLING_ACK -> {
                        val ack = KnxProtocol.parseGatewayAck(data, data.size)
                        if (ack == null) { invalid++; event("INVALID_ACK"); return }
                        lastGatewayAckAtMs = ms()
                        lastGatewayAckSeq = ack.sequence
                        val p = pending
                        if (ack.channel != channel || p == null || ack.sequence != p.seq) {
                            ignoredAcks++
                            event("UNMATCHED_ACK", seq = ack.sequence,
                                detail = "canal_rx=${ack.channel};canal_actual=$channel;" +
                                    "ga_pendiente=${p?.ga ?: "ninguna"};seq_esperada=${p?.seq ?: "ninguna"};" +
                                    "ms_desde_ultima_tx=${p?.let { ms() - it.sentAt } ?: "sin_pendiente"}")
                            return
                        }
                        if (ack.status != 0) {
                            event("ACK_REJECT", p.ga, p.seq, KnxProtocol.statusDescription(ack.status))
                            throw IllegalStateException("ACK rechazado: ${KnxProtocol.statusDescription(ack.status)}")
                        }
                        if (p.ackAt == null) {
                            p.ackAt = ms()
                            p.attempt.ackOffsetMs = ms()
                            records.getValue(p.ga).apply {
                                gatewayAcks++
                                ackLatencyMs = (System.nanoTime() - p.sentAtNs) / 1_000_000L
                                gatewayAckMissing = false
                            }
                            event("GATEWAY_ACK", p.ga, p.seq,
                                "ack_ms=${records.getValue(p.ga).ackLatencyMs};retransmisiones=${p.retransmits}")
                        }
                    }
                    KnxProtocol.TUNNELLING_REQ -> {
                        val meta = KnxProtocol.incomingMeta(data, data.size)
                        if (meta == null || meta.channel != channel) { invalid++; event("INVALID_INCOMING"); return }
                        // Confirmamos TODA trama válida del canal; no es GroupValueWrite.
                        send(KnxProtocol.tunnelAck(meta.channel, meta.sequence), "ACK_INCOMING:${meta.sequence}")
                        if (lastRxSequence == meta.sequence) {
                            duplicates++
                            event("DUPLICATE_TUNNEL", seq = meta.sequence)
                            return
                        }
                        lastRxSequence = meta.sequence
                        lastIncomingTunnelSeq = meta.sequence
                        val frame = KnxProtocol.parseBusFrame(data, data.size) ?: return
                        val r = records[frame.destination] ?: return
                        if (frame.isGroupWrite) {
                            r.incomingWrites++
                            event("EVENT_WRITE", frame.destination, meta.sequence,
                                "source=${frame.source};solo_evento=true")
                        } else if (frame.isGroupResponse) {
                            if (r.groupRequests == 0 || r.status == GaStatus.MONITOR_ONLY) {
                                event("UNREQUESTED_RESPONSE", frame.destination, meta.sequence,
                                    "source=${frame.source}")
                                return
                            }
                            if (r.status == GaStatus.RECEIVED) {
                                event("EXTRA_RESPONSE", frame.destination, meta.sequence,
                                    "source=${frame.source}")
                                return
                            }
                            val now = ms()
                            r.status = GaStatus.RECEIVED
                            r.error = ""
                            r.source = frame.source
                            r.rawHex = frame.payload.joinToString("") { "%02X".format(it.toInt() and 255) }
                            r.firstResponseOffsetMs = r.firstResponseOffsetMs ?: now
                            r.responseLatencyMs = now - (r.firstRequestOffsetMs ?: now)
                            val a = attempts.lastOrNull { it.address == frame.destination }
                            if (a != null && a.responseOffsetMs == null) {
                                a.responseOffsetMs = now
                                a.outcome = "RESPUESTA_RECIBIDA"
                            }
                            event("MATCH_RESPONSE", frame.destination, meta.sequence,
                                "source=${frame.source};solicitudes=${r.groupRequests};ack=${pending?.takeIf { it.ga == frame.destination }?.ackAt != null}")
                        }
                    }
                    0x0208 -> event("KEEPALIVE_RESPONSE")
                }
            }
            fun pump(msToWait: Long) {
                val deadline = ms() + msToWait
                while (ms() < deadline) {
                    ensureAlive()
                    process(poll(min(45L, (deadline - ms()).coerceAtLeast(1L)).toInt()))
                    publish()
                }
            }
            fun keepalive() {
                if (ms() - lastKeepalive < 30_000L) return
                val id = channel ?: return
                val ip = local ?: return
                send(KnxProtocol.connectionStateRequest(id, ip, udp!!.localPort, target.nat), "KEEPALIVE")
                lastKeepalive = ms()
                event("KEEPALIVE_TX")
            }
            try {
                require(target.port in 1..65535) { "Puerto KNX inválido" }
                val addr = InetAddress.getByName(target.host) as? Inet4Address
                    ?: error("Solo se admiten direcciones IPv4")
                udp = DatagramSocket()
                socket = udp
                // Debe aplicarse ANTES de connect() y de mandar el único CONNECT_REQUEST.
                // Un fallo de bind detiene la prueba; finally cierra este mismo socket.
                if (socketBinder != null) {
                    try {
                        socketBinder(udp)
                        event("WIFI_BIND_OK", detail = "socket_fijado_a_red_WiFi_antes_de_CONNECT")
                    } catch (e: Exception) {
                        event("WIFI_BIND_ERROR", detail =
                            "${e.javaClass.simpleName}: ${e.message ?: "sin_mensaje"};sin_CONNECT_TX")
                        throw IllegalStateException("No se pudo vincular socket a WiFi: ${e.message}", e)
                    }
                }
                // H04: apertura EXACTAMENTE igual para «Solo conexión» e «Iniciar H».
                // Mantener el filtro UDP conectado evita la divergencia introducida en H03.
                udp.connect(InetSocketAddress(addr, target.port))
                local = udp.localAddress as? Inet4Address ?: error("No se pudo obtener IP local IPv4")
                event("NETWORK_CONTEXT", detail = networkContext)
                event("SOCKET_ROUTE", detail =
                    "tipo=UDP_conectado_comun;modo=${if (diagnosticOnly) "solo_conexion" else "lectura_H"};" +
                    "local=${local.hostAddress}:${udp.localPort};remoto=${addr.hostAddress}:${target.port}")
                event("CONNECT_TX", detail = "${target.host}:${target.port};NAT=${target.nat};solo_diagnostico=$diagnosticOnly")
                send(KnxProtocol.connectRequest(local, udp.localPort, target.nat), "CONNECT_REQUEST")
                val connectLimit = ms() + 4_000L
                while (channel == null && ms() < connectLimit) {
                    ensureAlive()
                    val data = poll(100) ?: continue
                    if (KnxProtocol.service(data, data.size) == KnxProtocol.CONNECT_RES) {
                        val parsed = KnxProtocol.parseConnect(data, data.size)
                        event("CONNECT_RESPONSE_RX", detail =
                            "tipo=${parsed.javaClass.simpleName};" +
                            "endpoint_datos=${KnxProtocol.connectDataEndpoint(data, data.size) ?: "sin_hpai"};" +
                            "puerto_respuesta_filtrado=${target.port}")
                        when (parsed) {
                            is KnxProtocol.ConnectAnswer.Accepted -> channel = parsed.channel
                            is KnxProtocol.ConnectAnswer.Rejected -> error("CONNECT rechazado ${KnxProtocol.statusDescription(parsed.code)}")
                            KnxProtocol.ConnectAnswer.Invalid -> invalid++
                        }
                    }
                }
                if (channel == null) {
                    event("CONNECT_TIMEOUT_DETAILS", detail = "sin respuesta válida en 4 s;" +
                        "diagnostico=$diagnosticOnly;red=$networkContext;" +
                        "udp_recibidos=$rxUdpTotal;" +
                        "ip_local=${local.hostAddress};" +
                        "puerto_local=${udp.localPort};" +
                        "gateway=${target.host}:${target.port};" +
                        "socket_cerrado_en_finally=true;" +
                        "sin_reintentos_automaticos=true")
                    error("Sin CONNECT_RESPONSE en 4 segundos")
                }
                connectionMs = ms()
                lastKeepalive = ms()
                event("CONNECT_OK", detail = "canal=$channel;conexion_ms=$connectionMs")
                note = if (diagnosticOnly) "Diagnóstico: túnel abierto, SIN leer GAs"
                    else "H: túnel $channel; 1 lectura pendiente"
                publish(true)
                if (diagnosticOnly) {
                    event("DIAGNOSTICO_SIN_GA", detail = "conectado;0 GroupValueRead;se cierra el túnel")
                    completed = true
                } else {
                for (r in 0..2) {
                    round = r
                    ensureAlive()
                    val queue = records.values.filter { it.status != GaStatus.RECEIVED && it.status != GaStatus.MONITOR_ONLY }
                        .map { it.address }
                    if (queue.isEmpty()) break
                    val responseDeadlineMs = when (r) { 0 -> 750L; 1 -> 1_250L; else -> 1_800L }
                    note = "H ronda ${r + 1}/3 · ${queue.size} GAs"
                    event("ROUND_START", detail = "pendientes=${queue.size};timeout=$responseDeadlineMs")
                    publish(true)
                    for (ga in queue) {
                        ensureAlive()
                        keepalive()
                        // Una respuesta tardía recibida durante la pausa puede evitar otro READ.
                        if (records.getValue(ga).status == GaStatus.RECEIVED) continue
                        val minGap = 160L
                        val elapsedFromLast = ms() - lastSentMs
                        if (elapsedFromLast < minGap) pump(minGap - elapsedFromLast)
                        if (records.getValue(ga).status == GaStatus.RECEIVED) continue
                        val seq = sequenceOut
                        val packet = KnxProtocol.tunnelReadRequest(channel, seq, ga)
                        sequenceOut = (sequenceOut + 1) and 255
                        val sendAt = ms()
                        val attempt = GaAttempt(ga, r + 1, seq, sendAt)
                        attempts += attempt
                        val p = Pending(ga, seq, packet, sendAt, System.nanoTime(), attempt)
                        pending = p
                        val rec = records.getValue(ga)
                        rec.apply {
                            groupRequests++
                            lastRound = r + 1
                            status = GaStatus.WAITING
                            firstRequestOffsetMs = firstRequestOffsetMs ?: sendAt
                            this.error = ""
                        }
                        send(packet, "READ:$ga")
                        lastSentMs = ms()
                        event("READ_TX", ga, seq,
                            "ronda=${r + 1};timeout_ms=$responseDeadlineMs;ventana=1;" +
                                "canal=$channel;local_udp=${udp.localPort};" +
                                "rx_udp_previos=$rxUdpTotal;ultimo_ack_seq=${lastGatewayAckSeq ?: "ninguno"}")
                        val responseLimit = ms() + responseDeadlineMs
                        while (true) {
                            ensureAlive()
                            keepalive()
                            val now = ms()
                            if (rec.status == GaStatus.RECEIVED && p.ackAt != null) break
                            if (p.ackAt == null && now - p.resentAt >= 250L) {
                                if (p.retransmits == 0) {
                                    // retransmisión de transporte: MISMO número de secuencia
                                    send(p.frame, "TRANSPORT_RETX:$ga")
                                    p.resentAt = ms()
                                    p.retransmits++
                                    rec.ackRetransmits++
                                    attempt.gatewayRetransmits++
                                    event("TRANSPORT_RETX", ga, seq,
                                        "mismo_seq=true;sin_ack_ms=${ms() - p.sentAt};canal=$channel;" +
                                            "udp_rx_total=$rxUdpTotal;udp_ack_rx=$rxGatewayAckPackets;" +
                                            "udp_tunnel_rx=$rxTunnelPackets;" +
                                            "ms_desde_ultimo_udp=${lastUdpRxAtMs?.let { ms() - it } ?: "ninguno"};" +
                                            "ultimo_ack_seq=${lastGatewayAckSeq ?: "ninguno"}")
                                } else {
                                    rec.gatewayAckMissing = true
                                    attempt.outcome = "SIN_ACK_DETENER"
                                    attempt.resolvedOffsetMs = ms()
                                    event("STOP_NO_ACK", ga, seq,
                                        "motivo=ACK_TRANSPORTE_AUSENTE;sin_ack_ms=${ms() - p.sentAt};" +
                                            "reenvios_misma_trama=${p.retransmits};" +
                                            "canal=$channel;local_udp=${udp.localPort};" +
                                            "rx_total=$rxUdpTotal;rx_ack=$rxGatewayAckPackets;" +
                                            "rx_tunnelling=$rxTunnelPackets;rx_otro=$rxOtherPackets;" +
                                            "ms_desde_ultimo_udp=${lastUdpRxAtMs?.let { ms() - it } ?: "ninguno"};" +
                                            "ms_desde_ultimo_ack=${lastGatewayAckAtMs?.let { ms() - it } ?: "ninguno"};" +
                                            "ultimo_ack_seq=${lastGatewayAckSeq ?: "ninguno"};" +
                                            "ultimo_tunnel_rx_seq=${lastIncomingTunnelSeq ?: "ninguno"};" +
                                            "NO_ENVIAR_MAS_LECTURAS")
                                    throw IllegalStateException("H: ausencia de ACK de transporte en $ga; detenido por seguridad")
                                }
                            }
                            if (now >= responseLimit && p.ackAt != null) break
                            process(poll(35))
                            publish()
                        }
                        pending = null
                        attempt.resolvedOffsetMs = ms()
                        if (rec.status != GaStatus.RECEIVED) {
                            rec.status = GaStatus.NO_RESPONSE
                            rec.error = "No se recibió GroupValueResponse"
                            attempt.outcome = "TIMEOUT_RESPUESTA"
                            event("RESPONSE_TIMEOUT", ga, seq, "ack_recibido=true")
                        } else {
                            attempt.outcome = "RESPUESTA_RECIBIDA"
                        }
                        rec.lastResolutionOffsetMs = ms()
                        publish()
                    }
                    event("ROUND_END", detail = "pendientes=${records.values.count { it.status != GaStatus.RECEIVED && it.status != GaStatus.MONITOR_ONLY }}")
                    if (records.values.all { it.status == GaStatus.RECEIVED || it.status == GaStatus.MONITOR_ONLY }) break
                    if (r != 2) {
                        // La consolidación no reenvía GroupValueRead; recoge respuestas tardías.
                        event("LATE_RESPONSE_GRACE", detail = "espera_ms=${if (r == 0) 500 else 900}")
                        pump(if (r == 0) 500 else 900)
                    }
                }
                records.values.filter { it.status != GaStatus.RECEIVED && it.status != GaStatus.MONITOR_ONLY }
                    .forEach { it.status = if (it.gatewayAcks > 0) GaStatus.NO_RESPONSE else GaStatus.NO_ACK }
                val requested = records.values.count { it.status != GaStatus.MONITOR_ONLY }
                val got = records.values.count { it.status == GaStatus.RECEIVED }
                note = "H completada: $got/$requested respuestas, ${records.values.sumOf { it.groupRequests }} GroupValueRead"
                event("FINISH", detail = note)
                completed = true
                } // fin de modo de lectura H; diagnóstico no envía GroupValueRead.
            } catch (_: InterruptedException) {
                cancelled.set(true)
                note = "H cancelada"
            } catch (e: Exception) {
                if (cancelled.get()) note = "H cancelada"
                else { error = e.message ?: e.javaClass.simpleName; note = "H detenida: $error"; event("ERROR", detail = error ?: "") }
            } finally {
                // Resumen de diagnóstico PASIVO antes de cerrar (NO envía tráfico adicional).
                event("RX_UDP_RESUMEN", detail =
                    "total=$rxUdpTotal;ack_gateway=$rxGatewayAckPackets;" +
                        "tunnelling=$rxTunnelPackets;otros=$rxOtherPackets;" +
                        "ultimo_ack_seq=${lastGatewayAckSeq ?: "ninguno"};" +
                        "ultimo_tunnel_seq=${lastIncomingTunnelSeq ?: "ninguno"};" +
                        "ultimo_udp_offset_ms=${lastUdpRxAtMs ?: "ninguno"}")
                // Cierre cooperativo del túnel: el socket se conserva vivo hasta recibir
                // DISCONNECT_RESPONSE o agotar una espera ACOTADA. No se emite ningún READ.
                // No hacemos reconexiones automáticas: ante 0x24 hay que investigar canales.
                try {
                    val id = channel
                    val active = udp
                    val bind = local
                    if (id != null && active != null && bind != null && !active.isClosed) {
                        val goodbye = KnxProtocol.disconnectRequest(id, bind, active.localPort, target.nat)
                        val routeAtClose = runCatching { routeHealthCheck?.invoke() }
                            .getOrElse { "error_comprobacion=${it.javaClass.simpleName}" }
                        event("DISCONNECT_ROUTE", detail =
                            "canal=$id;ruta=${routeAtClose ?: "sin_cambio_detectado"};" +
                            "un_solo_intento_de_cierre=true")
                        event("DISCONNECT_TX", detail = "canal=$id;max_espera_ms=600")
                        // H04: cierre con la misma ruta UDP conectada en ambas pruebas.
                        active.send(DatagramPacket(goodbye, goodbye.size))
                        val deadline = System.nanoTime() + 600_000_000L
                        var acknowledged = false
                        while (!acknowledged) {
                            val remainingNs = deadline - System.nanoTime()
                            if (remainingNs <= 0) break
                            active.soTimeout = min(100L, (remainingNs / 1_000_000L).coerceAtLeast(1)).toInt()
                            val datagram = DatagramPacket(ByteArray(KnxProtocol.MAX_PACKET), KnxProtocol.MAX_PACKET)
                            try {
                                active.receive(datagram)
                            } catch (_: SocketTimeoutException) {
                                continue
                            }
                            val data = datagram.data.copyOf(datagram.length)
                            val reply = KnxProtocol.parseDisconnectAnswer(data, data.size)
                            if (reply != null && reply.channel == id) {
                                acknowledged = true
                                if (reply.status == 0) event("DISCONNECT_OK", detail = "canal=$id")
                                else event("DISCONNECT_REJECT", detail = KnxProtocol.statusDescription(reply.status))
                            } else if (KnxProtocol.service(data, data.size) == KnxProtocol.TUNNELLING_REQ) {
                                // Durante el cierre sigue siendo obligatorio reconocer telegramas
                                // de este canal, sin interpretar su carga ni generar nuevos READ.
                                val meta = KnxProtocol.incomingMeta(data, data.size)
                                if (meta != null && meta.channel == id) {
                                    val ack = KnxProtocol.tunnelAck(meta.channel, meta.sequence)
                                    active.send(DatagramPacket(ack, ack.size))
                                }
                            }
                        }
                        if (!acknowledged) event("DISCONNECT_NO_RESPONSE", detail = "canal=$id;espera_agotada_ms=600")
                    } else {
                        event("NO_ACTIVE_TUNNEL_TO_CLOSE", detail = "Sin canal asignado o socket cerrado")
                    }
                } catch (e: Exception) {
                    event("DISCONNECT_ERROR", detail = e.javaClass.simpleName + ": " + (e.message ?: ""))
                }
                udp?.close()
                socket = null
                pending = null
                if (!completed && error == null && !cancelled.get()) error = "Finalización anómala"
                busy.set(false)
                onFinished(snapshot(false))
            }
        }, "OneHouse-KNX-Lab-H").apply { isDaemon = true; start() }
        return true
    }

    // Cancelación cooperativa: poll() despierta cada <=100 ms. No cerrar el socket
    // desde UI: el hilo receptor debe poder enviar DISCONNECT_REQUEST antes de salir.
    fun stop() { cancelled.set(true) }
    override fun close() = stop()
}

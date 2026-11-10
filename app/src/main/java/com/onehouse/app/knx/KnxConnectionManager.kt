package com.onehouse.app.knx

import com.onehouse.app.feature.h08.H08Safety

import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.PortUnreachableException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Motor reutilizable de conexión KNXnet/IP Tunnelling.
 *
 * En v1.4.2 gestiona el ciclo de vida del túnel (connect/disconnect) y deja
 * definida la API que utilizará v1.5.0 para telegramas y lecturas de grupo.
 * Los métodos de bus devuelven [OperationResult.NotAvailable] hasta que se
 * incorpore la codificación cEMI/DPT en la siguiente versión.
 */
class KnxConnectionManager(
    private val timeoutMillis: Int = KnxProtocol.DEFAULT_TIMEOUT_MILLIS,
    private val stateRepository: KnxStateRepository? = null,
    private val monitorRepository: KnxTelegramMonitorRepository? = null
) : Closeable {

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    sealed interface ConnectResult {
        data class Success(
            val deviceAddress: String,
            val channelId: Int
        ) : ConnectResult

        data object Timeout : ConnectResult
        data class Rejected(val status: Int) : ConnectResult
        data class NetworkError(val detail: String) : ConnectResult
        data object InvalidResponse : ConnectResult
        data object Cancelled : ConnectResult
    }

    sealed interface OperationResult {
        data class Success(
            val incoming: IncomingGroupTelegram? = null,
            val diagnostic: TelegramDiagnostic? = null
        ) : OperationResult
        data class Failure(val detail: String) : OperationResult
        data class NotAvailable(val detail: String) : OperationResult
    }


    data class TelegramDiagnostic(
        val channelId: Int,
        val sequence: Int,
        val cemiHex: String,
        val knxNetIpHex: String,
        val gatewayAcknowledged: Boolean,
        val gatewayAckHex: String? = null,
        val gatewayRoundTripMillis: Long? = null,
        val incomingKnxNetIpHex: String? = null,
        val incomingCemiHex: String? = null,
        val incomingMessageCode: Int? = null,
        val incomingApci: String? = null,
        val ignoredAckCount: Int = 0,
        val invalidPacketCount: Int = 0,
        val duplicateIncomingCount: Int = 0,
        val transmissionAttempts: Int = 1
    )

    data class IncomingGroupTelegram(
        val kind: Kind,
        val sourceAddress: String,
        val destination: KnxGroupAddress,
        val booleanValue: Boolean?,
        val payload: ByteArray,
        val messageCode: Int,
        val apci: String,
        val cemiHex: String
    ) {
        enum class Kind { WRITE, RESPONSE }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var worker: Thread? = null
    private var socket: DatagramSocket? = null
    private var channelId: Int? = null
    private var localAddress: Inet4Address? = null
    private var endpoint: KnxEndpoint? = null
    private var cancelled = AtomicBoolean(false)
    private val sequenceCounter = AtomicInteger(0)
    private val operationLock = ReentrantLock()
    private var passiveReceiverThread: Thread? = null
    private var passiveReceiverCancellation = AtomicBoolean(true)
    private var scheduledDisconnect: Runnable? = null
    @Volatile private var lastTunnelActivityMillis: Long = 0L
    // v1.12.1.3: cuando varias pantallas solicitan conexión mientras el mismo
    // túnel todavía está en CONNECTING, no abrimos/cancelamos sesiones nuevas.
    // Las solicitudes se agrupan y reciben el mismo resultado.
    private var connectingEndpoint: KnxEndpoint? = null
    private val pendingConnectCallbacks = mutableListOf<(ConnectResult) -> Unit>()

    @Volatile
    var state: State = State.DISCONNECTED
        private set

    val isConnected: Boolean
        get() = state == State.CONNECTED && socket?.isClosed == false && channelId != null

    fun connect(
        endpoint: KnxEndpoint,
        source: String = "OTRO",
        onResult: (ConnectResult) -> Unit
    ): Closeable {
        if (H08Safety.READ_ONLY) {
            mainHandler.post { onResult(ConnectResult.NetworkError(H08Safety.BLOCKED_MESSAGE)) }
            return Closeable { }
        }
        require(endpoint.port in 1..65535) { "Puerto KNX/IP fuera de rango" }
        require(timeoutMillis > 0) { "El tiempo de espera debe ser mayor que cero" }

        cancelScheduledDisconnect()

        val decision = synchronized(lock) {
            val currentChannel = channelId
            when {
                state == State.CONNECTED &&
                    socket?.isClosed == false &&
                    this.endpoint == endpoint &&
                    currentChannel != null &&
                    System.currentTimeMillis() - lastTunnelActivityMillis <= MAX_SAFE_TUNNEL_IDLE_MILLIS ->
                    ConnectDecision.Reuse(currentChannel)

                state == State.CONNECTING && connectingEndpoint == endpoint -> {
                    pendingConnectCallbacks += onResult
                    ConnectDecision.JoinPending
                }

                else -> ConnectDecision.OpenNew
            }
        }

        when (decision) {
            is ConnectDecision.Reuse -> {
                KnxPerformanceMetrics.recordTunnelReuse(source)
                monitorRepository?.record(
                    direction = KnxTelegramEvent.Direction.SYSTEM,
                    kind = KnxTelegramEvent.Kind.CONNECT,
                    status = KnxTelegramEvent.Status.CONFIRMED,
                    detail = "Túnel reutilizado · canal ${decision.channelId} · origen $source"
                )
                mainHandler.post {
                    onResult(ConnectResult.Success(endpoint.host, decision.channelId))
                }
                return Closeable { }
            }

            ConnectDecision.JoinPending -> {
                // Contabilizamos también como reutilización: la solicitud aprovecha
                // el intento de apertura que ya está en curso en vez de crear otro.
                KnxPerformanceMetrics.recordTunnelReuse(source)
                monitorRepository?.record(
                    direction = KnxTelegramEvent.Direction.SYSTEM,
                    kind = KnxTelegramEvent.Kind.CONNECT,
                    status = KnxTelegramEvent.Status.PENDING,
                    detail = "Solicitud agrupada con una apertura de túnel ya en curso · origen $source"
                )
                return Closeable {
                    synchronized(lock) { pendingConnectCallbacks.remove(onResult) }
                }
            }

            ConnectDecision.OpenNew -> Unit
        }

        KnxPerformanceMetrics.recordTunnelOpenAttempt(source)
        monitorRepository?.record(
            direction = KnxTelegramEvent.Direction.SYSTEM,
            kind = KnxTelegramEvent.Kind.CONNECT,
            status = KnxTelegramEvent.Status.PENDING,
            detail = "Apertura de túnel ${endpoint.host}:${endpoint.port} · origen $source"
        )
        disconnectInternal(sendRequest = true, reason = "Nueva apertura solicitada")
        cancelled = AtomicBoolean(false)
        state = State.CONNECTING
        synchronized(lock) { connectingEndpoint = endpoint }

        val currentCancellation = cancelled
        val connectCompleted = AtomicBoolean(false)
        val thread = Thread {
            val result = openTunnel(endpoint, currentCancellation, source)
            connectCompleted.set(true)
            recordConnectResult(result, endpoint, source)

            if (!currentCancellation.get()) {
                mainHandler.post {
                    if (!currentCancellation.get()) {
                        val joinedCallbacks = synchronized(lock) {
                            connectingEndpoint = null
                            pendingConnectCallbacks.toList().also { pendingConnectCallbacks.clear() }
                        }
                        onResult(result)
                        joinedCallbacks.forEach { callback -> callback(result) }
                    }
                }
            }
        }.apply {
            name = "KnxConnectionManager"
            isDaemon = true
            start()
        }

        synchronized(lock) {
            worker = thread
        }

        return Closeable {
            // Cerrar el handle de una petición ya completada NO debe cerrar el
            // socket compartido. Hacerlo provocaba recvfrom/EBADF y estados
            // "Conectado" sin tráfico al cambiar de pantalla o de red.
            if (connectCompleted.get()) return@Closeable
            currentCancellation.set(true)
            val joinedCallbacks = synchronized(lock) {
                connectingEndpoint = null
                pendingConnectCallbacks.toList().also { pendingConnectCallbacks.clear() }
            }
            synchronized(lock) {
                socket?.close()
                worker?.interrupt()
            }
            if (state == State.CONNECTING) state = State.DISCONNECTED
            if (joinedCallbacks.isNotEmpty()) {
                mainHandler.post { joinedCallbacks.forEach { it(ConnectResult.Cancelled) } }
            }
        }
    }

    private sealed interface ConnectDecision {
        data class Reuse(val channelId: Int) : ConnectDecision
        data object JoinPending : ConnectDecision
        data object OpenNew : ConnectDecision
    }

    fun disconnect() {
        cancelScheduledDisconnect()
        disconnectInternal(sendRequest = true, reason = "Desconexión solicitada")
    }

    /**
     * Mantiene el túnel unos instantes para reutilizarlo en ráfagas de comandos
     * (escenas, navegación y controles consecutivos). Si llega otra operación
     * antes del vencimiento, el cierre pendiente se cancela automáticamente.
     */
    fun scheduleDisconnect(delayMillis: Long = DEFAULT_IDLE_DISCONNECT_MILLIS) {
        if (delayMillis <= 0L) {
            disconnect()
            return
        }
        cancelScheduledDisconnect()
        lateinit var task: Runnable
        task = Runnable {
            val shouldDisconnect = synchronized(lock) {
                if (scheduledDisconnect !== task) {
                    false
                } else {
                    scheduledDisconnect = null
                    true
                }
            }
            if (shouldDisconnect) disconnectInternal(sendRequest = true, reason = "Cierre por inactividad")
        }
        synchronized(lock) { scheduledDisconnect = task }
        mainHandler.postDelayed(task, delayMillis)
    }

    private fun cancelScheduledDisconnect() {
        val pending = synchronized(lock) {
            scheduledDisconnect.also { scheduledDisconnect = null }
        }
        pending?.let(mainHandler::removeCallbacks)
    }

    /** Envía un telegrama de grupo por el túnel KNXnet/IP activo. */
    fun sendTelegram(
        telegram: KnxTelegram,
        onResult: (OperationResult) -> Unit
    ) = sendTelegram(telegram, timeoutOverrideMillis = null, onResult = onResult)

    /**
     * Variante con timeout por operación. Permite que las lecturas masivas iniciales
     * sean ágiles sin reducir el timeout general usado por comandos y conexión.
     */
    fun sendTelegram(
        telegram: KnxTelegram,
        timeoutOverrideMillis: Int?,
        onResult: (OperationResult) -> Unit
    ) {
        if (H08Safety.READ_ONLY) {
            mainHandler.post { onResult(OperationResult.NotAvailable(H08Safety.BLOCKED_MESSAGE)) }
            return
        }
        cancelScheduledDisconnect()
        val thread = Thread {
            val operationTimeoutMillis = timeoutOverrideMillis
                ?.coerceAtLeast(1)
                ?.coerceAtMost(timeoutMillis)
                ?: timeoutMillis
            val result = operationLock.withLock {
                sendTelegramBlocking(telegram, operationTimeoutMillis)
            }
            mainHandler.post { onResult(result) }
        }.apply {
            name = "KnxTelegramSender"
            isDaemon = true
            start()
        }
        synchronized(lock) { worker = thread }
    }

    /** Compatibilidad con llamadas que todavía entregan dirección y APDU. */
    fun sendTelegram(
        groupAddress: String,
        payload: ByteArray,
        onResult: (OperationResult) -> Unit
    ) {
        val address = runCatching { KnxGroupAddress.parse(groupAddress) }.getOrElse {
            mainHandler.post { onResult(OperationResult.Failure(it.message ?: "Dirección KNX inválida")) }
            return
        }
        val value = payload.lastOrNull()?.toInt()?.and(0x01) == 1
        sendTelegram(KnxTelegram.GroupValueWriteBoolean(address, value), onResult)
    }

    fun readGroupValue(
        groupAddress: String,
        onResult: (OperationResult) -> Unit
    ) {
        val address = runCatching { KnxGroupAddress.parse(groupAddress) }.getOrElse {
            mainHandler.post { onResult(OperationResult.Failure(it.message ?: "Dirección KNX inválida")) }
            return
        }
        sendTelegram(KnxTelegram.GroupValueRead(address), onResult)
    }

    private fun sendTelegramBlocking(
        telegram: KnxTelegram,
        operationTimeoutMillis: Int = timeoutMillis
    ): OperationResult {
        val udpSocket: DatagramSocket
        val currentChannel: Int
        synchronized(lock) {
            udpSocket = socket ?: return OperationResult.Failure("No existe un túnel KNX/IP conectado")
            currentChannel = channelId ?: return OperationResult.Failure("Canal KNX/IP no disponible")
        }
        if (udpSocket.isClosed || state != State.CONNECTED) {
            return OperationResult.Failure("No existe un túnel KNX/IP conectado")
        }

        return try {
            val sequence = sequenceCounter.getAndUpdate { (it + 1) and 0xFF }
            val cemi = KnxTelegramEncoder.encode(telegram)
            val request = KnxProtocol.buildTunnellingRequest(currentChannel, sequence, cemi)
            KnxProtocol.validateTunnellingRequest(request, currentChannel, sequence, cemi.size)
                ?.let { return OperationResult.Failure("Telegrama KNX/IP inválido: $it") }

            udpSocket.soTimeout = operationTimeoutMillis
            var sentAtNanos = System.nanoTime()
            var transmissionAttempts = 1
            udpSocket.send(DatagramPacket(request, request.size))

            var acknowledged = false
            var gatewayAckHex: String? = null
            var gatewayRoundTripMillis: Long? = null
            var incoming: IncomingGroupTelegram? = null
            var incomingPacketHex: String? = null
            var incomingCemiHex: String? = null
            var incomingMessageCode: Int? = null
            var incomingApci: String? = null
            var ignoredAckCount = 0
            var invalidPacketCount = 0
            var duplicateIncomingCount = 0
            var lastIncomingSequence: Int? = null
            val deadline = System.currentTimeMillis() + operationTimeoutMillis
            var writeObservationDeadline: Long? = null

            while (System.currentTimeMillis() < deadline) {
                val now = System.currentTimeMillis()
                val waitingForRead = telegram is KnxTelegram.GroupValueRead && incoming == null
                val waitingForWriteObservation = telegram !is KnxTelegram.GroupValueRead &&
                    acknowledged && incoming == null &&
                    (writeObservationDeadline == null || now < writeObservationDeadline!!)
                if (acknowledged && !waitingForRead && !waitingForWriteObservation) break

                val effectiveDeadline = listOfNotNull(
                    deadline,
                    writeObservationDeadline?.takeIf { acknowledged && telegram !is KnxTelegram.GroupValueRead }
                ).minOrNull() ?: deadline
                val remaining = (effectiveDeadline - now).coerceAtLeast(1L).toInt()
                udpSocket.soTimeout = remaining
                val responseBuffer = ByteArray(KnxProtocol.MAX_PACKET_SIZE)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                try {
                    udpSocket.receive(response)
                } catch (_: SocketTimeoutException) {
                    if (!acknowledged && transmissionAttempts <= KnxProtocol.MAX_TUNNELLING_RETRIES) {
                        transmissionAttempts += 1
                        sentAtNanos = System.nanoTime()
                        udpSocket.send(DatagramPacket(request, request.size))
                        continue
                    }
                    if (acknowledged && telegram !is KnxTelegram.GroupValueRead) break
                    continue
                }

                when (KnxProtocol.serviceType(response.data, response.length)) {
                    KnxProtocol.TUNNELLING_ACK_SERVICE -> {
                        when (val ack = KnxProtocol.parseTunnellingAck(response.data, response.length)) {
                            is KnxProtocol.TunnellingAck.Accepted -> {
                                if (ack.channelId == currentChannel && ack.sequence == sequence) {
                                    acknowledged = true
                                    lastTunnelActivityMillis = System.currentTimeMillis()
                                    gatewayAckHex = KnxHex.format(response.data.copyOf(response.length))
                                    gatewayRoundTripMillis =
                                        (System.nanoTime() - sentAtNanos) / 1_000_000L
                                    if (telegram !is KnxTelegram.GroupValueRead) {
                                        writeObservationDeadline = System.currentTimeMillis() +
                                            KnxProtocol.WRITE_RESPONSE_WINDOW_MILLIS.coerceAtMost(operationTimeoutMillis.toLong())
                                    }
                                } else {
                                    ignoredAckCount += 1
                                }
                            }
                            is KnxProtocol.TunnellingAck.Rejected -> {
                                if (ack.channelId == currentChannel && ack.sequence == sequence) {
                                    return OperationResult.Failure(
                                        "Telegrama rechazado por KNX/IP: ${KnxProtocol.statusDescription(ack.status)} " +
                                            "(0x%02X)".format(ack.status)
                                    )
                                } else {
                                    ignoredAckCount += 1
                                }
                            }
                            KnxProtocol.TunnellingAck.Invalid -> invalidPacketCount += 1
                        }
                    }
                    KnxProtocol.TUNNELLING_REQUEST_SERVICE -> {
                        val parsed = KnxProtocol.parseIncomingGroupTelegram(response.data, response.length)
                        if (parsed != null) {
                            val ackPacket = KnxProtocol.buildTunnellingAck(
                                channelId = parsed.channelId,
                                sequence = parsed.sequence
                            )
                            udpSocket.send(DatagramPacket(ackPacket, ackPacket.size))
                            if (parsed.channelId != currentChannel) {
                                invalidPacketCount += 1
                            } else if (lastIncomingSequence == parsed.sequence) {
                                duplicateIncomingCount += 1
                            } else {
                                lastIncomingSequence = parsed.sequence
                                KnxParallelEventBus.publish(parsed.telegram)
                                val acceptedByStateCache = stateRepository?.record(parsed.telegram) ?: true
                                if (!acceptedByStateCache) {
                                    duplicateIncomingCount += 1
                                }
                                if (parsed.telegram.destination == telegram.destination) {
                                    incoming = parsed.telegram
                                    incomingPacketHex = KnxHex.format(response.data.copyOf(response.length))
                                    incomingCemiHex = parsed.telegram.cemiHex
                                    incomingMessageCode = parsed.telegram.messageCode
                                    incomingApci = parsed.telegram.apci
                                }
                            }
                        } else {
                            invalidPacketCount += 1
                        }
                    }
                    null -> invalidPacketCount += 1
                    else -> Unit
                }
            }

            if (!acknowledged) {
                OperationResult.Failure("No se recibió confirmación KNX/IP del telegrama")
            } else {
                OperationResult.Success(
                    incoming = incoming,
                    diagnostic = TelegramDiagnostic(
                        channelId = currentChannel,
                        sequence = sequence,
                        cemiHex = KnxHex.format(cemi),
                        knxNetIpHex = KnxHex.format(request),
                        gatewayAcknowledged = true,
                        gatewayAckHex = gatewayAckHex,
                        gatewayRoundTripMillis = gatewayRoundTripMillis,
                        incomingKnxNetIpHex = incomingPacketHex,
                        incomingCemiHex = incomingCemiHex,
                        incomingMessageCode = incomingMessageCode,
                        incomingApci = incomingApci,
                        ignoredAckCount = ignoredAckCount,
                        invalidPacketCount = invalidPacketCount,
                        duplicateIncomingCount = duplicateIncomingCount,
                        transmissionAttempts = transmissionAttempts
                    )
                )
            }
        } catch (_: SocketTimeoutException) {
            OperationResult.Failure("Tiempo de espera agotado al enviar o recibir el telegrama KNX")
        } catch (error: Exception) {
            OperationResult.Failure(error.localizedMessage ?: error.javaClass.simpleName)
        }
    }

    /**
     * Escucha telegramas espontáneos del bus mientras no hay una operación propia
     * usando el socket. Esto es imprescindible para reflejar inmediatamente los
     * pulsadores físicos: sus GroupValueWrite pueden llegar cuando OneHouse está
     * simplemente mostrando una estancia y no está enviando ninguna lectura.
     *
     * El mismo [operationLock] serializa este receptor y las operaciones activas,
     * evitando que un ACK o una respuesta de lectura sea consumido por el hilo
     * equivocado. El timeout corto limita a unas decenas de milisegundos la espera
     * máxima antes de que un comando de la app pueda tomar el socket.
     */
    private fun startPassiveReceiver(udpSocket: DatagramSocket, expectedChannelId: Int) {
        stopPassiveReceiver()
        val cancellation = AtomicBoolean(false)
        synchronized(lock) { passiveReceiverCancellation = cancellation }

        val thread = Thread {
            while (!cancellation.get() && !udpSocket.isClosed) {
                if (!operationLock.tryLock()) {
                    try {
                        Thread.sleep(PASSIVE_RECEIVER_RETRY_MILLIS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                try {
                    if (
                        cancellation.get() ||
                        udpSocket.isClosed ||
                        state != State.CONNECTED ||
                        channelId != expectedChannelId
                    ) {
                        break
                    }

                    udpSocket.soTimeout = PASSIVE_RECEIVE_TIMEOUT_MILLIS
                    val buffer = ByteArray(KnxProtocol.MAX_PACKET_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)

                    try {
                        udpSocket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    if (KnxProtocol.serviceType(packet.data, packet.length) != KnxProtocol.TUNNELLING_REQUEST_SERVICE) {
                        continue
                    }

                    val parsed = KnxProtocol.parseIncomingGroupTelegram(packet.data, packet.length) ?: continue
                    val ackPacket = KnxProtocol.buildTunnellingAck(
                        channelId = parsed.channelId,
                        sequence = parsed.sequence
                    )
                    udpSocket.send(DatagramPacket(ackPacket, ackPacket.size))

                    if (parsed.channelId == expectedChannelId) {
                        lastTunnelActivityMillis = System.currentTimeMillis()
                        KnxParallelEventBus.publish(parsed.telegram)
                        stateRepository?.record(parsed.telegram)
                        monitorRepository?.record(
                            direction = KnxTelegramEvent.Direction.INCOMING,
                            kind = if (parsed.telegram.kind == IncomingGroupTelegram.Kind.RESPONSE) {
                                KnxTelegramEvent.Kind.GROUP_VALUE_RESPONSE
                            } else {
                                KnxTelegramEvent.Kind.GROUP_VALUE_WRITE
                            },
                            groupAddress = parsed.telegram.destination.toString(),
                            value = parsed.telegram.booleanValue?.let { if (it) "1" else "0" },
                            status = KnxTelegramEvent.Status.RECEIVED,
                            detail = "RX espontáneo · origen ${parsed.telegram.sourceAddress} · ${parsed.telegram.apci}"
                        )
                    }
                } catch (_: SocketTimeoutException) {
                    // El timeout corto solo permite ceder el socket a comandos.
                } catch (_: Exception) {
                    if (cancellation.get() || udpSocket.isClosed) break
                } finally {
                    if (operationLock.isHeldByCurrentThread) {
                        operationLock.unlock()
                    }
                }
            }
        }.apply {
            name = "KnxPassiveReceiver"
            isDaemon = true
            start()
        }

        synchronized(lock) { passiveReceiverThread = thread }
    }

    private fun stopPassiveReceiver() {
        val thread = synchronized(lock) {
            passiveReceiverCancellation.set(true)
            passiveReceiverThread.also { passiveReceiverThread = null }
        }
        thread?.interrupt()
    }

    override fun close() {
        cancelScheduledDisconnect()
        disconnectInternal(sendRequest = true, reason = "Cierre del gestor KNX")
    }

    private fun openTunnel(
        target: KnxEndpoint,
        cancellation: AtomicBoolean,
        source: String
    ): ConnectResult {
        return try {
            val targetAddress = InetAddress.getByName(target.host)
            if (targetAddress !is Inet4Address) {
                state = State.DISCONNECTED
                ConnectResult.NetworkError("La dirección debe ser IPv4")
            } else {
                val udpSocket = DatagramSocket()
                synchronized(lock) {
                    socket = udpSocket
                    endpoint = target
                }

                udpSocket.soTimeout = timeoutMillis
                udpSocket.connect(InetSocketAddress(targetAddress, target.port))

                val ownAddress = udpSocket.localAddress as? Inet4Address
                    ?: run {
                        udpSocket.close()
                        state = State.DISCONNECTED
                        return ConnectResult.NetworkError("No se pudo obtener la dirección IPv4 local")
                    }

                localAddress = ownAddress

                // Transporte productivo: en remoto se intenta primero KNXnet/IP NAT mode
                // (HPAI 0.0.0.0:0). Se conserva temporalmente el fallback HPAI local
                // tras timeout sin respuesta. En LAN se ejecuta un único intento estándar.
                val attemptModes = if (target.natMode) listOf(true, false) else listOf(false)
                var response: DatagramPacket? = null
                var effectiveNatMode = target.natMode

                for ((attemptIndex, attemptNatMode) in attemptModes.withIndex()) {
                    if (cancellation.get()) {
                        udpSocket.close()
                        clearSession()
                        return ConnectResult.Cancelled
                    }

                    val modeLabel = if (attemptNatMode) "NAT_ESTANDAR" else if (target.natMode) "COMPAT_HPAI_LOCAL" else "LOCAL"
                    val request = KnxProtocol.buildConnectRequest(
                        ownAddress, udpSocket.localPort, natMode = attemptNatMode
                    )
                    monitorRepository?.record(
                        direction = KnxTelegramEvent.Direction.SYSTEM,
                        kind = KnxTelegramEvent.Kind.CONNECT,
                        status = KnxTelegramEvent.Status.PENDING,
                        detail = "Apertura túnel KNX/IP · modo $modeLabel · destino ${target.host}:${target.port} · origen $source"
                    )
                    val sendStartedAt = System.currentTimeMillis()
                    udpSocket.send(DatagramPacket(request, request.size))

                    try {
                        val responseBuffer = ByteArray(KnxProtocol.MAX_PACKET_SIZE)
                        val candidate = DatagramPacket(responseBuffer, responseBuffer.size)
                        udpSocket.receive(candidate)
                        val responseMillis = System.currentTimeMillis() - sendStartedAt
                        monitorRepository?.record(
                            direction = KnxTelegramEvent.Direction.SYSTEM,
                            kind = KnxTelegramEvent.Kind.CONNECT,
                            status = KnxTelegramEvent.Status.CONFIRMED,
                            detail = "Respuesta KNX/IP recibida · modo $modeLabel · ${responseMillis} ms · origen $source"
                        )
                        response = candidate
                        effectiveNatMode = attemptNatMode
                        break
                    } catch (error: PortUnreachableException) {
                        monitorRepository?.record(
                            direction = KnxTelegramEvent.Direction.SYSTEM,
                            kind = KnxTelegramEvent.Kind.CONNECT,
                            status = if (attemptIndex < attemptModes.lastIndex) KnxTelegramEvent.Status.PENDING else KnxTelegramEvent.Status.ERROR,
                            detail = "ICMP Port Unreachable · modo $modeLabel · destino ${target.host}:${target.port} · ${error.localizedMessage ?: "sin detalle"} · origen $source"
                        )
                    } catch (_: SocketTimeoutException) {
                        monitorRepository?.record(
                            direction = KnxTelegramEvent.Direction.SYSTEM,
                            kind = KnxTelegramEvent.Kind.CONNECT,
                            status = if (attemptIndex < attemptModes.lastIndex) KnxTelegramEvent.Status.PENDING else KnxTelegramEvent.Status.ERROR,
                            detail = "Timeout al abrir túnel ${target.host}:${target.port} · modo $modeLabel · origen $source"
                        )
                    }
                }

                val received = response ?: run {
                    udpSocket.close()
                    clearSession()
                    return ConnectResult.Timeout
                }

                when (val parsed = KnxProtocol.parseConnectResponse(received.data, received.length)) {
                    is KnxProtocol.ConnectResponse.Accepted -> {
                        // Conservamos el modo que realmente consiguió respuesta para
                        // que DISCONNECT_REQUEST anuncie el mismo tipo de HPAI.
                        synchronized(lock) { endpoint = target.copy(natMode = effectiveNatMode) }
                        if (cancellation.get()) {
                            runCatching {
                                val disconnect = KnxProtocol.buildDisconnectRequest(
                                    channelId = parsed.channelId,
                                    localAddress = ownAddress,
                                    localPort = udpSocket.localPort,
                                    natMode = effectiveNatMode
                                )
                                udpSocket.send(DatagramPacket(disconnect, disconnect.size))
                            }
                            udpSocket.close()
                            clearSession()
                            ConnectResult.Cancelled
                        } else {
                            channelId = parsed.channelId
                            sequenceCounter.set(0)
                            state = State.CONNECTED
                            lastTunnelActivityMillis = System.currentTimeMillis()
                            startPassiveReceiver(udpSocket, parsed.channelId)
                            KnxPerformanceMetrics.recordTunnelConnectSuccess(source)
                            monitorRepository?.record(
                                direction = KnxTelegramEvent.Direction.SYSTEM,
                                kind = KnxTelegramEvent.Kind.CONNECT,
                                status = KnxTelegramEvent.Status.CONFIRMED,
                                detail = "Túnel KNX/IP conectado · modo ${if (effectiveNatMode) "NAT_ESTANDAR" else if (target.natMode) "COMPAT_HPAI_LOCAL" else "LOCAL"} · canal ${parsed.channelId} · origen $source"
                            )
                            ConnectResult.Success(
                                deviceAddress = received.address.hostAddress ?: target.host,
                                channelId = parsed.channelId
                            )
                        }
                    }

                    is KnxProtocol.ConnectResponse.Rejected -> {
                        KnxPerformanceMetrics.recordTunnelRejected(parsed.status, source)
                        udpSocket.close()
                        clearSession()
                        ConnectResult.Rejected(parsed.status)
                    }

                    KnxProtocol.ConnectResponse.Invalid -> {
                        udpSocket.close()
                        clearSession()
                        ConnectResult.InvalidResponse
                    }
                }
            }
        } catch (error: Exception) {
            monitorRepository?.record(
                direction = KnxTelegramEvent.Direction.SYSTEM,
                kind = KnxTelegramEvent.Kind.CONNECT,
                status = KnxTelegramEvent.Status.ERROR,
                detail = "Fallo durante negociación KNX/IP · ${error.javaClass.simpleName}: ${error.localizedMessage ?: "sin detalle"} · destino ${target.host}:${target.port} · origen $source"
            )
            synchronized(lock) { socket?.close() }
            clearSession()
            if (cancellation.get()) ConnectResult.Cancelled
            else ConnectResult.NetworkError(error.localizedMessage ?: error.javaClass.simpleName)
        }
    }

    private fun recordConnectResult(result: ConnectResult, endpoint: KnxEndpoint, source: String) {
        val (status, detail) = when (result) {
            is ConnectResult.Success -> KnxTelegramEvent.Status.CONFIRMED to
                "Túnel abierto · ${endpoint.host}:${endpoint.port} · canal ${result.channelId} · origen $source"
            ConnectResult.Timeout -> KnxTelegramEvent.Status.ERROR to
                "Timeout al abrir túnel ${endpoint.host}:${endpoint.port} · origen $source"
            is ConnectResult.Rejected -> KnxTelegramEvent.Status.ERROR to
                "Túnel rechazado · código ${result.status} (${KnxProtocol.statusDescription(result.status)}) · origen $source"
            is ConnectResult.NetworkError -> KnxTelegramEvent.Status.ERROR to
                "Error de red al abrir túnel · ${result.detail} · origen $source"
            ConnectResult.InvalidResponse -> KnxTelegramEvent.Status.ERROR to
                "Respuesta KNX/IP no válida al abrir túnel · origen $source"
            ConnectResult.Cancelled -> KnxTelegramEvent.Status.ERROR to
                "Apertura de túnel cancelada · origen $source"
        }
        monitorRepository?.record(
            direction = KnxTelegramEvent.Direction.SYSTEM,
            kind = KnxTelegramEvent.Kind.CONNECT,
            status = status,
            detail = detail
        )
    }

    private fun disconnectInternal(sendRequest: Boolean, reason: String = "Desconexión") {
        val udpSocket: DatagramSocket?
        val currentChannel: Int?
        val ownAddress: Inet4Address?
        val currentEndpoint: KnxEndpoint?

        stopPassiveReceiver()
        synchronized(lock) {
            cancelled.set(true)
            worker?.interrupt()
            worker = null
            udpSocket = socket
            currentChannel = channelId
            ownAddress = localAddress
            currentEndpoint = endpoint
            state = if (udpSocket != null && !udpSocket.isClosed) {
                State.DISCONNECTING
            } else {
                State.DISCONNECTED
            }
        }

        if (
            sendRequest &&
            udpSocket != null &&
            !udpSocket.isClosed &&
            currentChannel != null &&
            ownAddress != null
        ) {
            runCatching {
                val packet = KnxProtocol.buildDisconnectRequest(
                    channelId = currentChannel,
                    localAddress = ownAddress,
                    localPort = udpSocket.localPort,
                    natMode = currentEndpoint?.natMode == true
                )
                udpSocket.send(DatagramPacket(packet, packet.size))
            }
        }

        if (udpSocket != null && !udpSocket.isClosed && currentChannel != null) {
            KnxPerformanceMetrics.recordTunnelDisconnect()
            monitorRepository?.record(
                direction = KnxTelegramEvent.Direction.SYSTEM,
                kind = KnxTelegramEvent.Kind.DISCONNECT,
                status = KnxTelegramEvent.Status.CONFIRMED,
                detail = "$reason · canal $currentChannel"
            )
        }
        udpSocket?.close()
        clearSession()
    }

    private fun clearSession() {
        synchronized(lock) {
            socket = null
            channelId = null
            localAddress = null
            endpoint = null
            connectingEndpoint = null
            worker = null
            state = State.DISCONNECTED
            lastTunnelActivityMillis = 0L
        }
    }
}

/**
 * Parámetros comunes del transporte KNXnet/IP.
 */
private const val DEFAULT_IDLE_DISCONNECT_MILLIS = 90_000L
// Si Android/HyperOS suspende el Handler de cierre, nunca reutilizamos un canal
// que lleva demasiado tiempo inactivo: el gateway puede haberlo caducado.
private const val MAX_SAFE_TUNNEL_IDLE_MILLIS = 60_000L
private const val PASSIVE_RECEIVE_TIMEOUT_MILLIS = 40
private const val PASSIVE_RECEIVER_RETRY_MILLIS = 4L


internal object KnxProtocol {
    const val DEFAULT_TIMEOUT_MILLIS = 4_000
    const val MAX_PACKET_SIZE = 512
    const val WRITE_RESPONSE_WINDOW_MILLIS = 650L
    const val MAX_TUNNELLING_RETRIES = 1

    private const val CONNECT_RESPONSE = 0x0206
    private const val CEMI_L_DATA_IND = 0x29
    private const val CEMI_L_DATA_CON = 0x2E
    private const val DISCONNECT_REQUEST = 0x0209
    private const val TUNNELLING_REQUEST = 0x0420
    private const val TUNNELLING_ACK = 0x0421
    const val TUNNELLING_REQUEST_SERVICE = TUNNELLING_REQUEST
    const val TUNNELLING_ACK_SERVICE = TUNNELLING_ACK

    fun buildConnectRequest(
        localAddress: Inet4Address,
        localPort: Int,
        natMode: Boolean = false
    ): ByteArray {
        val hpai = buildHpai(localAddress, localPort, natMode)
        return byteArrayOf(
            0x06, 0x10,
            0x02, 0x05,
            0x00, 0x1A,
            *hpai,
            *hpai,
            0x04, 0x04,
            0x02, 0x00
        )
    }

    fun buildDisconnectRequest(
        channelId: Int,
        localAddress: Inet4Address,
        localPort: Int,
        natMode: Boolean = false
    ): ByteArray = byteArrayOf(
        0x06, 0x10,
        ((DISCONNECT_REQUEST ushr 8) and 0xFF).toByte(),
        (DISCONNECT_REQUEST and 0xFF).toByte(),
        0x00, 0x10,
        (channelId and 0xFF).toByte(),
        0x00,
        *buildHpai(localAddress, localPort, natMode)
    )


    fun buildTunnellingRequest(
        channelId: Int,
        sequence: Int,
        cemi: ByteArray
    ): ByteArray {
        val totalLength = 10 + cemi.size
        return byteArrayOf(
            0x06, 0x10,
            ((TUNNELLING_REQUEST ushr 8) and 0xFF).toByte(),
            (TUNNELLING_REQUEST and 0xFF).toByte(),
            ((totalLength ushr 8) and 0xFF).toByte(),
            (totalLength and 0xFF).toByte(),
            0x04,
            (channelId and 0xFF).toByte(),
            (sequence and 0xFF).toByte(),
            0x00,
            *cemi
        )
    }


    fun validateTunnellingRequest(
        packet: ByteArray,
        expectedChannelId: Int,
        expectedSequence: Int,
        expectedCemiLength: Int
    ): String? {
        if (packet.size < 10) return "cabecera incompleta"
        if ((packet[0].toInt() and 0xFF) != 0x06) return "longitud de cabecera distinta de 6"
        if ((packet[1].toInt() and 0xFF) != 0x10) return "versión KNXnet/IP no compatible"
        if (serviceType(packet, packet.size) != TUNNELLING_REQUEST) return "servicio distinto de TUNNELLING_REQUEST"
        val declaredLength = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        if (declaredLength != packet.size) return "longitud declarada $declaredLength y real ${packet.size}"
        if ((packet[6].toInt() and 0xFF) != 0x04) return "estructura de conexión distinta de 4 bytes"
        if ((packet[7].toInt() and 0xFF) != (expectedChannelId and 0xFF)) return "canal incorrecto"
        if ((packet[8].toInt() and 0xFF) != (expectedSequence and 0xFF)) return "secuencia incorrecta"
        if ((packet[9].toInt() and 0xFF) != 0x00) return "byte reservado distinto de cero"
        if (packet.size - 10 != expectedCemiLength) return "longitud cEMI incorrecta"
        return null
    }

    fun serviceType(data: ByteArray, length: Int): Int? {
        if (length < 6) return null
        if ((data[0].toInt() and 0xFF) != 6 || (data[1].toInt() and 0xFF) != 0x10) return null
        return ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
    }


    fun statusDescription(status: Int): String = when (status and 0xFF) {
        0x00 -> "sin error"
        0x21 -> "identificador de conexión no válido"
        0x23 -> "secuencia no válida"
        0x24 -> "error de conexión KNX"
        0x26 -> "opción de conexión no admitida"
        0x27 -> "tipo de conexión no admitido"
        0x29 -> "sin más conexiones disponibles"
        0x2D -> "error de datos KNX"
        else -> "estado desconocido"
    }

    fun buildTunnellingAck(channelId: Int, sequence: Int): ByteArray = byteArrayOf(
        0x06, 0x10, 0x04, 0x21, 0x00, 0x0A,
        0x04, (channelId and 0xFF).toByte(), (sequence and 0xFF).toByte(), 0x00
    )

    data class ParsedIncoming(
        val channelId: Int,
        val sequence: Int,
        val telegram: KnxConnectionManager.IncomingGroupTelegram
    )

    fun parseIncomingGroupTelegram(data: ByteArray, length: Int): ParsedIncoming? {
        if (serviceType(data, length) != TUNNELLING_REQUEST || length < 21) return null
        val totalLength = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (totalLength != length || (data[6].toInt() and 0xFF) != 4) return null

        val channel = data[7].toInt() and 0xFF
        val sequence = data[8].toInt() and 0xFF
        val cemiOffset = 10
        val messageCode = data[cemiOffset].toInt() and 0xFF
        if (messageCode != CEMI_L_DATA_IND && messageCode != CEMI_L_DATA_CON) return null

        val additionalLength = data[cemiOffset + 1].toInt() and 0xFF
        val frameOffset = cemiOffset + 2 + additionalLength
        val apduSecondOffset = frameOffset + 8
        if (apduSecondOffset >= length) return null

        val control2 = data[frameOffset + 1].toInt() and 0xFF
        if ((control2 and 0x80) == 0) return null

        val sourceRaw = ((data[frameOffset + 2].toInt() and 0xFF) shl 8) or
            (data[frameOffset + 3].toInt() and 0xFF)
        val destinationRaw = ((data[frameOffset + 4].toInt() and 0xFF) shl 8) or
            (data[frameOffset + 5].toInt() and 0xFF)
        val dataLength = data[frameOffset + 6].toInt() and 0xFF
        if (dataLength < 1 || frameOffset + 7 + dataLength >= length) return null

        val apduFirst = data[frameOffset + 7].toInt() and 0xFF
        val apduSecond = data[apduSecondOffset].toInt() and 0xFF
        val apciCode = ((apduFirst and 0x03) shl 2) or ((apduSecond ushr 6) and 0x03)
        val (kind, apciName) = when (apciCode) {
            0x01 -> KnxConnectionManager.IncomingGroupTelegram.Kind.RESPONSE to "GroupValueResponse"
            0x02 -> KnxConnectionManager.IncomingGroupTelegram.Kind.WRITE to "GroupValueWrite"
            else -> return null
        }

        val source = "${(sourceRaw ushr 12) and 0x0F}.${(sourceRaw ushr 8) and 0x0F}.${sourceRaw and 0xFF}"
        val destination = KnxGroupAddress.fromRaw(destinationRaw)
        val cemi = data.copyOfRange(cemiOffset, totalLength)
        return ParsedIncoming(
            channelId = channel,
            sequence = sequence,
            telegram = KnxConnectionManager.IncomingGroupTelegram(
                kind = kind,
                sourceAddress = source,
                destination = destination,
                booleanValue = if (dataLength <= 1) (apduSecond and 0x01) == 1 else null,
                payload = if (dataLength <= 1) {
                    byteArrayOf((apduSecond and 0x01).toByte())
                } else {
                    val payloadStart = apduSecondOffset + 1
                    val payloadEnd = minOf(length, frameOffset + 8 + dataLength)
                    if (payloadStart < payloadEnd) data.copyOfRange(payloadStart, payloadEnd) else byteArrayOf()
                },
                messageCode = messageCode,
                apci = apciName,
                cemiHex = KnxHex.format(cemi)
            )
        )
    }

    fun parseTunnellingAck(data: ByteArray, length: Int): TunnellingAck {
        if (length < 10) return TunnellingAck.Invalid
        val serviceType = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val totalLength = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val structureLength = data[6].toInt() and 0xFF
        if (data[0].toInt() and 0xFF != 6 || data[1].toInt() and 0xFF != 0x10 ||
            serviceType != TUNNELLING_ACK || totalLength !in 10..length || structureLength != 4
        ) return TunnellingAck.Invalid

        val channel = data[7].toInt() and 0xFF
        val sequence = data[8].toInt() and 0xFF
        val status = data[9].toInt() and 0xFF
        return if (status == 0) TunnellingAck.Accepted(channel, sequence)
        else TunnellingAck.Rejected(channel, sequence, status)
    }

    fun parseConnectResponse(data: ByteArray, length: Int): ConnectResponse {
        if (length < 8) return ConnectResponse.Invalid

        val headerLength = data[0].toInt() and 0xFF
        val protocolVersion = data[1].toInt() and 0xFF
        val serviceType = ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)
        val totalLength = ((data[4].toInt() and 0xFF) shl 8) or
            (data[5].toInt() and 0xFF)

        if (
            headerLength != 6 ||
            protocolVersion != 0x10 ||
            serviceType != CONNECT_RESPONSE ||
            totalLength !in 8..length
        ) {
            return ConnectResponse.Invalid
        }

        val channelId = data[6].toInt() and 0xFF
        val status = data[7].toInt() and 0xFF
        return if (status == 0) {
            ConnectResponse.Accepted(channelId)
        } else {
            ConnectResponse.Rejected(status)
        }
    }

    private fun buildHpai(
        localAddress: Inet4Address,
        localPort: Int,
        natMode: Boolean = false
    ): ByteArray {
        // KNXnet/IP NAT mode anuncia 0.0.0.0:0. El servidor debe usar la
        // dirección/puerto de origen UDP observados tras la traducción NAT.
        val ip = if (natMode) byteArrayOf(0, 0, 0, 0) else localAddress.address
        val advertisedPort = if (natMode) 0 else localPort
        return byteArrayOf(
            0x08, 0x01,
            ip[0], ip[1], ip[2], ip[3],
            ((advertisedPort ushr 8) and 0xFF).toByte(),
            (advertisedPort and 0xFF).toByte()
        )
    }

    sealed interface TunnellingAck {
        data class Accepted(val channelId: Int, val sequence: Int) : TunnellingAck
        data class Rejected(val channelId: Int, val sequence: Int, val status: Int) : TunnellingAck
        data object Invalid : TunnellingAck
    }

    sealed interface ConnectResponse {
        data class Accepted(val channelId: Int) : ConnectResponse
        data class Rejected(val status: Int) : ConnectResponse
        data object Invalid : ConnectResponse
    }
}

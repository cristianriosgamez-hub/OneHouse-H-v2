package com.onehouse.app.knx

import com.onehouse.app.feature.h08.H08Safety

import android.content.Context
import com.onehouse.app.data.knx.SettingsDataStore
import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ejecuta comandos normalizados sobre un túnel KNXnet/IP.
 *
 * En la Entrega 6 se habilitan lectura de grupo y escritura DPT 1.x
 * (encendido/apagado). Los DPT que requieren codificación adicional se
 * rechazan de forma explícita para evitar telegramas incorrectos.
 */
class KnxCommandExecutor(context: Context) : Closeable {
    sealed interface Result {
        data class Success(
            val busValue: String? = null,
            val sourceAddress: String? = null,
            val diagnostic: KnxConnectionManager.TelegramDiagnostic? = null
        ) : Result
        data class Failure(val message: String) : Result
    }

    private val appContext = context.applicationContext
    private val centralResources = KnxCentralEngine.get(appContext)
    private val stateRepository = centralResources.stateRepository
    private val connectionManager = centralResources.connectionManager
    private val monitorRepository = centralResources.monitorRepository
    private val statisticsRepository = centralResources.statisticsRepository
    private var queuedOperation: Closeable? = null

    /** Flujo compartido con todos los últimos estados KNX conocidos. */
    val stateFlow: StateFlow<Map<String, KnxStateRepository.State>>
        get() = stateRepository.stateFlow

    /** Eventos puntuales compartidos de telegramas aceptados por el motor central. */
    val realtimeUpdates: SharedFlow<KnxStateRepository.Update>
        get() = stateRepository.updates

    /** Observa únicamente una dirección de grupo, sin polling. */
    fun observeState(groupAddress: String): Flow<KnxStateRepository.State?> =
        stateRepository.observeState(groupAddress)

    /** Devuelve inmediatamente el último estado conocido de una dirección. */
    fun getState(groupAddress: String): KnxStateRepository.State? =
        stateRepository.get(groupAddress)

    fun execute(
        command: KnxCommand,
        toggleValue: Boolean? = null,
        retryReadOnce: Boolean = true,
        verificationAddress: String? = null,
        onResult: (Result) -> Unit
    ) {
        if (H08Safety.READ_ONLY) {
            onResult(Result.Failure(H08Safety.BLOCKED_MESSAGE))
            return
        }
        queuedOperation?.close()
        queuedOperation = KnxTelegramQueue.enqueue { done ->
            executeQueued(command, toggleValue, retryReadOnce, verificationAddress) { result ->
                onResult(result)
                done()
            }
        }
    }

    private fun executeQueued(
        command: KnxCommand,
        toggleValue: Boolean?,
        retryReadOnce: Boolean,
        verificationAddress: String?,
        onResult: (Result) -> Unit
    ) {
        val telegram = telegramFor(command, toggleValue)
        if (telegram == null) {
            onResult(Result.Failure("El comando ${command.type.displayName.lowercase()} todavía no admite este DPT"))
            return
        }

        val endpointResult = resolveEndpoint()
        val endpoint = endpointResult.getOrElse {
            onResult(Result.Failure(it.message ?: "No se pudo determinar la conexión KNX/IP"))
            return
        }

        val eventKind = if (command.type == KnxCommandType.READ) {
            KnxTelegramEvent.Kind.GROUP_VALUE_READ
        } else {
            KnxTelegramEvent.Kind.GROUP_VALUE_WRITE
        }
        val eventValue = when (command.type) {
            KnxCommandType.ON -> "1"
            KnxCommandType.OFF -> "0"
            KnxCommandType.TOGGLE -> if (toggleValue == true) "1" else "0"
            KnxCommandType.UP -> "0"
            KnxCommandType.DOWN -> "1"
            KnxCommandType.POSITION,
            KnxCommandType.SET_VALUE -> command.valueHint
            else -> null
        }

        fun attempt(number: Int) {
            monitorRepository.record(
                direction = KnxTelegramEvent.Direction.SYSTEM,
                kind = KnxTelegramEvent.Kind.CONNECT,
                groupAddress = command.destination.toString(),
                status = KnxTelegramEvent.Status.PENDING,
                detail = if (number == 1) {
                    "Conectando con ${endpoint.host}:${endpoint.port}"
                } else {
                    "Reintento de lectura ${number - 1}/1"
                }
            )

            connectionManager.connect(endpoint, source = "COMMAND_EXECUTOR") { connectResult ->
                when (connectResult) {
                    is KnxConnectionManager.ConnectResult.Success -> {
                        statisticsRepository.recordConnectionSuccess()
                        monitorRepository.record(
                            direction = KnxTelegramEvent.Direction.SYSTEM,
                            kind = KnxTelegramEvent.Kind.CONNECT,
                            groupAddress = command.destination.toString(),
                            status = KnxTelegramEvent.Status.CONFIRMED,
                            detail = "Túnel KNX/IP conectado (canal ${connectResult.channelId})"
                        )
                        monitorRepository.record(
                            direction = KnxTelegramEvent.Direction.OUTGOING,
                            kind = eventKind,
                            groupAddress = command.destination.toString(),
                            value = eventValue,
                            status = KnxTelegramEvent.Status.PENDING
                        )
                        val operationStartedAt = System.currentTimeMillis()
                        connectionManager.sendTelegram(telegram) { operation ->
                            if (command.type == KnxCommandType.READ &&
                                operation is KnxConnectionManager.OperationResult.Success &&
                                operation.incoming == null &&
                                number == 1 &&
                                retryReadOnce
                            ) {
                                connectionManager.scheduleDisconnect()
                                attempt(2)
                                return@sendTelegram
                            }

                            recordOperationStatistics(operation)
                            val primaryResult = operation.toExecutorResult()
                            recordOutgoingResult(
                                groupAddress = command.destination.toString(),
                                eventKind = eventKind,
                                eventValue = eventValue,
                                result = primaryResult
                            )

                            val shouldVerifyWrite = command.type != KnxCommandType.READ &&
                                operation is KnxConnectionManager.OperationResult.Success
                            if (!shouldVerifyWrite) {
                                connectionManager.scheduleDisconnect()
                                onResult(primaryResult)
                                return@sendTelegram
                            }

                            val expectedBoolean = expectedBooleanFor(command, toggleValue)
                            val confirmationAddress = verificationAddress
                                ?.takeIf(AppKnxConfigurationRepository::isValidGroupAddress)
                                ?: command.destination.toString()
                            val cachedConfirmation = stateRepository
                                .get(confirmationAddress)
                                ?.takeIf { state ->
                                    state.timestampMillis >= operationStartedAt &&
                                        expectedBoolean != null &&
                                        state.booleanValue == expectedBoolean
                                }

                            if (cachedConfirmation != null) {
                                monitorRepository.record(
                                    direction = KnxTelegramEvent.Direction.SYSTEM,
                                    kind = KnxTelegramEvent.Kind.GROUP_VALUE_RESPONSE,
                                    groupAddress = confirmationAddress,
                                    value = cachedConfirmation.rawValue,
                                    status = KnxTelegramEvent.Status.CONFIRMED,
                                    detail = buildString {
                                        append("Escritura confirmada por el estado recibido en tiempo real")
                                        append(" · origen ${cachedConfirmation.sourceAddress}")
                                        append(" · ${cachedConfirmation.apci}")
                                    }
                                )
                                connectionManager.scheduleDisconnect()
                                onResult(
                                    Result.Success(
                                        busValue = cachedConfirmation.booleanValue?.let {
                                            if (it) "Encendido" else "Apagado"
                                        },
                                        sourceAddress = cachedConfirmation.sourceAddress,
                                        diagnostic = (primaryResult as? Result.Success)?.diagnostic
                                    )
                                )
                                return@sendTelegram
                            }

                            monitorRepository.record(
                                direction = KnxTelegramEvent.Direction.OUTGOING,
                                kind = KnxTelegramEvent.Kind.GROUP_VALUE_READ,
                                groupAddress = confirmationAddress,
                                status = KnxTelegramEvent.Status.PENDING,
                                detail = "No llegó confirmación en tiempo real; se solicita lectura de verificación"
                            )
                            connectionManager.readGroupValue(confirmationAddress) { verification ->
                                recordOperationStatistics(verification)
                                val verificationResult = verification.toExecutorResult()
                                recordOutgoingResult(
                                    groupAddress = confirmationAddress,
                                    eventKind = KnxTelegramEvent.Kind.GROUP_VALUE_READ,
                                    eventValue = null,
                                    result = verificationResult,
                                    successPrefix = "Verificación posterior a escritura"
                                )
                                connectionManager.scheduleDisconnect()

                                val combined = when {
                                    primaryResult is Result.Failure -> primaryResult
                                    verificationResult is Result.Success && verificationResult.busValue != null -> {
                                        Result.Success(
                                            busValue = verificationResult.busValue,
                                            sourceAddress = verificationResult.sourceAddress,
                                            diagnostic = (primaryResult as? Result.Success)?.diagnostic
                                        )
                                    }
                                    else -> primaryResult
                                }
                                onResult(combined)
                            }
                        }
                    }
                    KnxConnectionManager.ConnectResult.Timeout -> reportConnectionFailure(
                        command,
                        "Tiempo de espera agotado al conectar con KNX/IP",
                        onResult
                    )
                    is KnxConnectionManager.ConnectResult.Rejected -> reportConnectionFailure(
                        command,
                        "El interfaz KNX/IP rechazó el túnel (${connectResult.status})",
                        onResult
                    )
                    is KnxConnectionManager.ConnectResult.NetworkError -> reportConnectionFailure(
                        command,
                        connectResult.detail,
                        onResult
                    )
                    KnxConnectionManager.ConnectResult.InvalidResponse -> reportConnectionFailure(
                        command,
                        "Respuesta KNX/IP no válida",
                        onResult
                    )
                    KnxConnectionManager.ConnectResult.Cancelled -> onResult(Result.Failure("Operación cancelada"))
                }
            }
        }

        attempt(1)
    }


    private fun recordOperationStatistics(operation: KnxConnectionManager.OperationResult) {
        when (operation) {
            is KnxConnectionManager.OperationResult.Success -> operation.diagnostic?.let {
                statisticsRepository.recordOperation(
                    diagnostic = it,
                    receivedFromBus = operation.incoming != null
                )
            }
            is KnxConnectionManager.OperationResult.Failure,
            is KnxConnectionManager.OperationResult.NotAvailable -> statisticsRepository.recordOperationError()
        }
    }

    private fun recordOutgoingResult(
        groupAddress: String,
        eventKind: KnxTelegramEvent.Kind,
        eventValue: String?,
        result: Result,
        successPrefix: String? = null
    ) {
        monitorRepository.record(
            direction = KnxTelegramEvent.Direction.OUTGOING,
            kind = eventKind,
            groupAddress = groupAddress,
            value = eventValue,
            status = if (result is Result.Success) {
                KnxTelegramEvent.Status.CONFIRMED
            } else {
                KnxTelegramEvent.Status.ERROR
            },
            detail = when (result) {
                is Result.Success -> result.diagnostic?.let { diagnostic ->
                    buildString {
                        successPrefix?.let { append("$it\n") }
                        append("TX validado · canal ${diagnostic.channelId} · secuencia ${diagnostic.sequence}\n")
                        append("cEMI TX: ${diagnostic.cemiHex}\n")
                        append("KNXnet/IP TX: ${diagnostic.knxNetIpHex}")
                        diagnostic.gatewayAckHex?.let { append("\nACK gateway: $it") }
                        diagnostic.gatewayRoundTripMillis?.let { append("\nTiempo TX→ACK: ${it} ms") }
                        diagnostic.incomingKnxNetIpHex?.let { append("\nTelegrama bus RX: $it") }
                        diagnostic.incomingCemiHex?.let { append("\ncEMI RX: $it") }
                        diagnostic.incomingMessageCode?.let { append("\nMensaje cEMI RX: 0x%02X".format(it)) }
                        diagnostic.incomingApci?.let { append(" · APCI $it") }
                        if (diagnostic.ignoredAckCount > 0) append("\nACK ajenos ignorados: ${diagnostic.ignoredAckCount}")
                        if (diagnostic.invalidPacketCount > 0) append("\nPaquetes no válidos ignorados: ${diagnostic.invalidPacketCount}")
                        if (diagnostic.duplicateIncomingCount > 0) append("\nTelegramas entrantes duplicados: ${diagnostic.duplicateIncomingCount}")
                        append("\nIntentos de transmisión: ${diagnostic.transmissionAttempts}")
                    }
                } ?: successPrefix
                is Result.Failure -> result.message
            }
        )
    }


    private fun reportConnectionFailure(
        command: KnxCommand,
        message: String,
        onResult: (Result) -> Unit
    ) {
        statisticsRepository.recordConnectionError()
        monitorRepository.record(
            direction = KnxTelegramEvent.Direction.SYSTEM,
            kind = KnxTelegramEvent.Kind.CONNECT,
            groupAddress = command.destination.toString(),
            status = KnxTelegramEvent.Status.ERROR,
            detail = message
        )
        onResult(Result.Failure(message))
    }

    private fun KnxConnectionManager.OperationResult.toExecutorResult(): Result = when (this) {
        is KnxConnectionManager.OperationResult.Success -> {
            val incoming = incoming
            if (incoming != null) {
                val value = incoming.booleanValue?.let { if (it) "Encendido" else "Apagado" }
                monitorRepository.record(
                    direction = KnxTelegramEvent.Direction.INCOMING,
                    kind = if (incoming.kind == KnxConnectionManager.IncomingGroupTelegram.Kind.RESPONSE) {
                        KnxTelegramEvent.Kind.GROUP_VALUE_RESPONSE
                    } else {
                        KnxTelegramEvent.Kind.GROUP_VALUE_WRITE
                    },
                    groupAddress = incoming.destination.toString(),
                    value = incoming.booleanValue?.let { if (it) "1" else "0" },
                    status = KnxTelegramEvent.Status.RECEIVED,
                    detail = buildString {
                        append("Origen ${incoming.sourceAddress}")
                        append(" · ${incoming.apci}")
                        append(" · cEMI 0x%02X".format(incoming.messageCode))
                        append("\n${incoming.cemiHex}")
                    }
                )
                Result.Success(
                    busValue = value,
                    sourceAddress = incoming.sourceAddress,
                    diagnostic = diagnostic
                )
            } else {
                Result.Success(diagnostic = diagnostic)
            }
        }
        is KnxConnectionManager.OperationResult.Failure -> Result.Failure(detail)
        is KnxConnectionManager.OperationResult.NotAvailable -> Result.Failure(detail)
    }


    private fun expectedBooleanFor(
        command: KnxCommand,
        toggleValue: Boolean?
    ): Boolean? = when (command.type) {
        KnxCommandType.ON -> true
        KnxCommandType.OFF -> false
        KnxCommandType.TOGGLE -> toggleValue
        else -> null
    }

    private fun telegramFor(command: KnxCommand, toggleValue: Boolean?): KnxTelegram? = when (command.type) {
        KnxCommandType.READ -> KnxTelegram.GroupValueRead(command.destination)
        KnxCommandType.ON -> KnxTelegram.GroupValueWriteBoolean(command.destination, true)
        KnxCommandType.OFF -> KnxTelegram.GroupValueWriteBoolean(command.destination, false)
        KnxCommandType.TOGGLE -> toggleValue?.let {
            KnxTelegram.GroupValueWriteBoolean(command.destination, it)
        }
        KnxCommandType.UP -> KnxTelegram.GroupValueWriteBoolean(command.destination, false)
        KnxCommandType.DOWN -> KnxTelegram.GroupValueWriteBoolean(command.destination, true)
        // DPT 1.007 Step/Stop: un telegrama sobre el objeto de parada detiene
        // el movimiento actual. Se usa TRUE, compatible con el objeto PARADA
        // configurado en la instalación Schneider KNX.
        KnxCommandType.STOP -> KnxTelegram.GroupValueWriteBoolean(command.destination, true)
        KnxCommandType.POSITION -> command.valueHint?.toDoubleOrNull()?.let {
            KnxTelegram.GroupValueWritePercent(command.destination, it)
        }
        KnxCommandType.SET_VALUE -> command.valueHint?.toDoubleOrNull()?.let {
            when {
                command.dpt.startsWith("9") -> KnxTelegram.GroupValueWriteTemperature(command.destination, it)
                command.dpt.startsWith("5") -> KnxTelegram.GroupValueWritePercent(command.destination, it)
                command.dpt.startsWith("20") -> KnxTelegram.GroupValueWriteByte(command.destination, it.toInt())
                else -> null
            }
        }
        else -> null
    }

    private fun resolveEndpoint(): kotlin.Result<KnxEndpoint> = runCatching {
        val settings = SettingsDataStore(appContext).read()
        val detector = NetworkConnectionDetector(appContext)
        val network = try {
            detector.currentState()
        } finally {
            detector.close()
        }

        require(network.isConnected) { "El dispositivo no tiene conexión de red" }
        val useLocal = network.usesLocalRoute
        val host = (if (useLocal) settings.localIp else settings.remoteIp).trim()
        val portText = (if (useLocal) settings.localPort else settings.remotePort).trim()
        val route = if (useLocal) "local" else "remota"
        require(host.isNotBlank()) { "Configura la dirección KNX/IP $route" }
        val port = portText.toIntOrNull()
        require(port != null && port in 1..65535) { "Configura un puerto KNX/IP válido para la ruta $route" }
        KnxEndpoint(host, port, natMode = !useLocal)
    }

    override fun close() {
        queuedOperation?.close()
        queuedOperation = null
        // El gestor de conexión pertenece al motor central compartido. Cada
        // operación ya desconecta el túnel al finalizar; cerrar una pantalla no
        // debe invalidar los recursos que están usando las demás pantallas.
    }
}

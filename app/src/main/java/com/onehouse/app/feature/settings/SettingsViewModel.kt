package com.onehouse.app.feature.settings

import android.os.Handler
import android.os.Looper
import com.onehouse.app.data.knx.KnxConnectionStatus
import com.onehouse.app.data.knx.KnxSettings
import com.onehouse.app.data.knx.KnxSettingsRepository
import com.onehouse.app.data.knx.KnxSettingsValidation
import com.onehouse.app.knx.KnxConnectionManager
import com.onehouse.app.knx.KnxConnectionTester
import com.onehouse.app.knx.KnxEndpoint
import com.onehouse.app.knx.KnxHomeStateRepository
import com.onehouse.app.knx.NetworkConnectionDetector
import java.io.Closeable

class SettingsViewModel(
    private val repository: KnxSettingsRepository,
    private val networkDetector: NetworkConnectionDetector,
    private val connectionManager: KnxConnectionManager? = null
) {
    enum class ConnectionRoute {
        LOCAL,
        REMOTE,
        NONE
    }

    private val observers = mutableSetOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectionTest: Closeable? = null
    private var networkObservation: Closeable? = null

    var settings: KnxSettings = repository.load()
        private set

    var validation: KnxSettingsValidation = validate(settings)
        private set

    var networkState: NetworkConnectionDetector.State = networkDetector.currentState()
        private set

    var connectionStatus: KnxConnectionStatus = settings.lastConnectionStatus
        private set

    var statusMessage: String = settings.lastStatusMessage
        private set

    val selectedRoute: ConnectionRoute
        get() = if (settings.autoReconnect) {
            routeFor(networkState)
        } else if (networkState.isConnected) {
            ConnectionRoute.LOCAL
        } else {
            ConnectionRoute.NONE
        }

    val selectedEndpoint: String
        get() = selectedKnxEndpoint()?.let { "${it.host}:${it.port}" } ?: "No disponible"

    val lastTestEpochMillis: Long
        get() = settings.lastTestEpochMillis

    init {
        if (settings.lastTestEndpoint.isNotBlank() && settings.lastTestEndpoint != selectedEndpoint) {
            connectionStatus = KnxConnectionStatus.NOT_TESTED
            statusMessage = "La ruta actual todavía no se ha comprobado"
        }

        networkObservation = networkDetector.observe { newState ->
            mainHandler.post {
                val previousRoute = selectedRoute
                networkState = newState
                val newRoute = selectedRoute

                if (previousRoute != newRoute) {
                    connectionTest?.close()
                    connectionTest = null
                    updateTestState(
                        status = KnxConnectionStatus.NOT_TESTED,
                        message = "La red ha cambiado. Conexión pendiente de comprobar",
                        updateTimestamp = false,
                        testedEndpoint = ""
                    )
                } else {
                    notifyObservers()
                }
            }
        }
    }

    fun observe(observer: () -> Unit): Closeable {
        observers += observer
        observer()
        return Closeable { observers -= observer }
    }

    fun update(change: (KnxSettings) -> KnxSettings) {
        settings = change(settings).copy(
            lastUpdatedEpochMillis = System.currentTimeMillis(),
            lastConnectionStatus = KnxConnectionStatus.NOT_TESTED,
            lastStatusMessage = "Cambios guardados. Conexión pendiente de comprobar",
            lastTestEndpoint = ""
        )
        validation = validate(settings)
        connectionStatus = KnxConnectionStatus.NOT_TESTED
        statusMessage = settings.lastStatusMessage
        repository.save(settings)
        notifyObservers()
    }

    fun testConnection() {
        // REV1: Home puede recibir más de un evento de ciclo de vida durante el
        // arranque. Si ya hay una apertura en curso no la cancelamos ni abrimos
        // otra: dejamos que el intento central termine y sea reutilizado.
        if (connectionStatus == KnxConnectionStatus.TESTING && connectionTest != null) return

        networkState = networkDetector.currentState()
        validation = validate(settings)

        if (!networkState.isConnected || selectedRoute == ConnectionRoute.NONE) {
            failTest("El dispositivo no tiene conexión de red")
            return
        }

        val endpoint = selectedKnxEndpoint()
        if (endpoint == null) {
            val message = when (selectedRoute) {
                ConnectionRoute.LOCAL -> validation.localIpError ?: validation.localPortError
                ConnectionRoute.REMOTE -> validation.remoteIpError
                    ?: validation.remotePortError
                    ?: "Introduce la dirección IP remota"
                ConnectionRoute.NONE -> "No hay una ruta disponible"
            }
            failTest(message ?: "Revisa los datos de conexión")
            return
        }

        connectionTest?.close()
        updateTestState(
            status = KnxConnectionStatus.TESTING,
            message = "Abriendo túnel KNX/IP por la ruta ${routeLabel(selectedRoute).lowercase()}…",
            updateTimestamp = false,
            persist = false
        )

        val testedRoute = selectedRoute
        connectionTest = KnxConnectionTester.test(
            endpoint = endpoint,
            connectionManager = connectionManager
        ) { result ->
            connectionTest = null
            val finalStatus: KnxConnectionStatus
            val finalMessage: String

            when (result) {
                is KnxConnectionTester.Result.Success -> {
                    finalStatus = KnxConnectionStatus.CONNECTED
                    finalMessage = "Túnel KNX/IP correcto por ${routeLabel(testedRoute).lowercase()} (${result.deviceAddress})"
                    // Si la app arrancó sin KNX, el test manual de conexión debe
                    // relanzar al instante la carga inicial real de estados.
                    KnxHomeStateRepository.notifyConnectionAvailable()
                }
                KnxConnectionTester.Result.Timeout -> {
                    finalStatus = KnxConnectionStatus.FAILED
                    finalMessage = "Sin respuesta KNX/IP en ${endpoint.host}:${endpoint.port}."
                }
                is KnxConnectionTester.Result.Rejected -> {
                    finalStatus = KnxConnectionStatus.FAILED
                    finalMessage = "El interfaz KNX/IP rechazó el túnel (código ${result.status})"
                }
                KnxConnectionTester.Result.InvalidResponse -> {
                    finalStatus = KnxConnectionStatus.FAILED
                    finalMessage = "El equipo respondió, pero no aceptó KNXnet/IP Tunnelling"
                }
                is KnxConnectionTester.Result.NetworkError -> {
                    finalStatus = KnxConnectionStatus.FAILED
                    finalMessage = "Error de red: ${result.detail}"
                }
            }

            updateTestState(
                status = finalStatus,
                message = finalMessage,
                updateTimestamp = true,
                testedEndpoint = "${endpoint.host}:${endpoint.port}"
            )
        }
    }

    fun close() {
        connectionTest?.close()
        connectionTest = null
        networkObservation?.close()
        networkObservation = null
        networkDetector.close()
        observers.clear()
    }

    private fun selectedKnxEndpoint(): KnxEndpoint? {
        val host: String
        val portText: String
        val error: String?

        when (selectedRoute) {
            ConnectionRoute.LOCAL -> {
                host = settings.localIp.trim()
                portText = settings.localPort.trim()
                error = validation.localIpError ?: validation.localPortError
            }
            ConnectionRoute.REMOTE -> {
                host = settings.remoteIp.trim()
                portText = settings.remotePort.trim()
                error = if (host.isBlank()) {
                    "Introduce la dirección IP remota"
                } else {
                    ipError(host, required = true) ?: validation.remotePortError
                }
            }
            ConnectionRoute.NONE -> return null
        }

        val port = portText.toIntOrNull()
        return if (error == null && port != null) KnxEndpoint(
            host, port, natMode = selectedRoute == ConnectionRoute.REMOTE
        ) else null
    }

    private fun failTest(message: String) {
        updateTestState(
            status = KnxConnectionStatus.FAILED,
            message = message,
            updateTimestamp = true,
            testedEndpoint = selectedEndpoint.takeUnless { it == "No disponible" }.orEmpty()
        )
    }

    private fun updateTestState(
        status: KnxConnectionStatus,
        message: String,
        updateTimestamp: Boolean,
        persist: Boolean = true,
        testedEndpoint: String = settings.lastTestEndpoint
    ) {
        connectionStatus = status
        statusMessage = message
        settings = settings.copy(
            lastTestEpochMillis = if (updateTimestamp) System.currentTimeMillis() else settings.lastTestEpochMillis,
            lastConnectionStatus = status,
            lastStatusMessage = message,
            lastTestEndpoint = testedEndpoint
        )
        if (persist) repository.save(settings)
        notifyObservers()
    }

    private fun routeFor(state: NetworkConnectionDetector.State): ConnectionRoute = when {
        !state.isConnected -> ConnectionRoute.NONE
        state.usesLocalRoute -> ConnectionRoute.LOCAL
        state.usesRemoteRoute -> ConnectionRoute.REMOTE
        else -> ConnectionRoute.NONE
    }

    private fun routeLabel(route: ConnectionRoute): String = when (route) {
        ConnectionRoute.LOCAL -> "Local"
        ConnectionRoute.REMOTE -> "Remota"
        ConnectionRoute.NONE -> "Sin ruta"
    }

    private fun notifyObservers() = observers.toList().forEach { it() }

    private fun validate(value: KnxSettings): KnxSettingsValidation = KnxSettingsValidation(
        localIpError = ipError(value.localIp, required = true),
        localPortError = portError(value.localPort),
        remoteIpError = ipError(value.remoteIp, required = false),
        remotePortError = portError(value.remotePort)
    )

    private fun ipError(value: String, required: Boolean): String? {
        if (value.isBlank()) return if (required) "Introduce una dirección IP" else null
        val parts = value.split('.')
        return if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) null
        else "Dirección IP no válida"
    }

    private fun portError(value: String): String? {
        val port = value.toIntOrNull()
        return if (port != null && port in 1..65535) null else "Puerto no válido"
    }
}

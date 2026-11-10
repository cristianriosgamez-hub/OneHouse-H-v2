package com.onehouse.app.knx

import java.io.Closeable

/**
 * Compatibilidad para las pantallas que únicamente necesitan comprobar la
 * apertura del túnel. La implementación real se centraliza ahora en
 * [KnxConnectionManager].
 */
object KnxConnectionTester {
    sealed interface Result {
        data class Success(
            val deviceAddress: String,
            val channelId: Int
        ) : Result

        data object Timeout : Result
        data class Rejected(val status: Int) : Result
        data class NetworkError(val detail: String) : Result
        data object InvalidResponse : Result
    }

    fun test(
        endpoint: KnxEndpoint,
        timeoutMillis: Int = KnxProtocol.DEFAULT_TIMEOUT_MILLIS,
        connectionManager: KnxConnectionManager? = null,
        onResult: (Result) -> Unit
    ): Closeable {
        val ownsManager = connectionManager == null
        val manager = connectionManager ?: KnxConnectionManager(timeoutMillis)
        val operation = manager.connect(endpoint, source = "CONNECTION_TEST") { result ->
            when (result) {
                is KnxConnectionManager.ConnectResult.Success -> {
                    // v1.12.1.5: una comprobación no debe destruir el túnel central.
                    // Lo dejamos disponible unos segundos para que navegación,
                    // lecturas y comandos puedan reutilizar la misma sesión.
                    manager.scheduleDisconnect()
                    onResult(Result.Success(result.deviceAddress, result.channelId))
                }
                KnxConnectionManager.ConnectResult.Timeout -> onResult(Result.Timeout)
                is KnxConnectionManager.ConnectResult.Rejected -> {
                    onResult(Result.Rejected(result.status))
                }
                is KnxConnectionManager.ConnectResult.NetworkError -> {
                    onResult(Result.NetworkError(result.detail))
                }
                KnxConnectionManager.ConnectResult.InvalidResponse -> {
                    onResult(Result.InvalidResponse)
                }
                KnxConnectionManager.ConnectResult.Cancelled -> Unit
            }
        }

        return Closeable {
            operation.close()
            // Si el manager pertenece al motor central no se cierra desde el test:
            // cerrarlo aquí era precisamente lo que forzaba nuevas aperturas.
            if (ownsManager) manager.close()
        }
    }

    fun test(
        host: String,
        port: Int,
        onResult: (Result) -> Unit
    ): Closeable = test(KnxEndpoint(host.trim(), port), onResult = onResult)
}

/**
 * Punto de conexión KNXnet/IP compartido por la configuración y el futuro
 * control de dispositivos.
 */
data class KnxEndpoint(
    val host: String,
    val port: Int,
    /** Usa KNXnet/IP NAT mode: HPAI 0.0.0.0:0 para que el gateway responda al origen UDP real. */
    val natMode: Boolean = false
)

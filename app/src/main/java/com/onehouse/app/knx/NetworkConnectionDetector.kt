package com.onehouse.app.knx

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.Closeable

/**
 * Detecta el tipo de conexión de red activa del dispositivo.
 *
 * La clase se registra sobre la red predeterminada de Android y notifica los
 * cambios entre WiFi, datos móviles, Ethernet y ausencia de conexión. El
 * callback siempre recibe primero el estado actual y después cualquier cambio.
 */
class NetworkConnectionDetector(context: Context) : Closeable {

    enum class NetworkType {
        WIFI,
        MOBILE,
        ETHERNET,
        OTHER,
        OFFLINE
    }

    data class State(
        val type: NetworkType,
        val isConnected: Boolean,
        val isValidated: Boolean
    ) {
        val usesLocalRoute: Boolean
            get() = type == NetworkType.WIFI || type == NetworkType.ETHERNET

        val usesRemoteRoute: Boolean
            get() = type == NetworkType.MOBILE || type == NetworkType.OTHER
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val observers = mutableSetOf<(State) -> Unit>()
    private var isRegistered = false

    var state: State = readCurrentState()
        private set

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh(network)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateState(networkCapabilities.toState())
        }

        override fun onLost(network: Network) {
            refresh()
        }
    }

    /**
     * Empieza a observar los cambios de red.
     *
     * El observador recibe inmediatamente el estado actual. La función devuelve
     * un [Closeable] para cancelar únicamente esa suscripción.
     */
    fun observe(observer: (State) -> Unit): Closeable {
        synchronized(observers) {
            observers += observer
            if (!isRegistered) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                isRegistered = true
                state = readCurrentState()
            }
        }

        observer(state)

        return Closeable {
            synchronized(observers) {
                observers -= observer
                if (observers.isEmpty()) unregisterCallback()
            }
        }
    }

    /** Devuelve una lectura puntual del estado actual sin registrar callbacks. */
    fun currentState(): State {
        state = readCurrentState()
        return state
    }

    override fun close() {
        synchronized(observers) {
            observers.clear()
            unregisterCallback()
        }
    }

    private fun refresh(network: Network? = connectivityManager.activeNetwork) {
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        updateState(capabilities?.toState() ?: offlineState())
    }

    private fun readCurrentState(): State {
        val network = connectivityManager.activeNetwork ?: return offlineState()
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return offlineState()
        return capabilities.toState()
    }

    private fun NetworkCapabilities.toState(): State {
        val type = when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }

        return State(
            type = type,
            isConnected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
    }

    private fun updateState(newState: State) {
        if (newState == state) return
        state = newState
        val snapshot = synchronized(observers) { observers.toList() }
        snapshot.forEach { it(newState) }
    }

    private fun unregisterCallback() {
        if (!isRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        isRegistered = false
    }

    private fun offlineState() = State(
        type = NetworkType.OFFLINE,
        isConnected = false,
        isValidated = false
    )
}

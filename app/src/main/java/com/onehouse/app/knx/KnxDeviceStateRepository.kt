package com.onehouse.app.knx

import android.content.Context
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Caché persistente de estados KNX.
 *
 * En esta entrega registra el resultado de comandos locales. El método
 * [updateFromBus] queda preparado para que el receptor de GroupValueResponse
 * actualice exactamente el mismo flujo en próximas entregas.
 */
class KnxDeviceStateRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val observers = mutableMapOf<String, CopyOnWriteArraySet<(KnxDeviceState) -> Unit>>()

    fun get(deviceId: String): KnxDeviceState = read(deviceId)
        ?: KnxDeviceState(deviceId, null, KnxDeviceState.Source.UNKNOWN, 0L)

    fun observe(deviceId: String, observer: (KnxDeviceState) -> Unit): Closeable {
        val listeners = observers.getOrPut(deviceId) { CopyOnWriteArraySet() }
        listeners += observer
        observer(get(deviceId))
        return Closeable {
            listeners -= observer
            if (listeners.isEmpty()) observers.remove(deviceId)
        }
    }

    fun updateFromLocalCommand(deviceId: String, value: String) {
        save(KnxDeviceState(deviceId, value, KnxDeviceState.Source.LOCAL_COMMAND, System.currentTimeMillis()))
    }

    fun updateFromBus(deviceId: String, value: String) {
        save(KnxDeviceState(deviceId, value, KnxDeviceState.Source.BUS_RESPONSE, System.currentTimeMillis()))
    }

    fun clear() {
        preferences.edit().clear().apply()
        observers.keys.toList().forEach { deviceId ->
            notify(KnxDeviceState(deviceId, null, KnxDeviceState.Source.UNKNOWN, 0L))
        }
    }

    private fun save(state: KnxDeviceState) {
        val encoded = listOf(
            state.value.orEmpty(),
            state.source.name,
            state.updatedAtMillis.toString()
        ).joinToString(SEPARATOR)
        preferences.edit().putString(state.deviceId, encoded).apply()
        notify(state)
    }

    private fun read(deviceId: String): KnxDeviceState? {
        val encoded = preferences.getString(deviceId, null) ?: return null
        val parts = encoded.split(SEPARATOR)
        return KnxDeviceState(
            deviceId = deviceId,
            value = parts.getOrNull(0)?.ifBlank { null },
            source = parts.getOrNull(1)?.let {
                runCatching { KnxDeviceState.Source.valueOf(it) }.getOrNull()
            } ?: KnxDeviceState.Source.UNKNOWN,
            updatedAtMillis = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        )
    }

    private fun notify(state: KnxDeviceState) {
        observers[state.deviceId]?.forEach { it(state) }
    }

    private companion object {
        const val PREFERENCES_NAME = "knx_device_states"
        const val SEPARATOR = "\u001F"
    }
}

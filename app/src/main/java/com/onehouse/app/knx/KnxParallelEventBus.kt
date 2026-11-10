package com.onehouse.app.knx

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * v1.12.1 - Canal paralelo de eventos KNX.
 *
 * Recibe una copia inmutable de cada telegrama válido antes de que éste siga
 * por la ruta clásica. No controla dispositivos ni sustituye todavía a la
 * caché/observers actuales.
 */
object KnxParallelEventBus {
    private val mutableEvents = MutableSharedFlow<KnxConnectionManager.IncomingGroupTelegram>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<KnxConnectionManager.IncomingGroupTelegram>
        get() = mutableEvents.asSharedFlow()

    fun publish(telegram: KnxConnectionManager.IncomingGroupTelegram) {
        val startNanos = System.nanoTime()
        mutableEvents.tryEmit(telegram)
        KnxPerformanceMetrics.recordParallelDispatch(System.nanoTime() - startNanos)
    }
}

package com.onehouse.app.knx

import android.content.Context

/**
 * Punto único de acceso a los recursos compartidos del motor KNX de OneHouse.
 *
 * Todas las pantallas reutilizan la misma caché, monitor, estadísticas y gestor
 * de túnel. La cola global de [KnxTelegramQueue] continúa serializando las
 * operaciones para evitar que dos pantallas intenten utilizar el túnel a la vez.
 *
 * La fase 2 añade un flujo de eventos en tiempo real compartido. Cualquier
 * telegrama aceptado por la caché central se publica una sola vez y puede ser
 * observado por todas las pantallas sin lanzar lecturas adicionales.
 *
 * Desde v1.12.1.2 la carga masiva y los comandos comparten también el mismo
 * gestor de túnel, evitando que la navegación abra sesiones KNX/IP paralelas.
 */
internal object KnxCentralEngine {

    class Resources internal constructor(context: Context) {
        private val appContext = context.applicationContext

        val stateRepository = KnxStateRepository(appContext)
        val monitorRepository = KnxTelegramMonitorRepository(appContext)
        val healthRepository = KnxEngineHealthRepository(stateRepository)
        val realtimeDispatcher = KnxRealtimeDispatcher(stateRepository)
        val subscriptionManager = KnxSubscriptionManager(
            stateRepository = stateRepository,
            realtimeDispatcher = realtimeDispatcher
        )
        val connectionManager = KnxConnectionManager(
            stateRepository = stateRepository,
            monitorRepository = monitorRepository
        )
        val statisticsRepository = KnxSessionStatisticsRepository(appContext)
    }

    @Volatile
    private var resources: Resources? = null

    fun get(context: Context): Resources =
        resources ?: synchronized(this) {
            resources ?: Resources(context.applicationContext).also { resources = it }
        }
}

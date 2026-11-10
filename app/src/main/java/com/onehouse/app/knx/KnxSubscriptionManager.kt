package com.onehouse.app.knx

import kotlinx.coroutines.flow.Flow

/**
 * Fachada de suscripción para pantallas y componentes de OneHouse.
 *
 * Centraliza el acceso a la instantánea KNX y a los cambios en tiempo real sin
 * crear túneles adicionales ni duplicar repositorios por pantalla.
 */
class KnxSubscriptionManager internal constructor(
    private val stateRepository: KnxStateRepository,
    private val realtimeDispatcher: KnxRealtimeDispatcher
) {

    /** Último estado conocido de una dirección y posteriores actualizaciones. */
    fun observe(groupAddress: String): Flow<KnxStateRepository.State?> =
        stateRepository.observeState(groupAddress.trim())

    /** Instantánea filtrada y posteriores actualizaciones de varias direcciones. */
    fun observe(groupAddresses: Set<String>): Flow<Map<String, KnxStateRepository.State>> =
        stateRepository.observeStates(groupAddresses)

    /** Eventos puntuales en tiempo real para una única dirección. */
    fun realtime(groupAddress: String): Flow<KnxStateRepository.State> =
        realtimeDispatcher.updatesFor(groupAddress)

    /** Eventos puntuales en tiempo real para varias direcciones. */
    fun realtime(groupAddresses: Set<String>): Flow<KnxStateRepository.State> =
        realtimeDispatcher.updatesFor(groupAddresses)

    fun snapshot(groupAddresses: Set<String>): Map<String, KnxStateRepository.State> =
        stateRepository.snapshot(groupAddresses)
}

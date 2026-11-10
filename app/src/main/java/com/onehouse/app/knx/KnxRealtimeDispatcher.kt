package com.onehouse.app.knx

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

/**
 * Distribuidor ligero de actualizaciones KNX en tiempo real.
 *
 * No abre conexiones ni realiza lecturas adicionales: consume exclusivamente
 * los eventos aceptados por [KnxStateRepository] y permite que cada pantalla
 * observe solo las direcciones que utiliza.
 */
class KnxRealtimeDispatcher internal constructor(
    private val stateRepository: KnxStateRepository
) {

    /** Todos los cambios de estado aceptados por la caché central. */
    val updates: Flow<KnxStateRepository.State> =
        stateRepository.updates.mapNotNull { update ->
            (update as? KnxStateRepository.Update.StateChanged)?.state
        }

    /** Cambios de una única dirección de grupo. */
    fun updatesFor(groupAddress: String): Flow<KnxStateRepository.State> {
        val normalizedAddress = groupAddress.trim()
        return updates.filter { state -> state.groupAddress == normalizedAddress }
    }

    /** Cambios correspondientes a cualquiera de las direcciones indicadas. */
    fun updatesFor(groupAddresses: Set<String>): Flow<KnxStateRepository.State> {
        val normalizedAddresses = groupAddresses
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

        return updates.filter { state -> state.groupAddress in normalizedAddresses }
    }
}

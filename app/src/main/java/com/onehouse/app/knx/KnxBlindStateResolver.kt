package com.onehouse.app.knx

import com.onehouse.app.device.ImportedKnxDevice

/**
 * Resolución central del estado real de persianas KNX (v1.12.3).
 *
 * La UI y el resumen de Inicio deben utilizar la realimentación de posición
 * del actuador (DPT 5.001) y no deducir la posición del último mando local.
 * Así, una maniobra hecha desde un pulsador físico termina reflejándose en
 * OneHouse cuando llega el telegrama de estado de altura.
 */
internal object KnxBlindStateResolver {

    /**
     * Direcciones candidatas a contener la posición real.
     *
     * En el esquema actual, la GA de estado mantiene el mismo subgrupo que la
     * GA de escritura de posición (ej. 2/1/9 -> 2/2/9). Priorizamos esa GA y
     * dejamos el resto de lecturas como respaldo para proyectos importados.
     */
    fun positionStateAddresses(device: ImportedKnxDevice): List<String> {
        val positionSub = device.blindPositionAddress?.sub
        val preferred = device.readAddresses.filter { address ->
            positionSub != null && address.sub == positionSub
        }
        return (preferred + device.readAddresses)
            .map { it.toString() }
            .distinct()
    }

    fun confirmedPositionPercent(
        device: ImportedKnxDevice,
        states: Map<String, KnxStateRepository.State>
    ): Float? = positionStateAddresses(device)
        .asSequence()
        .mapNotNull { address -> states[address]?.takeIf(StateFreshness::isTrusted) }
        .sortedByDescending { state -> state.timestampMillis }
        .mapNotNull { state ->
            state.rawValue
                ?.let { raw -> KnxValueDecoder.decode(raw, "5.001") }
                ?.takeIf { value -> value.isFinite() && value in 0f..100f }
        }
        .firstOrNull()
}

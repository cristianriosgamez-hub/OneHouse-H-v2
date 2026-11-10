package com.onehouse.app.knx

import com.onehouse.knxlab.knx.H077ClimateFanCodec

/**
 * v1.12.4
 *
 * Fuente única de verdad para los estados de climatización.
 * Lee exclusivamente las direcciones KNX de ESTADO configuradas en OneHouse.
 * No deduce estado a partir del último mando enviado.
 */
internal object KnxClimateStateResolver {

    data class Snapshot(
        val powered: Boolean?,
        val currentTemperature: Float?,
        val targetTemperature: Float?,
        val mode: String?,
        val fanSpeed: String?
    )

    fun resolve(states: Map<String, KnxStateRepository.State>): Snapshot {
        fun trusted(address: String): KnxStateRepository.State? =
            states[address]?.takeIf(StateFreshness::isTrusted)

        fun boolAt(address: String): Boolean? =
            trusted(address)?.booleanValue

        fun numberAt(address: String, dpt: String): Float? =
            KnxValueDecoder.decode(trusted(address)?.rawValue, dpt)

        fun strictTemperatureAt(address: String, range: ClosedFloatingPointRange<Float>): Float? {
            val raw = trusted(address)?.rawValue ?: return null
            // DPT 9.001 real: dos bytes. Evitamos reinterpretar otras longitudes.
            if (raw.length != 4) return null
            return KnxValueDecoder.decode(raw, "9.001")
                ?.takeIf { it.isFinite() && it in range }
        }

        val modeCode = numberAt(KnxAddressBook.Climate.MODE_STATE, "20.105")?.toInt()
        val mode = when (modeCode) {
            0 -> "Auto"
            1 -> "Calor"
            3 -> "Frío"
            9 -> "Ventilador"
            14 -> "Dry"
            else -> null
        }

        // H08: manda MEDIA como 0x5E a 5/2/5, pero el actuador comunicó
        // físicamente MEDIA=0x80 en 5/3/5. NO clasificar por cercanía al
        // porcentaje de mando: un estado desconocido se muestra como ---.
        val fanRawHex = trusted(KnxAddressBook.Climate.FAN_SPEED_STATE)?.rawValue
        val fanRaw = H077ClimateFanCodec.decodeHex(fanRawHex.orEmpty())
        val fanSpeed = when {
            H077ClimateFanCodec.matchesLow(fanRaw) -> "Baja"
            H077ClimateFanCodec.matchesMedium(fanRaw) -> "Media"
            fanRaw == 255 -> "Alta" // estado 100 %; prueba física aún pendiente
            else -> null
        }

        return Snapshot(
            powered = boolAt(KnxAddressBook.Climate.POWER_STATE),
            currentTemperature = strictTemperatureAt(
                KnxAddressBook.Climate.CURRENT_TEMPERATURE,
                -20f..60f
            ),
            targetTemperature = strictTemperatureAt(
                KnxAddressBook.Climate.TARGET_TEMPERATURE_PRIMARY,
                16f..34f
            ),
            mode = mode,
            fanSpeed = fanSpeed
        )
    }
}

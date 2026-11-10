package com.onehouse.app.feature.maintenance

import com.onehouse.app.knx.KnxCommand
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxGroupAddress

internal data class MaintenanceCommand(val command: KnxCommand, val stateAddress: String?)
internal data class MaintenancePlan(val commands: List<MaintenanceCommand>, val unavailable: String? = null)

/** Addresses from the legacy app, checked against ETS line 3.15.
 * These are one-bit writes, not DPT 17/18 scene numbers. */
internal data class MaintenanceBitScene(val oneAddress: String, val zeroAddress: String) {
    fun plan(activationValue: Boolean?): MaintenancePlan {
        if (activationValue == null) return MaintenancePlan(emptyList(), "Pendiente de verificar la activación en ETS")
        return booleanMaintenancePlan(if (activationValue) oneAddress else zeroAddress, activationValue)
    }
}

internal object MaintenanceAddresses {
    val lightsOff = MaintenanceBitScene("1/1/50", "1/1/50")
    val generalOff = MaintenanceBitScene("5/5/3", "5/5/14")
    val closeBlinds = MaintenanceBitScene("2/1/50", "2/1/53")
    const val VALVE_COMMAND = "2/3/1"
    const val VALVE_STATE = com.onehouse.app.knx.KnxStartupReadPolicy.VALVE_STATE

    fun lightsOffPlan() = lightsOff.plan(false)
    // The user explicitly retained the legacy central call, including the valve channel.
    fun generalOffPlan() = generalOff.plan(false)
    fun closeBlindsPlan() = closeBlinds.plan(true)
}

internal fun booleanMaintenancePlan(commandAddress: String, value: Boolean, stateAddress: String? = null): MaintenancePlan {
    val address = runCatching { KnxGroupAddress.parse(commandAddress) }.getOrNull()
        ?: return MaintenancePlan(emptyList(), "Dirección de mando no válida")
    return MaintenancePlan(listOf(MaintenanceCommand(
        KnxCommand(if (value) KnxCommandType.ON else KnxCommandType.OFF, address, "1.001"), stateAddress
    )))
}

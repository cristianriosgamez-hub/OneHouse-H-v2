package com.onehouse.app.device

import com.onehouse.app.knx.KnxConnectionManager
import com.onehouse.app.knx.KnxGroupAddress
import com.onehouse.app.knx.KnxTelegram

/** Actuador KNX DPT 1.001 para una luz con encendido y apagado. */
class LightDevice(
    private val connectionManager: KnxConnectionManager,
    val name: String,
    val writeAddress: KnxGroupAddress,
    val statusAddress: KnxGroupAddress = writeAddress
) {
    fun setOn(
        enabled: Boolean,
        onResult: (KnxConnectionManager.OperationResult) -> Unit
    ) {
        connectionManager.sendTelegram(
            KnxTelegram.GroupValueWriteBoolean(writeAddress, enabled),
            onResult
        )
    }

    fun requestStatus(onResult: (KnxConnectionManager.OperationResult) -> Unit) {
        connectionManager.sendTelegram(
            KnxTelegram.GroupValueRead(statusAddress),
            onResult
        )
    }
}

package com.onehouse.app.feature.more

import com.onehouse.app.knx.AppKnxObject
import com.onehouse.app.knx.OneHouseKnxUsagePolicy

/** El editor/diagnóstico muestra exactamente los objetos importados que usa OneHouse. */
internal object AppKnxObjectFilter {
    fun isVisible(roomName: String, device: AppKnxObject): Boolean =
        roomName.trim().equals(device.roomName.trim(), ignoreCase = true) &&
            OneHouseKnxUsagePolicy.isUsedAppObject(device)
}

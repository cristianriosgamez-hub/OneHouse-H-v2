package com.onehouse.app.device

import com.onehouse.app.knx.AppKnxCategory
import com.onehouse.app.knx.AppKnxObject
import com.onehouse.app.knx.KnxCommand
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxGroupAddress

/** Representación normalizada de un objeto KNX utilizado por OneHouse. */
data class ImportedKnxDevice(
    val id: String,
    val roomName: String,
    val name: String,
    val category: AppKnxCategory,
    val controlKind: ControlKind,
    val writeAddresses: List<KnxGroupAddress>,
    val readAddresses: List<KnxGroupAddress>,
    val dataPointType: String?,
    val resolvedDpt: String,
    val unit: String?,
    val isFavourite: Boolean,
    val commands: List<KnxCommand>,
    val source: AppKnxObject
) {
    val primaryWriteAddress: KnxGroupAddress?
        get() = writeAddresses.firstOrNull()

    val primaryReadAddress: KnxGroupAddress?
        get() = readAddresses.firstOrNull()

    /** Direcciones semánticas para controles con más de un objeto de comunicación. */
    val blindMoveAddress: KnxGroupAddress?
        get() = writeAddresses.getOrNull(0)

    val blindStopAddress: KnxGroupAddress?
        get() = writeAddresses.getOrNull(1) ?: blindMoveAddress

    val blindPositionAddress: KnxGroupAddress?
        get() = writeAddresses.getOrNull(2) ?: blindMoveAddress

    val canWrite: Boolean
        get() = commands.any { it.type != KnxCommandType.READ }

    val canRead: Boolean
        get() = commands.any { it.type == KnxCommandType.READ }

    val iconGlyph: String
        get() = when (controlKind) {
            ControlKind.BOOLEAN_SWITCH -> if (category == AppKnxCategory.LIGHT) "💡" else "🔌"
            ControlKind.BLIND -> "🪟"
            ControlKind.TEMPERATURE -> "🌡"
            ControlKind.CLIMATE -> "❄"
            ControlKind.SCENE -> "▶"
            ControlKind.ALARM -> "⚠"
            ControlKind.SENSOR -> "◉"
            ControlKind.METER -> "⚡"
            ControlKind.READ_ONLY -> "◌"
            ControlKind.UNKNOWN -> "◆"
        }
}

enum class ControlKind(val displayName: String) {
    BOOLEAN_SWITCH("Interruptor"),
    BLIND("Persiana"),
    TEMPERATURE("Temperatura"),
    CLIMATE("Climatización"),
    SCENE("Escena"),
    ALARM("Alarma"),
    SENSOR("Sensor"),
    METER("Medición"),
    READ_ONLY("Solo lectura"),
    UNKNOWN("Sin determinar")
}

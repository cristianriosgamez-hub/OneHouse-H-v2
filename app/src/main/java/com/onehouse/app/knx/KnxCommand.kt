package com.onehouse.app.knx

/** Acción lógica que OneHouse podrá enviar al bus KNX. */
enum class KnxCommandType(val displayName: String) {
    READ("Leer estado"),
    ON("Encender"),
    OFF("Apagar"),
    TOGGLE("Alternar"),
    UP("Subir"),
    DOWN("Bajar"),
    STOP("Parar"),
    POSITION("Posición"),
    SET_VALUE("Establecer valor"),
    CALL_SCENE("Ejecutar escena")
}

/** Comando normalizado, todavía independiente del transporte KNXnet/IP. */
data class KnxCommand(
    val type: KnxCommandType,
    val destination: KnxGroupAddress,
    val dpt: String,
    val requiresValue: Boolean = false,
    val valueHint: String? = null
)

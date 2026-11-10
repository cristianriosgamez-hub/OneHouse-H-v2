package com.onehouse.app.knx

/** Dirección de grupo KNX de tres niveles: principal/intermedio/subgrupo. */
@JvmInline
value class KnxGroupAddress private constructor(val raw: Int) {
    val main: Int get() = (raw ushr 11) and 0x1F
    val middle: Int get() = (raw ushr 8) and 0x07
    val sub: Int get() = raw and 0xFF

    fun toBytes(): ByteArray = byteArrayOf(
        ((raw ushr 8) and 0xFF).toByte(),
        (raw and 0xFF).toByte()
    )

    override fun toString(): String = "$main/$middle/$sub"

    companion object {
        fun of(main: Int, middle: Int, sub: Int): KnxGroupAddress {
            require(main in 0..31) { "El grupo principal debe estar entre 0 y 31" }
            require(middle in 0..7) { "El grupo intermedio debe estar entre 0 y 7" }
            require(sub in 0..255) { "El subgrupo debe estar entre 0 y 255" }
            return KnxGroupAddress((main shl 11) or (middle shl 8) or sub)
        }

        fun fromRaw(raw: Int): KnxGroupAddress {
            require(raw in 0..0xFFFF) { "Dirección KNX fuera de rango" }
            return KnxGroupAddress(raw)
        }

        fun parse(value: String): KnxGroupAddress {
            val parts = value.trim().split('/')
            require(parts.size == 3) { "Dirección KNX inválida. Usa el formato 1/2/3" }
            return of(
                main = parts[0].toIntOrNull() ?: error("Grupo principal inválido"),
                middle = parts[1].toIntOrNull() ?: error("Grupo intermedio inválido"),
                sub = parts[2].toIntOrNull() ?: error("Subgrupo inválido")
            )
        }
    }
}

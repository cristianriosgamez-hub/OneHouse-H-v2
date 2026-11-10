package com.onehouse.app.knx

import com.onehouse.app.device.ControlKind
import com.onehouse.app.knx.AppKnxCategory
import com.onehouse.app.knx.AppKnxObject
import java.util.Locale

/**
 * Resuelve y normaliza tipos de punto de datos KNX.
 *
 * El proyecto KNX puede guardar el DPT de forma no consistente: puede faltar,
 * aparecer como "DPT 1.001", usar coma decimal o contener texto adicional.
 * Este objeto concentra la normalización para que el resto de la aplicación
 * trabaje siempre con identificadores canónicos.
 */
object KnxDptResolver {

    /** Tipo lógico de valor representado por una familia DPT. */
    enum class ValueKind {
        BOOLEAN,
        PERCENTAGE,
        FLOAT_2_BYTE,
        TIME,
        DATE,
        FLOAT_4_BYTE,
        SCENE,
        ENUMERATION,
        UNKNOWN
    }

    /** Descripción estable de un DPT ya normalizado. */
    data class Descriptor(
        val dpt: String,
        val mainNumber: Int?,
        val subNumber: Int?,
        val valueKind: ValueKind,
        val readable: Boolean,
        val writable: Boolean
    )

    /**
     * Devuelve el DPT explícito del objeto o uno deducido por su control.
     */
    fun resolve(source: AppKnxObject, controlKind: ControlKind): String {
        normalize(source.dataPointType)?.let { return it }

        return when (controlKind) {
            ControlKind.BOOLEAN_SWITCH -> "1.001"
            ControlKind.BLIND -> "1.008"
            ControlKind.TEMPERATURE -> "9.001"
            ControlKind.CLIMATE -> when (source.category) {
                AppKnxCategory.TEMPERATURE -> "9.001"
                else -> "20.102"
            }
            ControlKind.SCENE -> "17.001"
            ControlKind.ALARM,
            ControlKind.SENSOR -> "1.005"
            ControlKind.METER -> "14.000"
            ControlKind.READ_ONLY,
            ControlKind.UNKNOWN -> UNKNOWN_DPT
        }
    }

    /** Número principal de la familia DPT, por ejemplo 9 para DPT 9.001. */
    fun mainNumber(dpt: String): Int? = normalize(dpt)
        ?.substringBefore('.')
        ?.toIntOrNull()

    /** Subtipo DPT, por ejemplo 1 para DPT 9.001. */
    fun subNumber(dpt: String): Int? = normalize(dpt)
        ?.substringAfter('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.toIntOrNull()

    /**
     * Convierte distintas representaciones en el formato canónico N.SSS.
     *
     * Ejemplos:
     * - "DPT 1.1" -> "1.001"
     * - "9,001" -> "9.001"
     * - "DPT9.001 temperatura" -> "9.001"
     */
    fun normalize(value: String?): String? {
        val trimmed = value
            ?.trim()
            ?.replace(',', '.')
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (trimmed.equals(UNKNOWN_DPT, ignoreCase = true)) return null

        val match = DPT_PATTERN.find(trimmed) ?: return null
        val main = match.groupValues[1].toIntOrNull() ?: return null
        val subText = match.groupValues.getOrNull(2).orEmpty()
        val sub = subText.takeIf { it.isNotBlank() }?.toIntOrNull()

        if (main !in MIN_MAIN_DPT..MAX_MAIN_DPT) return null
        if (sub != null && sub !in MIN_SUB_DPT..MAX_SUB_DPT) return null

        return if (sub == null) {
            main.toString()
        } else {
            String.format(Locale.ROOT, "%d.%03d", main, sub)
        }
    }

    /** Obtiene metadatos útiles para codificación, UI y validación. */
    fun describe(dpt: String): Descriptor {
        val normalized = normalize(dpt)
        val main = normalized?.substringBefore('.')?.toIntOrNull()
        val sub = normalized
            ?.substringAfter('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()

        val kind = when (main) {
            1 -> ValueKind.BOOLEAN
            5 -> ValueKind.PERCENTAGE
            9 -> ValueKind.FLOAT_2_BYTE
            10 -> ValueKind.TIME
            11 -> ValueKind.DATE
            14 -> ValueKind.FLOAT_4_BYTE
            17 -> ValueKind.SCENE
            20 -> ValueKind.ENUMERATION
            else -> ValueKind.UNKNOWN
        }

        return Descriptor(
            dpt = normalized ?: UNKNOWN_DPT,
            mainNumber = main,
            subNumber = sub,
            valueKind = kind,
            readable = normalized != null,
            writable = main in WRITABLE_FAMILIES
        )
    }

    /** Indica si el DPT puede representarse como un valor booleano KNX. */
    fun isBoolean(dpt: String): Boolean = mainNumber(dpt) == 1

    /** Indica si el DPT pertenece a una familia que OneHouse conoce. */
    fun isKnown(dpt: String): Boolean = describe(dpt).valueKind != ValueKind.UNKNOWN

    /**
     * Indica si la familia está preparada para escritura lógica o numérica.
     * La codificación concreta se incorpora gradualmente en el ejecutor.
     */
    fun isWritableFamily(dpt: String): Boolean = mainNumber(dpt) in WRITABLE_FAMILIES

    private const val UNKNOWN_DPT = "desconocido"
    private const val MIN_MAIN_DPT = 1
    private const val MAX_MAIN_DPT = 999
    private const val MIN_SUB_DPT = 0
    private const val MAX_SUB_DPT = 999

    private val WRITABLE_FAMILIES = setOf(1, 5, 9, 10, 11, 14, 17, 20)

    private val DPT_PATTERN = Regex(
        pattern = "(?i)(?:DPT\\s*)?(\\d{1,3})(?:\\s*\\.\\s*(\\d{1,3}))?"
    )
}

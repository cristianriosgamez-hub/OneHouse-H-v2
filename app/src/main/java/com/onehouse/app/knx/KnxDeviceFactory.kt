package com.onehouse.app.knx

import com.onehouse.app.device.ControlKind
import com.onehouse.app.device.ImportedKnxDevice
import com.onehouse.app.knx.AppKnxCategory
import com.onehouse.app.knx.AppKnxObject
import com.onehouse.app.knx.AppKnxProject

/** Convierte objetos importados en descriptores KNX listos para construir comandos. */
object KnxDeviceFactory {
    fun create(project: AppKnxProject): List<ImportedKnxDevice> =
        project.devices.mapIndexed { index, source -> create(index, source) }

    fun create(index: Int, source: AppKnxObject): ImportedKnxDevice {
        val readAddresses = source.readAddresses.mapNotNull(::parseAddress)
        val importedWriteAddresses = source.writeAddresses.mapNotNull(::parseAddress)
        val writeAddresses = if (source.category == AppKnxCategory.BLIND) {
            resolveBlindWriteAddresses(
                importedWriteAddresses = importedWriteAddresses,
                readAddresses = readAddresses
            )
        } else {
            importedWriteAddresses.ifEmpty {
                inferWriteAddresses(source, readAddresses)
            }
        }
        val kind = controlKindFor(source, writeAddresses.isNotEmpty())
        val resolvedDpt = KnxDptResolver.resolve(source, kind)
        val stableAddress = (source.writeAddresses + source.readAddresses).firstOrNull().orEmpty()

        val provisional = ImportedKnxDevice(
            id = listOf(source.roomName, source.name, source.sourceType, stableAddress, index)
                .joinToString("|"),
            roomName = source.roomName,
            name = source.name,
            category = source.category,
            controlKind = kind,
            writeAddresses = writeAddresses,
            readAddresses = readAddresses,
            dataPointType = source.dataPointType,
            resolvedDpt = resolvedDpt,
            unit = source.unit,
            isFavourite = source.isFavourite,
            commands = emptyList(),
            source = source
        )
        return provisional.copy(commands = KnxCommandBuilder.build(provisional))
    }

    private fun parseAddress(raw: String): KnxGroupAddress? =
        runCatching { KnxGroupAddress.parse(raw) }.getOrNull()

    /**
     * Reconstruye direcciones de mando cuando el proyecto almacenado solo contiene estado.
     *
     * Patrones comprobados en proyectos ETS reales:
     *
     *  - Luces:       1/1/n mando, 1/2/n estado.
     *  - Persianas:   2/1/n mando, 2/2/n estado de posición.
     *  - Clima:       5/2/n mando, 5/3/n estado.
     *
     * La inferencia solo se usa cuando el proyecto importado no incluye ninguna
     * dirección de escritura. Las direcciones explícitas siempre tienen prioridad.
     */
    /**
     * Normaliza los tres objetos de comunicación de una persiana:
     * movimiento, parada y posición.
     *
     * Algunas configuraciones pueden contener solo una parte de las direcciones
     * de escritura. En ese caso no debemos interpretar la dirección de posición
     * como si fuera la de parada. Cuando existe el estado de altura 2/2/n se
     * reconstruye de forma segura el patrón confirmado 2/1/(n-2), 2/1/(n-1),
     * 2/1/n y se conservan las direcciones explícitas que coincidan.
     */
    private fun resolveBlindWriteAddresses(
        importedWriteAddresses: List<KnxGroupAddress>,
        readAddresses: List<KnxGroupAddress>
    ): List<KnxGroupAddress> {
        val inferred = inferBlindWriteAddresses(readAddresses)
        if (inferred.size != 3) return importedWriteAddresses
        if (importedWriteAddresses.size >= 3) return importedWriteAddresses

        return inferred.map { expected ->
            importedWriteAddresses.firstOrNull { it == expected } ?: expected
        }
    }

    private fun inferWriteAddresses(
        source: AppKnxObject,
        readAddresses: List<KnxGroupAddress>
    ): List<KnxGroupAddress> = when (source.category) {
        AppKnxCategory.LIGHT,
        AppKnxCategory.SWITCH -> inferParallelMiddleGroup(
            readAddresses = readAddresses,
            expectedMain = 1,
            readMiddle = 2,
            writeMiddle = 1
        )

        AppKnxCategory.BLIND -> inferBlindWriteAddresses(readAddresses)

        AppKnxCategory.CLIMATE -> inferParallelMiddleGroup(
            readAddresses = readAddresses,
            expectedMain = 5,
            readMiddle = 3,
            writeMiddle = 2
        )

        else -> emptyList()
    }

    private fun inferParallelMiddleGroup(
        readAddresses: List<KnxGroupAddress>,
        expectedMain: Int,
        readMiddle: Int,
        writeMiddle: Int
    ): List<KnxGroupAddress> = readAddresses.mapNotNull { readAddress ->
        if (readAddress.main == expectedMain && readAddress.middle == readMiddle) {
            KnxGroupAddress.of(
                main = expectedMain,
                middle = writeMiddle,
                sub = readAddress.sub
            )
        } else {
            null
        }
    }.distinct()

    /**
     * En el esquema de persianas, la dirección de estado de altura mantiene el
     * mismo subgrupo que la escritura de posición. Los dos subgrupos anteriores
     * corresponden a movimiento Up/Down y Stop, respectivamente.
     *
     * Ejemplo: estado 2/2/9 -> movimiento 2/1/7, stop 2/1/8, posición 2/1/9.
     */
    private fun inferBlindWriteAddresses(
        readAddresses: List<KnxGroupAddress>
    ): List<KnxGroupAddress> {
        val positionState = readAddresses.firstOrNull { address ->
            address.main == 2 && address.middle == 2 && address.sub >= 3
        } ?: return emptyList()

        return listOf(
            KnxGroupAddress.of(2, 1, positionState.sub - 2),
            KnxGroupAddress.of(2, 1, positionState.sub - 1),
            KnxGroupAddress.of(2, 1, positionState.sub)
        )
    }

    private fun controlKindFor(source: AppKnxObject, hasWriteAddress: Boolean): ControlKind {
        if (!hasWriteAddress && source.readAddresses.isNotEmpty()) return when (source.category) {
            AppKnxCategory.TEMPERATURE -> ControlKind.TEMPERATURE
            AppKnxCategory.METER -> ControlKind.METER
            AppKnxCategory.ALARM -> ControlKind.ALARM
            AppKnxCategory.SENSOR -> ControlKind.SENSOR
            else -> ControlKind.READ_ONLY
        }

        return when (source.category) {
            AppKnxCategory.LIGHT,
            AppKnxCategory.SWITCH -> ControlKind.BOOLEAN_SWITCH
            AppKnxCategory.BLIND -> ControlKind.BLIND
            AppKnxCategory.CLIMATE -> ControlKind.CLIMATE
            AppKnxCategory.TEMPERATURE -> ControlKind.TEMPERATURE
            AppKnxCategory.SCENE -> ControlKind.SCENE
            AppKnxCategory.ALARM -> ControlKind.ALARM
            AppKnxCategory.SENSOR -> ControlKind.SENSOR
            AppKnxCategory.METER -> ControlKind.METER
            AppKnxCategory.UNKNOWN -> ControlKind.UNKNOWN
        }
    }
}

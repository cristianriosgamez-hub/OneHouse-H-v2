package com.onehouse.app.knx

/**
 * Proyecto KNX inicial para OneHouse v2.0.0.
 *
 * Los 19 controles (15 luces + 4 persianas) se han reconstruido de la
 * configuración real previamente verificada para esta vivienda. Las direcciones
 * globales de clima, sensores, alarmas y ACS siguen en [KnxAddressBook].
 *
 * Sin MAC del interfaz, credenciales, IP ni claves de ETS. El usuario puede
 * editar los objetos y el repositorio solo siembra estos valores cuando no
 * existe un proyecto KNX previamente almacenado.
 */
object OneHouseFactoryKnxProject {
    fun create(): AppKnxProject = AppKnxProject(
        projectName = "OneHouse",
        builderVersion = "2.0.10",
        minimumAppVersion = "2.0.2",
        interfaceMacAddress = "",
        rooms = listOf(
        AppKnxRoom(
            name = "Entrada", iconName = "homelock",
            devices = listOf(
                AppKnxObject(
                    roomName = "Entrada", name = "Luz 1", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/7"),
                    writeAddresses = listOf("1/1/7"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Comedor", iconName = "lounge",
            devices = listOf(
                AppKnxObject(
                    roomName = "Comedor", name = "Salón", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/1"),
                    writeAddresses = listOf("1/1/1"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Comedor", name = "Comedor", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/2"),
                    writeAddresses = listOf("1/1/2"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Comedor", name = "Lampara", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/4"),
                    writeAddresses = listOf("1/1/4"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Comedor", name = "Persiana", sourceType = 6,
                    category = AppKnxCategory.BLIND, isFavourite = false,
                    readAddresses = listOf("2/2/3"),
                    writeAddresses = listOf("2/1/1", "2/1/2"),
                    dataPointType = "1.008 / 1.007", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Cocina", iconName = "kitchen3",
            devices = listOf(
                AppKnxObject(
                    roomName = "Cocina", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/6"),
                    writeAddresses = listOf("1/1/6"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Cocina", name = "Fluorescente", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/5"),
                    writeAddresses = listOf("1/1/5"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Cocina", name = "Persiana", sourceType = 6,
                    category = AppKnxCategory.BLIND, isFavourite = false,
                    readAddresses = listOf("2/2/6"),
                    writeAddresses = listOf("2/1/4", "2/1/5"),
                    dataPointType = "1.008 / 1.007", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Suite", iconName = "bedroom2",
            devices = listOf(
                AppKnxObject(
                    roomName = "Suite", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/12"),
                    writeAddresses = listOf("1/1/12"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Suite", name = "Mesita 1", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/14"),
                    writeAddresses = listOf("1/1/14"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Suite", name = "Mesita 2", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/13"),
                    writeAddresses = listOf("1/1/13"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Suite", name = "Persiana", sourceType = 6,
                    category = AppKnxCategory.BLIND, isFavourite = false,
                    readAddresses = listOf("2/2/12"),
                    writeAddresses = listOf("2/1/10", "2/1/11"),
                    dataPointType = "1.008 / 1.007", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Habitación 1", iconName = "inhome",
            devices = listOf(
                AppKnxObject(
                    roomName = "Habitación 1", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/10"),
                    writeAddresses = listOf("1/1/10"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Habitación 1", name = "Mesita", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/11"),
                    writeAddresses = listOf("1/1/11"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                ),
                AppKnxObject(
                    roomName = "Habitación 1", name = "Persiana", sourceType = 6,
                    category = AppKnxCategory.BLIND, isFavourite = false,
                    readAddresses = listOf("2/2/9"),
                    writeAddresses = listOf("2/1/7", "2/1/8"),
                    dataPointType = "1.008 / 1.007", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Baño", iconName = "bathroom2",
            devices = listOf(
                AppKnxObject(
                    roomName = "Baño", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/15"),
                    writeAddresses = listOf("1/1/15"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Pasillo", iconName = "home4",
            devices = listOf(
                AppKnxObject(
                    roomName = "Pasillo", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/9"),
                    writeAddresses = listOf("1/1/9"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Trastero", iconName = "dressing",
            devices = listOf(
                AppKnxObject(
                    roomName = "Trastero", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/8"),
                    writeAddresses = listOf("1/1/8"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        ),
        AppKnxRoom(
            name = "Terraza", iconName = "garden2",
            devices = listOf(
                AppKnxObject(
                    roomName = "Terraza", name = "Luz", sourceType = 1,
                    category = AppKnxCategory.LIGHT, isFavourite = false,
                    readAddresses = listOf("1/2/3"),
                    writeAddresses = listOf("1/1/3"),
                    dataPointType = "1.001", unit = null,
                    values = emptyList(), iconNames = emptyList()
                )
            )
        )
        )
    )
}

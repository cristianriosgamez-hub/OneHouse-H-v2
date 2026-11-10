package com.onehouse.app.knx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Previene regresiones del estado inicial antes de restaurar ningún backup. */
class OneHouseFactoryKnxProjectTest {
    @Test fun `instalacion limpia incorpora las nueve estancias y los diecinueve controles`() {
        val factory = OneHouseFactoryKnxProject.create()
        assertEquals(
            listOf("Entrada", "Comedor", "Cocina", "Suite", "Habitación 1", "Baño", "Pasillo", "Trastero", "Terraza"),
            factory.rooms.map { it.name }
        )
        assertEquals(15, factory.devices.count { it.category == AppKnxCategory.LIGHT })
        assertEquals(4, factory.devices.count { it.category == AppKnxCategory.BLIND })
        assertEquals(factory, OneHouseKnxUsagePolicy.prune(factory))
        assertTrue(factory.interfaceMacAddress.isEmpty())
    }

    @Test fun `las treinta y siete GAs de lectura son distintas sin comandos de grupo`() {
        val project = OneHouseFactoryKnxProject.create()
        val devices = KnxDeviceFactory.create(project)
        assertEquals(19, devices.count { it.canRead })
        val reads = devices.flatMap { device -> device.readAddresses.map { it.toString() } }
        assertEquals(19, reads.distinct().size)
        val globals = listOf(
            "5/3/2", "5/3/3", "5/3/4", "5/3/5", "5/2/6", "5/2/6",
            "5/1/1", "5/1/2", "5/1/3", "5/2/3", "5/5/1", "5/4/1",
            "5/4/1", "5/4/2", "15/0/11", "15/0/13", "15/0/14",
            "15/0/15", "15/5/67", KnxStartupReadPolicy.VALVE_STATE
        )
        assertEquals(18, globals.distinct().size)
        val tracked = KnxStartupReadPolicy.readable(globals + reads)
        assertEquals(37, tracked.size)
        assertTrue(tracked.contains("15/0/15"))
        assertTrue(tracked.contains("5/3/3"))
        assertTrue(tracked.contains("5/2/3"))
        assertFalse(tracked.contains("15/0/21"))
        assertFalse(tracked.any { it in KnxStartupReadPolicy.commandOnly })
        assertTrue(tracked.all(AppKnxConfigurationRepository::isValidGroupAddress))
    }

    @Test fun `cada control incorpora mando y lectura previstos en la configuracion validada`() {
        val project = OneHouseFactoryKnxProject.create()
        val byRoom = project.rooms.associate { room -> room.name to room.devices }
        assertEquals(listOf("1/2/7"), byRoom.getValue("Entrada").single().readAddresses)
        assertEquals(listOf("1/1/7"), byRoom.getValue("Entrada").single().writeAddresses)
        assertEquals(listOf("1/2/3"), byRoom.getValue("Terraza").single().readAddresses)
        assertEquals(listOf("1/1/3"), byRoom.getValue("Terraza").single().writeAddresses)

        val expectedBlinds = mapOf(
            "Comedor" to ("2/2/3" to listOf("2/1/1", "2/1/2", "2/1/3")),
            "Cocina" to ("2/2/6" to listOf("2/1/4", "2/1/5", "2/1/6")),
            "Habitación 1" to ("2/2/9" to listOf("2/1/7", "2/1/8", "2/1/9")),
            "Suite" to ("2/2/12" to listOf("2/1/10", "2/1/11", "2/1/12"))
        )
        expectedBlinds.forEach { (room, expected) ->
            val blind = byRoom.getValue(room).single { it.category == AppKnxCategory.BLIND }
            val resolved = KnxDeviceFactory.create(0, blind)
            assertEquals(listOf(expected.first), blind.readAddresses)
            assertEquals(expected.second, resolved.writeAddresses.map { it.toString() })
        }
    }
}

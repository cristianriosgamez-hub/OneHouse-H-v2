package com.onehouse.app.knx

import com.onehouse.app.knx.AppKnxCategory
import com.onehouse.app.knx.AppKnxObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auditoría de regresión KNX.
 *
 * Estas pruebas protegen las direcciones, DPT y comandos que ya han sido
 * validados en la instalación Schneider real. No modifican la lógica de
 * producción y sirven para detectar cambios accidentales en futuras entregas.
 */
class KnxAuditTest {

    @Test
    fun `catalogo global mantiene direcciones y DPT validados`() {
        val expected = mapOf(
            "climate_power_command" to ("5/2/2" to "1.001"),
            "climate_power" to ("5/3/2" to "1.001"),
            "climate_target_command" to ("5/2/1" to "9.001"),
            "climate_target_primary" to ("5/2/6" to "9.001"),
            "climate_current_temperature" to ("5/3/3" to "9.001"),
            "climate_mode_command" to ("5/2/4" to "20.105"),
            "climate_mode" to ("5/3/4" to "20.105"),
            "climate_fan_command" to ("5/2/5" to "5.001"),
            "climate_fan" to ("5/3/5" to "5.001"),
            "dining_co2" to ("5/1/1" to "9.008"),
            "entrance_pir_block" to ("5/5/1" to "1.001"),
            "kitchen_flood" to ("5/4/1" to "1.005"),
            "bathroom_flood" to ("5/4/1" to "1.005"),
            "terrace_luminosity" to ("15/0/11" to "9.004"),
            "terrace_wind_speed" to ("15/0/14" to "9.005"),
            "terrace_raining" to ("15/0/15" to "1.005"),
            "hallway_fire" to ("5/4/2" to "1.005")
        )

        val actual = KnxAddressBook.entries.associate { entry ->
            entry.key to (entry.defaultAddress to entry.dpt)
        }

        expected.forEach { (key, value) ->
            assertEquals("Objeto KNX alterado: $key", value, actual[key])
        }
    }

    @Test
    fun `catalogo global no contiene direcciones invalidas ni claves duplicadas`() {
        assertEquals(
            KnxAddressBook.entries.size,
            KnxAddressBook.entries.map { it.key }.distinct().size
        )
        KnxAddressBook.entries.forEach { entry ->
            assertTrue(
                "Dirección inválida en ${entry.key}: ${entry.defaultAddress}",
                AppKnxConfigurationRepository.isValidGroupAddress(entry.defaultAddress)
            )
            assertTrue(
                "DPT desconocido en ${entry.key}: ${entry.dpt}",
                KnxDptResolver.isKnown(entry.dpt)
            )
        }
    }

    @Test
    fun `entrada PIR e inundacion compartida conservan configuracion validada`() {
        val entries = KnxAddressBook.entries.associateBy { it.key }

        assertEquals("5/5/1", entries.getValue("entrance_pir_block").defaultAddress)
        assertEquals("Lectura y mando", entries.getValue("entrance_pir_block").kind)
        assertEquals("1.001", entries.getValue("entrance_pir_block").dpt)

        assertEquals(
            entries.getValue("kitchen_flood").defaultAddress,
            entries.getValue("bathroom_flood").defaultAddress
        )
        assertEquals("5/4/1", entries.getValue("kitchen_flood").defaultAddress)
    }

    @Test
    fun `persianas reconstruyen movimiento stop y posicion correctamente`() {
        val cases = listOf(
            Triple("Comedor", "2/2/3", listOf("2/1/1", "2/1/2", "2/1/3")),
            Triple("Cocina", "2/2/6", listOf("2/1/4", "2/1/5", "2/1/6")),
            Triple("Habitación 1", "2/2/9", listOf("2/1/7", "2/1/8", "2/1/9")),
            Triple("Suite", "2/2/12", listOf("2/1/10", "2/1/11", "2/1/12"))
        )

        cases.forEachIndexed { index, (room, stateAddress, expectedWrites) ->
            val source = AppKnxObject(
                roomName = room,
                name = "Persiana",
                sourceType = 0,
                category = AppKnxCategory.BLIND,
                isFavourite = false,
                readAddresses = listOf(stateAddress),
                writeAddresses = emptyList(),
                dataPointType = "5.001",
                unit = "%",
                values = emptyList(),
                iconNames = emptyList()
            )

            val device = KnxDeviceFactory.create(index, source)
            assertEquals(expectedWrites, device.writeAddresses.map { it.toString() })

            val commands = device.commands.associateBy { it.type }
            assertEquals(expectedWrites[0], commands[KnxCommandType.UP]?.destination.toString())
            assertEquals(expectedWrites[0], commands[KnxCommandType.DOWN]?.destination.toString())
            assertEquals(expectedWrites[1], commands[KnxCommandType.STOP]?.destination.toString())
            assertEquals(expectedWrites[2], commands[KnxCommandType.POSITION]?.destination.toString())
            assertEquals("1.007", commands[KnxCommandType.STOP]?.dpt)
            assertEquals("5.001", commands[KnxCommandType.POSITION]?.dpt)
        }
    }

    @Test
    fun `normalizacion DPT conserva formatos utilizados por OneHouse`() {
        assertEquals("1.001", KnxDptResolver.normalize("DPT 1.1"))
        assertEquals("9.001", KnxDptResolver.normalize("9,001"))
        assertEquals("20.105", KnxDptResolver.normalize("DPT20.105"))
        assertEquals("9.008", KnxDptResolver.normalize("DPT 9.008 CO2"))
        assertTrue(KnxDptResolver.isBoolean("1.005"))
        assertFalse(KnxDptResolver.isBoolean("5.001"))
    }

    @Test
    fun `valores de ventilador Schneider permanecen dentro de rango`() {
        assertEquals(25, KnxAddressBook.Climate.FAN_SPEED_LOW_VALUE)
        assertEquals(37, KnxAddressBook.Climate.FAN_SPEED_MEDIUM_VALUE)
        assertEquals(100, KnxAddressBook.Climate.FAN_SPEED_HIGH_VALUE)
        assertTrue(KnxAddressBook.Climate.FAN_SPEED_LOW_VALUE in 0..100)
        assertTrue(KnxAddressBook.Climate.FAN_SPEED_MEDIUM_VALUE in 0..100)
        assertTrue(KnxAddressBook.Climate.FAN_SPEED_HIGH_VALUE in 0..100)
    }
}

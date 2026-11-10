package com.onehouse.app.feature.maintenance

import com.onehouse.app.knx.KnxCommandType
import org.junit.Assert.*
import org.junit.Test

class MaintenanceActionsTest {
    @Test fun enabledActionsUseEtsReceiverPolarity() {
        val lights = MaintenanceAddresses.lightsOffPlan().commands.single().command
        val general = MaintenanceAddresses.generalOffPlan().commands.single().command
        val blinds = MaintenanceAddresses.closeBlindsPlan().commands.single().command
        assertEquals("1/1/50", lights.destination.toString())
        assertEquals(KnxCommandType.OFF, lights.type)
        assertEquals("5/5/14", general.destination.toString())
        assertEquals(KnxCommandType.OFF, general.type)
        assertEquals("2/1/50", blinds.destination.toString())
        assertEquals(KnxCommandType.ON, blinds.type)
    }
    @Test fun sceneUsesDistinctAddressesAndBitValuesFromLegacyConfiguration() {
        val one = MaintenanceAddresses.generalOff.plan(true).commands.single().command
        val zero = MaintenanceAddresses.generalOff.plan(false).commands.single().command
        assertEquals("5/5/3", one.destination.toString())
        assertEquals(KnxCommandType.ON, one.type)
        assertEquals("5/5/14", zero.destination.toString())
        assertEquals(KnxCommandType.OFF, zero.type)
        assertEquals("1.001", one.dpt)
        assertEquals("2/1/50", MaintenanceAddresses.closeBlinds.plan(true).commands.single().command.destination.toString())
        assertEquals("2/1/53", MaintenanceAddresses.closeBlinds.plan(false).commands.single().command.destination.toString())
        for (value in listOf(true, false)) {
            assertEquals("1/1/50", MaintenanceAddresses.lightsOff.plan(value).commands.single().command.destination.toString())
        }
    }

    @Test fun unverifiedActivationCannotSendAnyCommand() {
        for (scene in listOf(MaintenanceAddresses.lightsOff, MaintenanceAddresses.generalOff, MaintenanceAddresses.closeBlinds)) {
            assertNotNull(scene.plan(null).unavailable)
            assertTrue(scene.plan(null).commands.isEmpty())
        }
    }

    @Test fun climateOffUsesConfiguredCommandAndSeparateFeedback() {
        val item = booleanMaintenancePlan("5/2/2", false, "5/3/2").commands.single()
        assertEquals("5/2/2", item.command.destination.toString())
        assertEquals(KnxCommandType.OFF, item.command.type)
        assertEquals("5/3/2", item.stateAddress)
        assertEquals("7/1/2", booleanMaintenancePlan("7/1/2", false, "7/2/2").commands.single().command.destination.toString())
        assertNotNull(booleanMaintenancePlan("invalid", false).unavailable)
    }

    @Test fun valveUsesProvisionalZeroClosedOneOpenPolarity() {
        val open = booleanMaintenancePlan(MaintenanceAddresses.VALVE_COMMAND, true, MaintenanceAddresses.VALVE_STATE).commands.single()
        val closed = booleanMaintenancePlan(MaintenanceAddresses.VALVE_COMMAND, false, MaintenanceAddresses.VALVE_STATE).commands.single()
        assertEquals("2/3/1", open.command.destination.toString())
        assertEquals(KnxCommandType.ON, open.command.type)
        assertEquals("2/4/1", open.stateAddress)
        assertEquals(KnxCommandType.OFF, closed.command.type)
    }
}

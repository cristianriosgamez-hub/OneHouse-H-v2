package com.onehouse.app.knx

import org.junit.Assert.*
import org.junit.Test

class H095LightMappingTest {
    @Test fun eachPilotStateUpdatesOnlyItsOwnFactoryDevice() {
        val devices = KnxDeviceFactory.create(OneHouseFactoryKnxProject.create())
        val expected = mapOf("1/2/2" to "Comedor", "1/2/5" to "Fluorescente", "1/2/6" to "Luz")
        for ((ga, name) in expected) {
            val target = devices.single { it.readAddresses.any { address -> address.toString() == ga } }
            assertEquals(name,target.name)
            if (ga == "1/2/2") {
                assertEquals("Comedor",target.roomName)
                assertEquals(listOf("1/1/2"),target.writeAddresses.map { it.toString() })
            }
            for (on in listOf(true,false)) {
                val state = KnxStateRepository.State(ga,on,if(on) "01" else "00","3.15.1",
                    KnxConnectionManager.IncomingGroupTelegram.Kind.WRITE,"GroupValueWrite",Long.MAX_VALUE) // Trusted fixture without starting Android process session.
                val values = mapOf(ga to state)
                assertEquals(on,KnxLightStateResolver.confirmedBoolean(target,values))
                devices.filter { it.id != target.id }.forEach {
                    assertNull("$ga must not update ${it.name}",KnxLightStateResolver.confirmedBoolean(it,values))
                }
            }
        }
    }
}

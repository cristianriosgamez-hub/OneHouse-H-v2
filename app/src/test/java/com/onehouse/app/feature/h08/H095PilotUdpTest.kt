package com.onehouse.app.feature.h08

import com.onehouse.knxlab.knx.H091PassiveMonitorRunner
import com.onehouse.knxlab.knx.KnxProtocol
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class H095PilotUdpTest {
    @Test(timeout = 105_000) fun threeLightsKeepSingleNinetySecondSession() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { gateway ->
            gateway.soTimeout = 35_000
            val result = ArrayBlockingQueue<H091PassiveMonitorRunner.Snapshot>(1)
            val applied = java.util.Collections.synchronizedList(mutableListOf<String>())
            val runner = H091PassiveMonitorRunner()
            val start = System.nanoTime()
            assertTrue(runner.start("127.0.0.1", gateway.localPort, {}, { null }, {},
                { result.add(it) }, { applied.add(it.address + "=" + it.value); true }))
            fun receive(): DatagramPacket {
                val p = DatagramPacket(ByteArray(512), 512)
                gateway.receive(p)
                return p
            }
            try {
                val connect = receive()
                assertEquals(0x0205, KnxProtocol.service(connect.data, connect.length))
                fun send(bytes: ByteArray) = gateway.send(DatagramPacket(bytes, bytes.size, connect.socketAddress))
                val port = gateway.localPort
                send(byteArrayOf(6,16,2,6,0,20,37,0,8,1,127,0,0,1,
                    (port ushr 8).toByte(),port.toByte(),4,4,17,17))
                fun event(seq: Int, ga: Int, apdu: Int, tpci: Int = 0) {
                    send(byteArrayOf(6,16,4,32,0,21,4,37,seq.toByte(),0,
                        41,0,188.toByte(),224.toByte(),63,1,10,ga.toByte(),1,tpci.toByte(),apdu.toByte()))
                    val ack = receive()
                    assertArrayEquals(byteArrayOf(6,16,4,33,0,10,4,37,seq.toByte(),0), ack.data.copyOf(ack.length))
                }
                event(0,2,129) // ON
                event(1,2,128) // OFF
                event(2,2,130) // Two-bit value is invalid.
                event(3,2,64)  // Response is not a Write.
                event(4,2,129,64) // Non-zero TPCI.
                event(5,5,129) // Kitchen still applies.
                event(6,6,128)
                event(6,6,128) // Duplicate ACK, no second application.
                event(7,6,129)
                event(8,5,128)
                event(9,4,129) // Unauthorised dining lamp.
                var keepalives = 0
                while (true) {
                    val p = receive()
                    when (KnxProtocol.service(p.data,p.length)) {
                        0x0207 -> { keepalives++; send(byteArrayOf(6,16,2,8,0,8,37,0)) }
                        0x0209 -> { send(byteArrayOf(6,16,2,10,0,8,37,0)); break }
                        else -> fail("Unexpected request / extra CONNECT or active tunnelling")
                    }
                }
                val s = result.poll(3,TimeUnit.SECONDS)!!
                assertTrue((System.nanoTime()-start)/1_000_000 in 90_000..95_000)
                assertEquals(2,keepalives)
                assertTrue(s.disconnectedOk)
                assertEquals("LIMITE_90_SEGUNDOS",s.stopReason)
                assertEquals(0,s.outgoingTunnelRequests)
                assertEquals(listOf("1/2/2=true","1/2/2=false","1/2/5=true","1/2/6=false","1/2/6=true","1/2/5=false"),applied.toList())
                assertEquals(6,s.applied)
                assertEquals(6,s.acceptedWrites)
                val valid = s.events.filter { it.kind == "WRITE_ACCEPTED" && it.address == "1/2/2" }.map { it.toCsvRow() }
                assertEquals(2,valid.size)
                assertTrue(valid[0].contains(";1/2/2;type=WRITE,value=1,source=3.15.1,seq=0,format_DPT1_001=PASS"))
                assertTrue(valid[1].contains("value=0,source=3.15.1,seq=1"))
                assertEquals(2,s.events.count { it.kind == "IGNORED_NON_BOOLEAN" })
                assertEquals(setOf("1/2/2","1/2/5","1/2/6"),s.events.filter { it.kind == "CACHE_APPLIED" }.map { it.address }.toSet())

            } finally { runner.requestStop() }
        }
    }
}

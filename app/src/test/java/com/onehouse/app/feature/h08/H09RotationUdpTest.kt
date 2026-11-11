package com.onehouse.app.feature.h08

import com.onehouse.knxlab.knx.H091PassiveMonitorRunner
import com.onehouse.knxlab.knx.KnxProtocol
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Actual receiver + UDP gateway; lifecycle notifications are explicit test doubles. */
class H09RotationUdpTest {
    @Test fun rotationsKeepOneTunnelAndEventsWhileManualStopClosesOnce() {
        val loopback = InetAddress.getByName("127.0.0.1")
        DatagramSocket(0, loopback).use { gateway ->
            gateway.soTimeout = 5_000
            val runner = H091PassiveMonitorRunner(keepaliveMs = 1_000)
            val connected = CountDownLatch(1)
            val completed = ArrayBlockingQueue<H091PassiveMonitorRunner.Snapshot>(1)
            val samples = ArrayBlockingQueue<H091PassiveMonitorRunner.Sample>(8)
            val connects = AtomicInteger()
            val disconnects = AtomicInteger()
            val observedStarts = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
            val timers = mutableListOf<() -> Unit>()
            val lifecycle = H09ActivityLifecycle(runner::requestStop) { _, action ->
                timers.add(action)
                val cancel: () -> Unit = { timers.remove(action) }
                cancel
            }
            var token = Any()
            lifecycle.started(token)
            check(runner.start("127.0.0.1", gateway.localPort, {}, { null }, {
                observedStarts.add(it.startedEpochMs)
                if (it.connected) connected.countDown()
            }, { completed.add(it) }, { samples.add(it); true }))

            fun receive(service: Int): Pair<ByteArray, InetSocketAddress> {
                val p = DatagramPacket(ByteArray(512), 512)
                gateway.receive(p)
                val bytes = p.data.copyOf(p.length)
                val actual = KnxProtocol.service(bytes, bytes.size)
                if (actual == 0x0205) connects.incrementAndGet()
                if (actual == 0x0209) disconnects.incrementAndGet()
                assertEquals(service, actual)
                return bytes to (p.socketAddress as InetSocketAddress)
            }
            val (_, client) = receive(0x0205)
            fun send(bytes: ByteArray) = gateway.send(DatagramPacket(bytes, bytes.size, client))
            val hpai = byteArrayOf(8,1,127,0,0,1,(gateway.localPort ushr 8).toByte(),gateway.localPort.toByte())
            send(byteArrayOf(6,16,2,6,0,20,37,0) + hpai + byteArrayOf(4,4,17,17))
            assertTrue(connected.await(2, TimeUnit.SECONDS))
            try {
                repeat(3) { sequence ->
                    lifecycle.stopped(token, changingConfigurations = true)
                    // Both kitchen GAs and state delivery during the recreation gap.
                    send(byteArrayOf(6,16,4,32,0,21,4,37,sequence.toByte(),0,
                        41,0,188.toByte(),224.toByte(),17,17,10,(5+sequence%2).toByte(),1,0,
                        (128+sequence%2).toByte()))
                    val (ack, _) = receive(0x0421)
                    assertArrayEquals(byteArrayOf(6,16,4,33,0,10,4,37,sequence.toByte(),0), ack)
                    assertNotNull(samples.poll(1, TimeUnit.SECONDS))
                    token = Any()
                    lifecycle.started(token)
                    assertTrue(runner.isRunning())
                }
                receive(0x0207)
                lifecycle.stopped(token, true)
                send(byteArrayOf(6,16,2,8,0,8,37,0))
                token = Any()
                lifecycle.started(token)
                runner.requestStop() // Same barrier called by the manual button.
                receive(0x0209)
                send(byteArrayOf(6,16,2,10,0,8,37,0))
                val result = completed.poll(2, TimeUnit.SECONDS)
                assertNotNull(result)
                assertEquals(1, connects.get())
                assertEquals(1, disconnects.get())
                assertEquals(1, observedStarts.size)
                assertEquals(3, result!!.applied)
                assertTrue(result.disconnectedOk)
                assertEquals(0, result.outgoingTunnelRequests)
                assertEquals(0, result.unexpectedFrames)
                assertTrue(timers.isEmpty())
                lifecycle.started(Any())
                assertFalse(runner.isRunning())
            } finally {
                runner.requestStop()
            }
        }
    }
}

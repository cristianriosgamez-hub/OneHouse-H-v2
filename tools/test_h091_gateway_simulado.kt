package com.onehouse.knxlab.knx

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
    server.soTimeout = 6000
    val completed = CountDownLatch(1)
    val wrote = CountDownLatch(2)
    val badOutgoing = AtomicInteger()
    var status: H091PassiveMonitorRunner.Snapshot? = null
    val serverThread = Thread {
        val packet = DatagramPacket(ByteArray(512),512)
        var remote: java.net.SocketAddress? = null
        var sent = false
        val channel=37
        while (true) {
            try {
                packet.length=512
                server.receive(packet)
                remote = packet.socketAddress
                val bytes=packet.data.copyOf(packet.length)
                val st=KnxProtocol.service(bytes,bytes.size)
                if (st==0x0205) {
                    val response=byteArrayOf(6,0x10,2,6,0,8,channel.toByte(),0)
                    server.send(DatagramPacket(response,response.size,remote))
                    // send 2 writes, then duplicate sequence, then irrelevant GA
                    Thread.sleep(80)
                    fun write(seq:Int,ga:String,value:Int) {
                        val raw=KnxProtocol.gaToRaw(ga)
                        val cemi = byteArrayOf(0x29,0,0xBC.toByte(),0xE0.toByte(),0x11,0x11,
                            (raw ushr 8).toByte(),raw.toByte(),1,0,(0x80 or value).toByte())
                        val len = 10+cemi.size
                        val request=byteArrayOf(6,0x10,4,0x20, (len ushr 8).toByte(),len.toByte(),4,channel.toByte(),seq.toByte(),0) + cemi
                        server.send(DatagramPacket(request,request.size,remote))
                    }
                    write(10,"1/2/6",1)
                    Thread.sleep(60)
                    write(11,"1/2/5",0)
                    Thread.sleep(60)
                    write(11,"1/2/5",0) // must be deduped
                    Thread.sleep(60)
                    write(12,"1/2/10",1) // ignored
                    sent=true
                } else if (st==0x0209) {
                    val response=byteArrayOf(6,0x10,2,0x0A,0,8,channel.toByte(),0)
                    server.send(DatagramPacket(response,response.size,remote))
                    break
                } else if (st==0x0420) {
                    badOutgoing.incrementAndGet()
                }
            } catch (e: Exception) { throw RuntimeException("Gateway simulator error: ${e.message}",e) }
        }
        check(sent)
    }
    serverThread.start()
    val receiver=H091PassiveMonitorRunner()
    check(receiver.start("127.0.0.1",server.localPort, { }, { null }, { },
        { s -> status=s; completed.countDown() },
        { wrote.countDown() }))
    check(wrote.await(3, TimeUnit.SECONDS)) { "No recibió 2 escritos" }
    receiver.requestStop()
    check(completed.await(5,TimeUnit.SECONDS)) { "No terminó" }
    serverThread.join(3000)
    server.close()
    val s=status ?: error("sin snapshot")
    println("H09.1 SIM: events=${s.acceptedWrites}, duplicate=${s.duplicateFrames}, disconnected=${s.disconnectedOk}, outgoingREAD=${badOutgoing.get()}, values=${s.values}, reason=${s.stopReason}")
    check(s.acceptedWrites==2)
    check(s.duplicateFrames==1)
    check(s.disconnectedOk)
    check(badOutgoing.get()==0)
    check(s.values["1/2/6"]?.value==true)
    check(s.values["1/2/5"]?.value==false)
    println("PASS -- Gateway UDP simulado, datos 2/2, sin GVR ni GWV, cierre confirmado")
}

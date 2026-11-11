package com.onehouse.knxlab.knx

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val CHANNEL = 37
private val loopback = InetAddress.getByName("127.0.0.1")
private fun socket() = DatagramSocket(0, loopback).apply { soTimeout = 6000 }
private fun control(service: Int, length: Int = 8) =
    byteArrayOf(6, 16, 2, service.toByte(), 0, length.toByte(), CHANNEL.toByte(), 0)

private fun write(seq: Int, ga: Int = 6, value: Int = 1): ByteArray =
    byteArrayOf(6,16,4,32,0,21,4,CHANNEL.toByte(),seq.toByte(),0,41,0,188.toByte(),224.toByte(),
        17,17,10,ga.toByte(),1,0,(128 + value).toByte())

private class Gateway(
    interval: Long = 30_000, timeout: Long = 4_000, recoveryMs: Long = 4_000,
    route: () -> String? = { null },
    apply: (H091PassiveMonitorRunner.Sample) -> Boolean = { true }
) : AutoCloseable {
    val ctl = socket()
    val data = socket()
    val runner = H091PassiveMonitorRunner(interval, timeout, recoveryMs)
    private val finished = ArrayBlockingQueue<H091PassiveMonitorRunner.Snapshot>(1)
    private val connected = CountDownLatch(1)
    lateinit var client: InetSocketAddress
    private val hpai get() = byteArrayOf(8,1,127,0,0,1,(data.localPort ushr 8).toByte(),data.localPort.toByte())

    init {
        check(runner.start("127.0.0.1", ctl.localPort, {}, route,
            { if (it.connected) connected.countDown() }, { finished.add(it) }, apply))
        receive(ctl, 0x0205)
    }
    fun receive(socket: DatagramSocket, service: Int): ByteArray {
        val p = DatagramPacket(ByteArray(512), 512)
        socket.receive(p)
        if (!::client.isInitialized) client = p.socketAddress as InetSocketAddress
        check(p.socketAddress == client)
        val b = p.data.copyOf(p.length)
        check(KnxProtocol.service(b,b.size) == service) { "Servicio inesperado: ${KnxProtocol.hex(b)}" }
        return b
    }
    fun send(s: DatagramSocket, b: ByteArray) = s.send(DatagramPacket(b,b.size,client))
    fun connect(wait: Boolean = true) {
        send(ctl, control(6,20) + hpai + byteArrayOf(4,4,17,17))
        if (wait) check(connected.await(2,TimeUnit.SECONDS))
    }
    fun ack(seq: Int, status: Int = 0) {
        val b = receive(data,0x0421)
        check(b.contentEquals(byteArrayOf(6,16,4,33,0,10,4,CHANNEL.toByte(),seq.toByte(),status.toByte())))
    }
    fun primeTo(first: Int) {
        repeat(first) { seq -> send(data, write(seq, ga=10)); ack(seq) }
    }
    fun noAck() {
        val previous = data.soTimeout
        data.soTimeout = 120
        try {
            val p = DatagramPacket(ByteArray(512), 512)
            try { data.receive(p); error("ACK inesperado: ${KnxProtocol.hex(p.data.copyOf(p.length))}") }
            catch (_: java.net.SocketTimeoutException) { }
        } finally { data.soTimeout = previous }
    }
    fun finish() = checkNotNull(finished.poll(3,TimeUnit.SECONDS)).also {
        check(it.outgoingTunnelRequests == 0)
    }
    fun closeSession(stop: Boolean = true, confirm: Boolean = true): H091PassiveMonitorRunner.Snapshot {
        if (stop) runner.requestStop()
        receive(ctl,0x0209)
        if (confirm) send(ctl,control(10))
        return finish()
    }
    override fun close() { runner.requestStop(); ctl.close(); data.close() }
}

private fun sequenceTests() {
    // Sequence starts at zero after CONNECT; 0 is non-pilot traffic before the requested 1.. cases.
    data class Case(val name: String, val seqs: List<Int>, val accepted: Set<Int>, val gaps: Int, val duplicates: Int)
    val cases = listOf(
        Case("correcta 1,2,3,4,5", listOf(1,2,3,4,5), setOf(1,2,3,4,5),0,0),
        Case("esperada tardia y retransmision", listOf(1,2,3,5,4,5,6), setOf(1,2,3,4,5,6),1,0),
        Case("duplicados inmediatos / ACK perdido", listOf(1,1,2,2,3), setOf(1,2,3),0,2),
        Case("atrasada", listOf(1,2,3,1,4), setOf(1,2,3,4),1,0),
        Case("wrap", listOf(254,255,0,1), setOf(254,255,0,1),0,0),
        Case("wrap duplicado", listOf(254,255,255,0,1), setOf(254,255,0,1),0,1)
    )
    for (c in cases) {
        val consumed = CountDownLatch(c.accepted.size)
        val samples = java.util.Collections.synchronizedList(mutableListOf<H091PassiveMonitorRunner.Sample>())
        Gateway(apply={ samples.add(it); consumed.countDown(); true }).use { g ->
            g.connect(); g.primeTo(c.seqs.first())
            var last = (c.seqs.first()-1) and 255
            var acked = c.seqs.first()
            var rejected = 0
            c.seqs.forEach { seq ->
                g.send(g.data, write(seq,5+seq%2,seq%2))
                if (seq == ((last+1) and 255) || seq == last) {
                    g.ack(seq); acked++; last=seq
                } else { g.noAck(); rejected++ }
            }
            check(consumed.await(1,TimeUnit.SECONDS))
            val s=g.closeSession()
            check(s.applied==c.accepted.size && s.acceptedWrites==c.accepted.size && samples.size==c.accepted.size)
            check(s.ackCount==acked && s.unexpectedFrames==c.gaps && s.duplicateFrames==c.duplicates)
            check(s.discarded==c.seqs.first()+rejected+c.duplicates && !s.freshReadRequired && s.disconnectedOk)
            check(samples.all { it.address in setOf("1/2/5","1/2/6") })
            println("PASS ${c.name}: ACK=${s.ackCount}, aceptados=${s.acceptedWrites}, aplicados=${s.applied}, descartados=${s.discarded}")
        }
    }
    // Persistent loss has an absolute deadline: later traffic/duplicates cannot extend it.
    for (stop in listOf(false,true)) {
      val consumed=CountDownLatch(3)
      Gateway(recoveryMs=if(stop) 650 else 4_000, apply={consumed.countDown();true}).use { g ->
        g.connect();g.primeTo(1)
        for(seq in 1..3){g.send(g.data,write(seq,5+seq%2,seq%2));g.ack(seq)}
        check(consumed.await(1,TimeUnit.SECONDS))
        for(seq in 5..7){g.send(g.data,write(seq));g.noAck()}
        val s=if(stop) {
            g.runner.requestStop();g.receive(g.ctl,0x0209)
            g.send(g.data,write(4));g.ack(4)
            g.send(g.ctl,control(10));g.finish()
        } else g.closeSession(stop=false)
        check(s.applied==3 && s.acceptedWrites==3 && s.values.size==2 && s.ackCount==if(stop) 5 else 4)
        check(s.freshReadRequired && s.disconnectedOk && s.events.any{it.kind=="SEQUENCE_RECOVERY_END"})
        if(!stop) check("SEQUENCE_RECOVERY_TIMEOUT" in s.stopReason)
        println("PASS ${if(stop) "STOP durante recuperacion" else "perdida 1,2,3,5,6,7 / DISCONNECT"}: ACK=${s.ackCount}, aplicados=3, descartados=${s.discarded}, nueva H obligatoria")
      }
    }
    Gateway(recoveryMs=650).use { g ->
        g.connect();g.send(g.data,write(0,ga=10));g.ack(0)
        val started=System.nanoTime()
        for(seq in 2..5) {
            g.send(g.data,write(seq));g.noAck()
            g.send(g.data,write(0,ga=10));g.ack(0)
        }
        val s=g.closeSession(false)
        val durationMs=(System.nanoTime()-started)/1_000_000
        check(durationMs in 600..1500 && s.ackCount==5 && s.applied==0 && s.freshReadRequired)
        check(s.sequenceRecoveryStarts==1 && s.sequenceRecoverySuccesses==0)
        println("PASS plazo absoluto no ampliado por saltos/duplicados: ${durationMs}ms")
    }
    Gateway().use { g ->
        g.connect()
        // Idle background traffic, including confirmation, response and retransmissions.
        val frames=listOf(write(0,ga=10),write(1).also{it[10]=46},write(1).also{it[10]=46},
            write(2).also{it[20]=64},write(2).also{it[20]=64},write(3,ga=10))
        frames.forEach { g.send(g.data,it);g.ack(it[8].toInt() and 255) }
        val s=g.closeSession()
        check(s.applied==0 && s.acceptedWrites==0 && s.ackCount==6 && s.duplicateFrames==2 && s.discarded==6)
        println("PASS reposo: GA ajena, confirmacion, Response y retransmisiones sin cache")
    }
    Gateway(interval=160, recoveryMs=900).use { g ->
        g.connect();g.send(g.data,write(0,ga=10));g.ack(0)
        g.send(g.data,write(2));g.noAck()
        g.receive(g.ctl,0x0207);g.send(g.ctl,control(8))
        g.send(g.data,write(1,ga=10));g.ack(1)
        val s=g.closeSession()
        check(s.events.any{it.kind=="KEEPALIVE_OK"} && s.events.any{it.kind=="SEQUENCE_RECOVERY_OK"})
        check(s.applied==0 && !s.freshReadRequired && s.ackCount==2)
        println("PASS KEEPALIVE durante anomalia / recuperacion sin aplicar el adelantado")
    }
    Gateway(recoveryMs=400).use { g ->
        g.connect();g.send(g.data,write(1));g.noAck()
        val s=g.closeSession(false)
        check(s.applied==0 && s.ackCount==0 && s.freshReadRequired)
        println("PASS secuencia inicial distinta de 0: sin ACK ni cache")
    }
    Gateway().use { g ->
        g.connect()
        g.send(g.ctl,write(0));g.noAck()
        g.send(g.data,write(0).also{it[7]=38});g.noAck()
        g.send(g.data,write(0).also{it[9]=1});g.noAck()
        g.send(g.data,KnxProtocol.tunnelAck(CHANNEL,0));g.noAck()
        g.send(g.data,write(0,ga=10));g.ack(0)
        g.runner.requestStop();g.receive(g.ctl,0x0209)
        g.send(g.data,write(2));g.noAck()
        g.send(g.data,write(1));g.ack(1)
        g.send(g.ctl,control(10))
        val s=g.finish()
        check(s.applied==0 && s.ackCount==2 && s.disconnectedOk && s.events.any{it.kind=="UNSOLICITED_TUNNEL_ACK"})
        println("PASS endpoint/canal/formato/ACK no solicitado y salto durante cierre")
    }
}

fun main() {
    sequenceTests()
    for ((seqs, statuses) in listOf(
        listOf(254,255,0,1) to listOf(0,0,0,0),
        listOf(254,255,255,0,1) to listOf(0,0,0,0,0),
        listOf(254,0,255,1,0,1) to listOf(0,4,0,4,0,0),
        listOf(10,11,10,12) to listOf(0,0,4,0))) {
        val expectedApplied = if (seqs.first()==10) 3 else 4
        val consumed = CountDownLatch(expectedApplied)
        Gateway(apply={consumed.countDown();true}).use { g ->
            g.connect(); g.primeTo(seqs.first())
            seqs.zip(statuses).forEach { (seq,status) -> g.send(g.data,write(seq)); if (status == 0) g.ack(seq) else g.noAck() }
            check(consumed.await(1,TimeUnit.SECONDS))
            val sentinel = (seqs.last()+1) and 255
            g.send(g.data,write(sentinel,ga=10));g.ack(sentinel)
            val s = g.closeSession()
            check(s.disconnectedOk && s.applied == (if (seqs.first()==10) 3 else 4))
            check(s.duplicateFrames == (if (seqs.size==5) 1 else 0))
        }
    }
    Gateway().use { g ->
        g.connect()
        val frames = listOf(write(0).also { it[10]=46 }, write(1).also { it[20]=64 },
            write(2).also { it[20]=(-126).toByte() },write(3).also { it[18]=2 },
            (write(4)+0.toByte()).also { it[5]=22; it[18]=2 },
            (write(5)+1.toByte()).also { it[5]=22; it[18]=2 },
            (write(6)+0.toByte()).also { it[5]=22 }, write(7,ga=10))
        frames.forEachIndexed { seq,b -> g.send(g.data,b); g.ack(seq) }
        // DISCONNECT is observed only after the last frame has completed filtering.
        val s=g.closeSession()
        check(s.applied==0 && s.discarded==frames.size)
    }
    val wifiDown = AtomicBoolean()
    Gateway(route={ if(wifiDown.get()) "wifi_perdida" else null }).use { g ->
        g.connect();wifiDown.set(true)
        val s=g.closeSession(false);check(s.firstError.startsWith("WIFI_ROUTE_LOST") && s.disconnectedOk)
    }
    Gateway().use { g ->
        g.connect()
        // Wrong peer and channel must never enter the sequence stream.
        g.send(g.ctl,write(91)); g.send(g.data,write(92).also{it[7]=38})
        g.runner.requestStop(); g.receive(g.ctl,0x0209)
        g.send(g.data,write(0));g.ack(0);g.send(g.ctl,control(10))
        val s=g.finish();check(s.applied==0 && s.disconnectedOk)
        g.send(g.data,write(1));check(!g.runner.isRunning())
    }
    val entered=CountDownLatch(1); val release=CountDownLatch(1); val armed=AtomicBoolean()
    Gateway(route={ if(armed.compareAndSet(true,false)){entered.countDown();check(release.await(2,TimeUnit.SECONDS))};null }).use { g ->
        g.connect();armed.set(true);check(entered.await(2,TimeUnit.SECONDS))
        g.runner.requestStop();g.send(g.data,write(0));release.countDown();g.ack(0)
        check(g.closeSession(false).applied==0)
    }
    Gateway().use { g ->
        g.runner.requestStop();g.connect(false)
        check(g.closeSession(false).disconnectedOk)
    }
    Gateway().use { g -> g.runner.requestStop();check(g.finish().recoveryRequired) }
    Gateway().use { g -> g.connect();check(g.closeSession(confirm=false).recoveryRequired) }
    Gateway(interval=50,timeout=100).use { g ->
        g.connect();g.receive(g.ctl,0x0207);g.send(g.ctl,control(8));g.receive(g.ctl,0x0207)
        val s=g.closeSession(false);check("KEEPALIVE" in s.stopReason && s.disconnectedOk)
    }
    Gateway().use { g ->
        g.connect();g.send(g.ctl,control(9,16)+byteArrayOf(8,1,127,0,0,1,0,1))
        g.receive(g.ctl,0x020a);check(g.finish().stopReason=="CIERRE_GATEWAY")
    }
    Gateway(apply={error("injected")}).use { g ->
        g.connect();g.primeTo(1);g.send(g.data,write(1));g.ack(1)
        val s=g.closeSession(false);check(s.applyErrors==1 && s.applied==0 && s.values.isEmpty())
    }
    val repoDuplicate = CountDownLatch(1)
    Gateway(apply={repoDuplicate.countDown();false}).use { g ->
        g.connect();g.primeTo(1);g.send(g.data,write(1));g.ack(1)
        check(repoDuplicate.await(1,TimeUnit.SECONDS))
        g.send(g.data,write(2,ga=10));g.ack(2)
        val s=g.closeSession();check(s.cacheDuplicates==1 && s.applied==0)
    }
    // Hold the repository while verifying that the transport continues acknowledging traffic.
    val blocked = CountDownLatch(1)
    val unblock = CountDownLatch(1)
    Gateway(apply={blocked.countDown();check(unblock.await(3,TimeUnit.SECONDS));true}).use { g ->
        g.connect();g.send(g.data,write(0));g.ack(0);check(blocked.await(1,TimeUnit.SECONDS))
        for(seq in 1..65){g.send(g.data,write(seq));g.ack(seq)}
        g.receive(g.ctl,0x0209);g.send(g.ctl,control(10));unblock.countDown()
        val s=g.finish();check("Cola" in s.stopReason && s.applied==1 && s.discarded==65)
    }
    val consumed=CountDownLatch(340)
    Gateway(apply={consumed.countDown();true}).use { g ->
        g.connect()
        val start=System.nanoTime()
        repeat(34){ burst ->
            repeat(10){val i=burst*10+it; g.send(g.data,write(i,5+i%2,i%2))}
            repeat(10){g.ack((burst*10+it) and 255)}
        }
        check(consumed.await(1,TimeUnit.SECONDS))
        val s=g.closeSession()
        check(s.applied==340 && s.ackCount==340 && s.lostRecords>0 && s.events.size==320 &&
            s.connectResult.startsWith("CONNECT_OK") && s.disconnectedOk && s.values.size==2)
        println("Ráfaga: UDP=${s.receivedUdp}, ACK=${s.ackCount}, aplicados=${s.applied}, maxRxAckUs=${s.maxAckMicros}, totalMs=${(System.nanoTime()-start)/1000000}, registros descartados=${s.lostRecords}")
    }
    println("PASS H09.3 gateway UDP / secuencias, filtros, STOP, endpoint, cierre, keepalive, caché y ráfaga")
}

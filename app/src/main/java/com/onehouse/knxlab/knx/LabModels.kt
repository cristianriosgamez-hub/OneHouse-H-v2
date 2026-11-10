package com.onehouse.knxlab.knx

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.Serializable

/** Lista copiada de las direcciones de lectura por defecto de OneHouse v2.0.0 Entrega 05.
 * No incluye direcciones de mando; no depende de la configuración instalada por OneHouse.
 * Son 37 direcciones DISTINTAS: 19 estados de dispositivos + 17 estados globales + 1 válvula.
 * Las modificaciones que el usuario haya realizado dentro de OneHouse no se importan.
 */
data class LabGa(val address: String, val label: String, val group: String)

object LabCatalog {
    val addresses = listOf(
        LabGa("5/3/2", "Estado climatización", "Clima"),
        LabGa("5/3/3", "Temperatura Daikin", "Clima"),
        LabGa("5/3/4", "Modo climatización", "Clima"),
        LabGa("5/3/5", "Velocidad ventilador", "Clima"),
        LabGa("5/2/6", "Consigna", "Clima"),
        LabGa("5/1/1", "CO₂ Comedor", "Sensores"),
        LabGa("5/1/2", "Humedad Comedor", "Sensores"),
        LabGa("5/1/3", "Temperatura Comedor", "Sensores"),
        LabGa("5/2/3", "Temperatura Suite", "Sensores"),
        LabGa("5/5/1", "Bloqueo PIR Entrada", "Sensores"),
        LabGa("5/4/1", "Inundación Cocina/Baño", "Sensores"),
        LabGa("5/4/2", "Incendio Pasillo", "Sensores"),
        LabGa("15/0/11", "Luminosidad Terraza", "Sensores"),
        LabGa("15/0/13", "Viento excesivo", "Sensores"),
        LabGa("15/0/14", "Velocidad viento", "Sensores"),
        LabGa("15/0/15", "Lluvia Terraza", "Sensores"),
        LabGa("15/5/67", "ACS acumulado", "Consumos"),
        LabGa("2/4/1", "Electroválvula agua", "Mantenimiento"),
        LabGa("1/2/7", "Luz Entrada", "Luces"),
        LabGa("1/2/1", "Salón", "Luces"),
        LabGa("1/2/2", "Comedor", "Luces"),
        LabGa("1/2/4", "Lámpara salón", "Luces"),
        LabGa("2/2/3", "Persiana comedor", "Persianas"),
        LabGa("1/2/6", "Luz Cocina", "Luces"),
        LabGa("1/2/5", "Fluorescente Cocina", "Luces"),
        LabGa("2/2/6", "Persiana cocina", "Persianas"),
        LabGa("1/2/12", "Luz Suite", "Luces"),
        LabGa("1/2/14", "Mesita 1 Suite", "Luces"),
        LabGa("1/2/13", "Mesita 2 Suite", "Luces"),
        LabGa("2/2/12", "Persiana Suite", "Persianas"),
        LabGa("1/2/10", "Luz Habitación 1", "Luces"),
        LabGa("1/2/11", "Mesita Habitación 1", "Luces"),
        LabGa("2/2/9", "Persiana Habitación 1", "Persianas"),
        LabGa("1/2/15", "Luz Baño", "Luces"),
        LabGa("1/2/9", "Luz Pasillo", "Luces"),
        LabGa("1/2/8", "Luz Trastero", "Luces"),
        LabGa("1/2/3", "Luz Terraza", "Luces")
    ).also { list ->
        require(list.size == 37 && list.map { it.address }.distinct().size == 37)
    }
}

enum class LabMode(val label: String, val explanation: String, val inFlight: Int, val gapMs: Long, val firstMs: Long) {
    A("A · Original", "Secuencial: 350/900/1500 ms; pausas 1500/5000 ms", 1, 0, 350),
    B("B · Ajustada", "Secuencial: espera estado, pausas pequeñas", 1, 35, 600),
    C("C · Asíncrona", "Siguiente petición después del ACK; máximo 2 estados pendientes", 2, 45, 600),
    D("D · Ventana 3", "Peticiones tras ACK; máximo 3 estados pendientes", 3, 65, 600),
    E("E · Flujo OneHouse", "Secuencial según KnxBulkStateReader; contraste de GroupValueWrite/Response (réplica aislada, no motor completo)", 1, 0, 350),
    F("F · Motor original", "KnxConnectionManager original sin cambios funcionales; referencia lenta", 1, 0, 350),
    G("G · Prioridad segura", "Mismo motor y timeouts que F; prioridad a lecturas frente a receptor pasivo; solo GroupValueResponse válida", 1, 0, 350),
    H("H · Receptor único", "Motor experimental: un socket/lector; 1 READ pendiente; ACK + GroupValueResponse; pausa mínima 160 ms", 1, 160, 750);

    fun responseTimeoutMs(round: Int): Long = when (this) {
        A, E, F, G -> when (round) { 0 -> 350; 1 -> 900; else -> 1500 }
        H -> when (round) { 0 -> 750; 1 -> 1250; else -> 1800 }
        else -> when (round) { 0 -> 600; 1 -> 900; else -> 1400 }
    }
    fun interRoundDelayMs(round: Int): Long = when (this) {
        A, E, F, G -> if (round == 1) 1_500 else 5_000
        H -> if (round == 1) 500 else 900
        else -> if (round == 1) 300 else 700
    }
}

enum class GaStatus { PENDING, WAITING, RECEIVED, NO_RESPONSE, NO_ACK, MONITOR_ONLY }

data class GaMeasurement(
    val address: String,
    val label: String,
    val group: String,
    var status: GaStatus = GaStatus.PENDING,
    var groupRequests: Int = 0,
    var ackRetransmits: Int = 0,
    var gatewayAcks: Int = 0,
    var ackLatencyMs: Long? = null,
    var responseLatencyMs: Long? = null,
    var lastRound: Int = 0,
    var source: String = "",
    var rawHex: String = "",
    var error: String = "",
    var gatewayAckMissing: Boolean = false,
    var incomingWrites: Int = 0,
    var operationsResolvedByWrite: Int = 0,
    var firstRequestOffsetMs: Long? = null,
    var firstResponseOffsetMs: Long? = null,
    var lastResolutionOffsetMs: Long? = null
) : Serializable

/** Traza conservada con cada prueba y exportada: tiempos relativos al inicio (reloj monotónico aproximado en ms). */
data class LabTrace(
    val offsetMs: Long,
    val round: Int,
    val address: String,
    val event: String,
    val sequence: Int?,
    val detail: String = ""
) : Serializable

data class GaAttempt(
    val address: String,
    val round: Int,
    val sequence: Int,
    val sentOffsetMs: Long,
    var ackOffsetMs: Long? = null,
    var responseOffsetMs: Long? = null,
    var resolvedOffsetMs: Long? = null,
    var outcome: String = "PENDIENTE",
    var gatewayRetransmits: Int = 0
) : Serializable

data class RunSnapshot(
    val mode: LabMode,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val running: Boolean = false,
    val currentRound: Int = 0,
    val channel: Int? = null,
    val states: List<GaMeasurement> = emptyList(),
    val invalidPackets: Int = 0,
    val ignoredAck: Int = 0,
    val duplicateFrames: Int = 0,
    val connectionError: String? = null,
    val comment: String = "",
    val cancelled: Boolean = false,
    val omitEventOnly: Boolean = false,
    val connectionTimeMs: Long? = null,
    val trace: List<LabTrace> = emptyList(),
    val attempts: List<GaAttempt> = emptyList()
) : Serializable {
    val received: Int get() = states.count { it.status == GaStatus.RECEIVED }
    val total: Int get() = states.count { it.status != GaStatus.MONITOR_ONLY }
    val monitoredEvents: Int get() = states.filter { it.status == GaStatus.MONITOR_ONLY }.sumOf { it.incomingWrites }
    val percent: Int get() = if (total == 0) 0 else received * 100 / total
    val failures: Int get() = total - received
    val elapsedMs: Long get() = (endedAtMs ?: System.currentTimeMillis()) - startedAtMs
    val clean: Boolean get() = endedAtMs != null && !cancelled && connectionError == null &&
        received == total && invalidPackets == 0 && ignoredAck == 0 && duplicateFrames == 0 &&
        states.all { !it.gatewayAckMissing && it.ackRetransmits == 0 }
}

object LabCsv {
    private fun cell(value: Any?): String = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
    private fun line(vararg cells: Any?): String = cells.joinToString(";") { cell(it) }
    fun summaryRow(run: RunSnapshot): String = line(run.mode.name, run.startedAtMs, run.elapsedMs, run.received,
        run.total, run.monitoredEvents, run.omitEventOnly, run.clean, run.connectionError.orEmpty(), run.duplicateFrames,
        run.ignoredAck, run.invalidPackets, run.connectionTimeMs, run.attempts.size,
        run.states.sumOf { it.groupRequests }, run.states.sumOf { (it.groupRequests - 1).coerceAtLeast(0) })
    fun compare(history: List<RunSnapshot>): String = buildString {
        appendLine("OneHouse KNX Lab v0.6.2 — COMPARATIVA")
        appendLine("metodo;inicio_ms;duracion_ms;recibidas;total_lecturas;monitor_eventos;omitida_15_0_13;limpio;error_conexion;duplicados;ack_ajenos;invalidos;conexion_ms;intentos;solicitudes_group_read;lecturas_repetidas")
        history.forEach { run ->
            appendLine(summaryRow(run))
        }
        history.forEachIndexed { i, run ->
            appendLine()
            appendLine("=== PRUEBA ${i + 1} / ${history.size} ===")
            append(generate(run))
        }
    }

    fun generate(snapshot: RunSnapshot): String = buildString {
        appendLine("OneHouse KNX Lab v0.6.2")
        appendLine("NOTA;E replica flujo aislado; F usa motor original; G usa el motor original con prioridad; H usa receptor unificado READ-ONLY experimental; ninguno reproduce toda OneHouse")
        appendLine("metodo;inicio;duracion_ms;recibidas;total_lecturas;total_catalogo;monitor_eventos;omitir_15_0_13;porcentaje;limpio;cancelado;error_conexion;paquetes_invalidos;ack_ignorados;duplicados;conexion_ms")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(snapshot.startedAtMs))
        appendLine(line(snapshot.mode.name, date, snapshot.elapsedMs, snapshot.received,
            snapshot.total, snapshot.states.size, snapshot.monitoredEvents, snapshot.omitEventOnly, snapshot.percent,
            snapshot.clean, snapshot.cancelled, snapshot.connectionError.orEmpty(), snapshot.invalidPackets,
            snapshot.ignoredAck, snapshot.duplicateFrames, snapshot.connectionTimeMs))
        appendLine()
        appendLine("DETALLE_GA")
        appendLine("ga;nombre;categoria;estado;peticiones;reenvios_ack;ack_recibidos;ack_ms;respuesta_ms;ronda;origen;dato_hex;escrituras_espontaneas;resuelta_por_write_en_E;primera_tx_offset_ms;primera_rx_offset_ms;ultima_resolucion_offset_ms;sin_ack;error")
        snapshot.states.forEach { ga ->
            appendLine(line(ga.address, ga.label, ga.group, ga.status.name, ga.groupRequests,
                ga.ackRetransmits, ga.gatewayAcks, ga.ackLatencyMs, ga.responseLatencyMs,
                ga.lastRound, ga.source, ga.rawHex, ga.incomingWrites, ga.operationsResolvedByWrite,
                ga.firstRequestOffsetMs, ga.firstResponseOffsetMs, ga.lastResolutionOffsetMs,
                ga.gatewayAckMissing, ga.error))
        }
        appendLine()
        appendLine("INTENTOS_POR_GA")
        appendLine("ga;ronda;secuencia;tx_offset_ms;ack_offset_ms;rx_offset_ms;resuelto_offset_ms;latencia_ack_ms;latencia_rx_ms;duracion_intento_ms;reenvios_transporte;resultado")
        snapshot.attempts.forEach { a ->
            appendLine(line(a.address, a.round, a.sequence, a.sentOffsetMs, a.ackOffsetMs,
                a.responseOffsetMs, a.resolvedOffsetMs, a.ackOffsetMs?.minus(a.sentOffsetMs),
                a.responseOffsetMs?.minus(a.sentOffsetMs), a.resolvedOffsetMs?.minus(a.sentOffsetMs),
                a.gatewayRetransmits, a.outcome))
        }
        appendLine()
        appendLine("H061_DIAGNOSTICO_COMUNICACION")
        appendLine("metrica;valor")
        appendLine(line("respuestas_confirmadas", snapshot.received))
        appendLine(line("lecturas_esperadas", snapshot.total))
        appendLine(line("solicitudes_group_read", snapshot.states.sumOf { it.groupRequests }))
        appendLine(line("reenvios_transporte", snapshot.states.sumOf { it.ackRetransmits }))
        appendLine(line("esperas_respuesta_agotadas", snapshot.trace.count { it.event == "H_RESPONSE_TIMEOUT" }))
        appendLine(line("paradas_por_ausencia_ack", snapshot.trace.count { it.event == "H_STOP_NO_ACK" }))
        appendLine(line("ack_fuera_secuencia", snapshot.ignoredAck))
        appendLine(line("tramas_duplicadas", snapshot.duplicateFrames))
        appendLine(line("conexion_ms", snapshot.connectionTimeMs))
        appendLine(line("cierre", snapshot.trace.lastOrNull { it.event.startsWith("H_DISCONNECT") }?.event ?: "sin_dato"))
        appendLine("incidencias_ga;secuencia;offset_ms;evento;detalle")
        snapshot.trace.filter { it.event in setOf("H_TRANSPORT_RETX", "H_STOP_NO_ACK",
            "H_UNMATCHED_ACK", "H_RESPONSE_TIMEOUT", "H_ACK_REJECT", "H_CONNECT_TIMEOUT_DETAILS") }
            .forEach { t -> appendLine(line(t.address, t.sequence, t.offsetMs, t.event, t.detail)) }
        appendLine()
        appendLine("TRAZA_CRONOLOGICA")
        appendLine("offset_ms;ronda;ga;evento;secuencia;detalle")
        snapshot.trace.forEach { t -> appendLine(line(t.offsetMs, t.round, t.address, t.event, t.sequence, t.detail)) }
    }
}

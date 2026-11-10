package com.onehouse.app.feature.h08

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.onehouse.app.data.knx.SettingsDataStore
import com.onehouse.app.knx.KnxCentralEngine
import com.onehouse.app.knx.KnxConnectionManager
import com.onehouse.app.knx.KnxGroupAddress
import com.onehouse.knxlab.knx.H091PassiveMonitorRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Piloto EXPLÍCITO H09.2. No hay reinicio, polling de GAs ni servicio en segundo plano. */
object H091MonitorCoordinator {
    data class UiState(
        val running: Boolean = false,
        val connected: Boolean = false,
        val stopping: Boolean = false,
        val recovering: Boolean = false,
        val message: String = "Monitor H09.2 inactivo: lectura H08 PASS antes de iniciar.",
        val snapshot: H091PassiveMonitorRunner.Snapshot? = null,
        val readId: String = "",
        val endpoint: String = ""
    )
    private val handler = Handler(Looper.getMainLooper())
    private val receiver = H091PassiveMonitorRunner()
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()

    /** Incluye el intervalo entre solicitud de STOP y DISCONNECT_RESPONSE. */
    private var invalidReadId = ""
    fun permitsReadId(id: String) = id != invalidReadId
    fun isBusy(): Boolean = mutable.value.running || mutable.value.recovering || receiver.isRunning()

    private data class Route(val network: Network, val ipv4: Set<String>)

    private fun wifiRoute(context: Context): Route? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return@runCatching null
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@runCatching null
        val ip = cm.getLinkProperties(net)?.linkAddresses?.mapNotNull {
            (it.address as? Inet4Address)?.hostAddress
        }?.toSet().orEmpty()
        if (ip.isEmpty()) null else Route(net, ip)
    }.getOrNull()

    fun start(context: Context): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val app = context.applicationContext
        if (isBusy()) return false
        val read = H08Coordinator.status.value
        if (!read.lastRunSuccess || !read.success || read.running ||
            read.imported != H08Safety.EXPECTED_READS ||
            !permitsReadId(read.lastRunId) || read.lastRunId.isBlank() || H082LightControlCoordinator.isBusy() ||
            read.runFinishedAtEpochMillis <= 0L ||
            System.currentTimeMillis() - read.runFinishedAtEpochMillis !in 0L..300_000L) {
            mutable.value = mutable.value.copy(message = "Primero realiza una lectura H08 PASS reciente (máximo 5 minutos), sin mandos pendientes.")
            return false
        }
        val settings = SettingsDataStore(app).read()
        val port = settings.localPort.toIntOrNull()
        val endpoint = "${settings.localIp}:${settings.localPort}"
        if (port == null || port !in 1..65535 || read.lastEndpoint != endpoint) {
            mutable.value = mutable.value.copy(message = "Gateway diferente al de la lectura PASS; vuelve a Leer H.")
            return false
        }
        val initialRoute = wifiRoute(app)
        if (initialRoute == null) {
            mutable.value = mutable.value.copy(message = "Monitor no iniciado: conecta la WiFi local sin VPN.")
            return false
        }
        // El marcador running se establece ANTES de abrir el socket: ningún mando
        // ni nueva lectura pueden comenzar mientras se abre/cierra este túnel.
        mutable.value = UiState(
            running = true, message = "H09.2: conectando receptor pasivo (0 GroupValueRead)...",
            readId = read.lastRunId, endpoint = endpoint
        )
        val started = receiver.start(
            host = settings.localIp,
            port = port,
            socketBinder = { socket -> initialRoute.network.bindSocket(socket) },
            routeHealthCheck = {
                val current = wifiRoute(app)
                when {
                    current == null -> "wifi_no_disponible"
                    current.network != initialRoute.network -> "network_cambiada"
                    current.ipv4.intersect(initialRoute.ipv4).isEmpty() -> "ipv4_cambiada"
                    else -> null
                }
            },
            onSnapshot = { snapshot ->
                handler.post {
                    if (mutable.value.running && !mutable.value.stopping) {
                        mutable.value = mutable.value.copy(
                            connected = snapshot.connected,
                            snapshot = snapshot,
                            message = if (snapshot.connected)
                                "H09.2 activo: ${snapshot.acceptedWrites} eventos Cocina; 0 lecturas; límite 90 s."
                                else "H09.2 conectando..."
                        )
                    }
                }
            },
            onAcceptedWrite = { sample ->
                // Sólo se guarda si el telegrama es GroupValueWrite, DPT 1.001 y
                // GA incluida en la lista blanca. Nunca inferimos OFF por silencio.
                val telegram = KnxConnectionManager.IncomingGroupTelegram(
                    kind = KnxConnectionManager.IncomingGroupTelegram.Kind.WRITE,
                    sourceAddress = sample.source,
                    destination = KnxGroupAddress.parse(sample.address),
                    booleanValue = sample.value,
                    payload = byteArrayOf(if (sample.value) 1 else 0),
                    messageCode = 0x29,
                    apci = "GroupValueWrite",
                    cemiHex = ""
                )
                KnxCentralEngine.get(app).stateRepository.record(telegram)
            },
            onFinish = { snapshot ->
                handler.post {
                    mutable.value = mutable.value.copy(
                        running = false, stopping = false, recovering = snapshot.recoveryRequired,
                        connected = false,
                        snapshot = snapshot,
                        message = "H09.2 finalizado: ${snapshot.stopReason}; " +
                            "eventos=${snapshot.acceptedWrites}; " +
                            "cierre=${snapshot.closeResult}. " +
                            if (snapshot.recoveryRequired) "Recuperación: espera 30 s y realiza otra lectura H." else "Exporta CSV."
                    )
                    if (snapshot.recoveryRequired || snapshot.stopReason.startsWith("ERROR")) invalidReadId = mutable.value.readId
                    if (snapshot.recoveryRequired) {
                        handler.postDelayed({
                            mutable.value = mutable.value.copy(recovering = false, message = "Recuperación: comprueba el canal y realiza una nueva lectura H.")
                        }, 30_000L)
                    }
                }
            }
        )
        if (!started) {
            mutable.value = mutable.value.copy(running = false, message = "Monitor ocupado; no se abrió otro túnel.")
        }
        return started
    }

    fun stop() {
        if (!mutable.value.running) return
        receiver.requestStop()
        mutable.value = mutable.value.copy(stopping = true, connected = false, message = "Deteniendo H09.2 y cerrando el túnel KNX/IP... (sin nuevas lecturas)")
    }

    private fun utc(timeMs: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(timeMs))
    }

    fun exportCsv(): String {
        val s = state.value
        val snap = s.snapshot ?: return "H09.2;sin_telemetria\n"
        return buildString {
            appendLine("ONEHOUSE_H092_ENTREGA36_MONITOR_PASIVO_COCINA_TEST")
            appendLine("lectura_H08_origen;${s.readId}")
            appendLine("gateway;${s.endpoint}")
            appendLine("inicio_utc;${utc(snap.startedEpochMs)}")
            appendLine("fin_de_monitor;${!snap.running}")
            appendLine("canal;${snap.channel ?: "---"}")
            appendLine("eventos_escritura_validos;${snap.acceptedWrites}")
            appendLine("tramas_duplicadas;${snap.duplicateFrames}")
            appendLine("tramas_anomalas;${snap.unexpectedFrames}")
            appendLine("udp_recibidos;${snap.receivedUdp}")
            appendLine("solicitudes_tunel_TX_medidas;${snap.outgoingTunnelRequests}")
            appendLine("aplicados_cache;${snap.applied}")
            appendLine("duplicados_cache;${snap.cacheDuplicates}")
            appendLine("errores_aplicacion;${snap.applyErrors}")
            appendLine("descartados;${snap.discarded}")
            appendLine("registros_descartados;${snap.lostRecords}")
            appendLine("ack_enviados;${snap.ackCount}")
            appendLine("max_recepcion_a_ack_us_escucha;${snap.maxAckMicros}")
            appendLine("connect;${snap.connectResult.replace(';', ',')}")
            appendLine("primer_error;${snap.firstError.replace(';', ',').replace('\n',' ')}")
            appendLine("disconnect;${snap.closeResult.replace(';', ',')}")
            appendLine("motivo_fin;${snap.stopReason.replace(';', ',').replace('\n', ' ')}")
            appendLine("disconnect_ok;${snap.disconnectedOk}")
            appendLine("validacion_fisica;PENDIENTE_USUARIO")
            appendLine("GA_piloto;1/2/6 (Luz Cocina),1/2/5 (Fluorescente Cocina)")
            appendLine("nota;El silencio no confirma OFF. Un dispositivo que no publica cambios en GA de estado no puede actualizarse por eventos.")
            appendLine()
            appendLine("offset_ms;evento;GA;detalle")
            snap.events.forEach { item ->
                appendLine("${item.atMs};${item.kind};${item.address};${item.detail}")
            }
        }
    }
}

package com.onehouse.app.feature.h08

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.onehouse.app.data.knx.KnxConnectionStatus
import com.onehouse.app.data.knx.SettingsDataStore
import com.onehouse.app.knx.AppKnxConfigurationRepository
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxCentralEngine
import com.onehouse.app.knx.KnxDeviceFactory
import com.onehouse.app.knx.KnxHomeStateRepository
import com.onehouse.app.knx.KnxLoadProgressRepository
import com.onehouse.knxlab.knx.GaStatus
import com.onehouse.knxlab.knx.KnxLabRunner
import com.onehouse.knxlab.knx.LabCatalog
import com.onehouse.knxlab.knx.LabCsv
import com.onehouse.knxlab.knx.RunSnapshot
import com.onehouse.knxlab.knx.UnifiedReceiverRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.DatagramSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * H08.2.2: las pantallas reales se alimentan con lecturas H. Dos mandos de Cocina
 * se ejecutan exclusivamente con el escritor H07 validado; gestor antiguo bloqueado.
 * H es dueño exclusivo del socket. El gestor antiguo no puede conectar.
 * Los estados importados fluyen al MISMO KnxStateRepository que consumen
 * Inicio, Estancias, Climatización y Mantenimiento.
 *
 * No hay polling por minuto, ni receptor continuo ni automatizaciones.
 * Los mandos experimentales autorizados se realizan por un túnel separado
 * exclusivamente DESPUÉS de cerrar el túnel de lectura H, nunca simultáneo.
 * La vida del socket H termina ANTES de publicar la caché a las pantallas.
 */
object H08Safety {
    const val READ_ONLY: Boolean = true
    const val BLOCKED_MESSAGE = "H08.2.2 TEST: gestor KNX antiguo y automatizaciones bloqueados. Solo dos luces de Cocina con el ejecutor H autorizado."
    val EVENT_ONLY: Set<String> = setOf("15/0/13", "15/0/15")
    const val EXPECTED_READS = 35
}

data class H08Status(
    val configured: Boolean = false,
    val running: Boolean = false,
    val success: Boolean = false,
    val received: Int = 0,
    val total: Int = H08Safety.EXPECTED_READS,
    val imported: Int = 0,
    val message: String = "Configura la IP local para leer estados H.",
    val lastRun: RunSnapshot? = null,
    val lastEndpoint: String = "",
    val screenLoadMs: Long? = null,
    val processToCacheMs: Long? = null,
    // Metadatos INMUTABLES del ultimo resultado. No se modifican al guardar IP.
    val lastRunSuccess: Boolean = false,
    val lastRunId: String = "",
    val lastRunSequence: Int = 0,
    val runStartedAtEpochMillis: Long = 0L,
    val runFinishedAtEpochMillis: Long = 0L
)

object H08Coordinator {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val runner = UnifiedReceiverRunner()
    private val mutableStatus = MutableStateFlow(H08Status())
    val status = mutableStatus.asStateFlow()
    private var appContext: Context? = null
    private var lastAutoStartAtMillis = 0L
    private val processStartMillis = System.currentTimeMillis()
    private var loadStartMillis: Long = 0L
    private var nextRunSequence: Int = 0
    private var lastExportedRunId = ""
    private var exportNumberForCurrentRun = 0
    // Tras EPERM, evitar intentos manuales inmediatos con una ruta inestable.
    // No se programa ninguna reconexion; solo se vuelve a permitir el boton.
    private var udpErrorCooldownUntilMs = 0L

    fun initialize(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        // Prepara el proceso/limpia caché de sesiones previas ANTES de leer H;
        // la instancia NO inicia el lector antiguo porque H08Safety es true.
        KnxHomeStateRepository(app)
        val settings = SettingsDataStore(app).read()
        mutableStatus.value = H08Status(
            configured = settings.lastUpdatedEpochMillis > 0L,
            message = if (settings.lastUpdatedEpochMillis > 0L) {
                "Preparado: ${settings.localIp}:${settings.localPort}. Lecturas H solo bajo WiFi."
            } else "Introduce la IP del gateway KNX/IP. H08 no modifica OneHouse estable."
        )
    }

    fun settingsIp(): Pair<String, String> {
        val context = appContext ?: return "" to "3671"
        return SettingsDataStore(context).read().let { it.localIp to it.localPort }
    }

    fun saveEndpoint(ip: String, portText: String): String? {
        if (H091MonitorCoordinator.isBusy()) return "Detén primero el monitor H09.1 y espera al cierre del túnel."
        if (H082LightControlCoordinator.isBusy()) return "Orden H08.2 en curso: no se puede cambiar el gateway."
        if (status.value.running) return "Hay una lectura H en curso."
        val host = ip.trim()
        val octets = host.split('.')
        if (octets.size != 4 || octets.any { item ->
                item.isEmpty() || item.length > 3 || item.any { !it.isDigit() } ||
                    (item.toIntOrNull()?.let { it in 0..255 } != true)
            }) return "Introduce una IPv4 válida de tu gateway KNX/IP."
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) return "Puerto inválido (1–65535)."
        val app = appContext ?: return "Aplicación no inicializada."
        val store = SettingsDataStore(app)
        store.write(store.read().copy(
            localIp = host,
            localPort = port.toString(),
            lastUpdatedEpochMillis = System.currentTimeMillis(),
            lastConnectionStatus = KnxConnectionStatus.NOT_TESTED,
            lastStatusMessage = "Pendiente de lectura H08"
        ))
        val endpoint = "$host:$port"
        val previous = status.value
        val endpointChanged = previous.lastRun != null && previous.lastEndpoint != endpoint
        if (endpointChanged) {
            // El resultado anterior pertenece a otro gateway: no volver a exportarlo
            // como si correspondiese a la nueva configuración.
            KnxCentralEngine.get(app).stateRepository.clear()
            lastExportedRunId = ""
            exportNumberForCurrentRun = 0
            mutableStatus.value = H08Status(
                configured = true,
                message = "Gateway cambiado a $endpoint. Lectura anterior invalidada; pulsa Leer H."
            )
        } else {
            // Guardar LA MISMA IP no altera el veredicto de la última lectura.
            mutableStatus.value = previous.copy(
                configured = true,
                message = if (previous.lastRun != null) {
                    "IP guardada ($endpoint). Ultima lectura ${if (previous.lastRunSuccess) "PASS" else "NO_VALIDADA"} " +
                        "(ID ${previous.lastRunId}); pulsa Leer H para una nueva ejecución."
                } else "IP guardada en H08.2.2: $endpoint. Pulsa Leer H."
            )
        }
        return null
    }

    fun startOnLaunchIfConfigured() {
        if (!status.value.configured || status.value.running) return
        val now = System.currentTimeMillis()
        // Evita lecturas duplicadas tras una rotación o reapertura inmediata.
        if (now - lastAutoStartAtMillis in 0..30_000L) return
        lastAutoStartAtMillis = now
        startRead()
    }

    // H08.2.2: el túnel de lectura queda anclado explícitamente a la misma red WiFi
    // activa que validamos para los controles. Así no depende de una ruta UDP
    // implícita durante la inicialización de Android/HyperOS.
    // No se solicita una segunda red ni se abren sockets auxiliares.
    private data class WifiRoute(val network: Network, val detail: String, val ipv4: Set<String>)

    private fun activeWifiRoute(app: Context): WifiRoute? = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return@runCatching null
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@runCatching null
        val links = cm.getLinkProperties(net)
        val ipv4 = links?.linkAddresses?.mapNotNull { link ->
            (link.address as? Inet4Address)?.hostAddress
        }.orEmpty()
        if (ipv4.isEmpty()) return@runCatching null
        WifiRoute(net, "WiFi activa;interfaz=${links?.interfaceName ?: "desconocida"};" +
            "ipv4=${ipv4.joinToString()};network=$net;sin_VPN_y_sin_celular", ipv4.toSet())
    }.getOrNull()

    internal fun wifiOnly(app: Context): Boolean = activeWifiRoute(app) != null

    /** Se mantiene el catálogo fijo auditado H; otras GAs configuradas bloquean la prueba. */
    private fun mappingIssue(context: Context): String? {
        val config = AppKnxConfigurationRepository(context)
        val project = config.loadProject() ?: return "No se pudo leer el proyecto KNX."
        val all = LabCatalog.addresses.map { it.address }.toSet()
        val deviceUnknown = project.devices.flatMap { it.readAddresses }.distinct()
            .filterNot { it in all }
        if (deviceUnknown.isNotEmpty()) return "GAs personalizadas fuera de H: ${deviceUnknown.joinToString()}. No se ha leído."
        val globalsChanged = KnxAddressBook.entries.filter { entry ->
            val isCommand = entry.kind == "Mando"
            !isCommand && config.globalAddress(entry.key, entry.defaultAddress) != entry.defaultAddress
        }
        if (globalsChanged.isNotEmpty()) return "GAs globales personalizadas: ${globalsChanged.joinToString { it.key }}. H08 requiere mapa validado."
        return null
    }

    fun startRead(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val app = appContext ?: return false
        if (status.value.running || H082LightControlCoordinator.isBusy() || H091MonitorCoordinator.isBusy()) return false
        val waitMs = udpErrorCooldownUntilMs - System.currentTimeMillis()
        if (waitMs > 0L) {
            mutableStatus.value = status.value.copy(
                message = "H08.2.2: espera ${((waitMs + 999L) / 1000L)} s tras error UDP antes de reintentar manualmente. " +
                    "Comprueba que el WiFi sigue conectado.")
            return false
        }
        val settings = SettingsDataStore(app).read()
        if (settings.lastUpdatedEpochMillis <= 0L) {
            mutableStatus.value = status.value.copy(message = "Primero configura tu IP local.")
            return false
        }
        val wifiRoute = activeWifiRoute(app)
        if (wifiRoute == null) {
            mutableStatus.value = status.value.copy(success = false,
                message = "H08.2.2: no hay red WiFi activa compatible (sin VPN/celular). No se ha abierto túnel.")
            return false
        }
        val issue = mappingIssue(app)
        if (issue != null) {
            mutableStatus.value = status.value.copy(success = false, message = issue)
            return false
        }
        val port = settings.localPort.toIntOrNull()
        if (port == null || port !in 1..65535) {
            mutableStatus.value = status.value.copy(success = false, message = "Puerto KNX/IP inválido.")
            return false
        }
        val endpoint = "${settings.localIp}:$port"
        val states = KnxCentralEngine.get(app).stateRepository
        // No dejar valores caducados en pantalla cuando falla una siguiente lectura.
        states.clear()
        val config = AppKnxConfigurationRepository(app)
        KnxAddressBook.apply(config)
        val devices = config.loadProject()?.let(KnxDeviceFactory::create).orEmpty()
        val addresses = LabCatalog.addresses.map { it.address }.filterNot { it in H08Safety.EVENT_ONLY }
        KnxLoadProgressRepository.begin(KnxLoadProgressRepository.buildTargets(devices, addresses))
        // Una nueva ejecución reemplaza el resultado anterior; jamás reciclar el CSV
        // anterior ante un intento nuevo. La secuencia distingue las ejecuciones.
        val startedAt = System.currentTimeMillis()
        val sequence = ++nextRunSequence
        val runId = "H0822-$startedAt-$sequence"
        mutableStatus.value = H08Status(
            configured = true, running = true,
            message = "Lectura $sequence en curso (ID $runId): 35 direcciones...",
            lastEndpoint = endpoint,
            lastRunId = runId,
            lastRunSequence = sequence,
            runStartedAtEpochMillis = startedAt
        )
        loadStartMillis = startedAt
        val began = runner.run(
            target = KnxLabRunner.Target(settings.localIp, port, nat = false),
            omitEventOnly = true,
            networkContext = "H08.2.2;${wifiRoute.detail};lector_antiguo_prohibido",
            // Solo altera la selección de ruta UDP. El motor H conserva CONNECT,
            // intervalos, ACK, 35 lecturas, cierre y sus límites exactos.
            socketBinder = { udp: DatagramSocket -> wifiRoute.network.bindSocket(udp) },
            // Solo consulta metadatos Android: NO crea sockets ni trafico KNX.
            routeHealthCheck = {
                val current = activeWifiRoute(app)
                when {
                    current == null -> "wifi_sin_red_compatible_o_sin_ipv4"
                    current.network != wifiRoute.network -> "network_cambiada;inicial=${wifiRoute.network};actual=${current.network}"
                    current.ipv4.intersect(wifiRoute.ipv4).isEmpty() ->
                        "ipv4_cambiada;inicial=${wifiRoute.ipv4};actual=${current.ipv4}"
                    else -> null
                }
            },
            onProgress = { run ->
                mainHandler.post {
                    if (!status.value.running) return@post
                    mutableStatus.value = status.value.copy(
                        received = run.received, total = run.total,
                        message = "Leyendo KNX: ${run.received}/${run.total}; una sola petición pendiente"
                    )
                }
            },
            onFinished = { run -> mainHandler.post { finish(app, run, endpoint) } }
        )
        if (!began) {
            mutableStatus.value = status.value.copy(running = false, success = false,
                message = "El receptor H ya tiene una ejecución en curso; esta lectura no comenzó.",
                lastRunId = "", lastRunSequence = 0, runStartedAtEpochMillis = 0L)
        }
        return began
    }

    private fun finish(app: Context, run: RunSnapshot, endpoint: String) {
        val measured = run.states.filter { it.status != GaStatus.MONITOR_ONLY }
        val ackCorrect = measured.size == H08Safety.EXPECTED_READS && measured.all {
            it.status == GaStatus.RECEIVED && it.groupRequests == 1 &&
                it.gatewayAcks == 1 && it.ackRetransmits == 0 && !it.gatewayAckMissing
        }
        val closed = run.trace.any { it.event == "H_DISCONNECT_OK" } &&
            run.trace.none { it.event in setOf("H_DISCONNECT_ERROR", "H_DISCONNECT_REJECT", "H_DISCONNECT_NO_RESPONSE") }
        val accepted = run.clean && ackCorrect && closed &&
            run.states.count { it.status == GaStatus.MONITOR_ONLY } == 2 &&
            run.trace.none { it.event == "H_ACK_REJECT" }
        val repository = KnxCentralEngine.get(app).stateRepository
        val imported = if (accepted) HResultBridge.mergeReadOnly(run, repository) else 0
        val success = accepted && imported == H08Safety.EXPECTED_READS
        val missing = measured.filter { it.status != GaStatus.RECEIVED }.map { it.address }
        KnxLoadProgressRepository.syncReceived(repository.snapshot())
        KnxLoadProgressRepository.finish(missing, repository.snapshot())
        // No llamar "tiempo hasta caché" a una ejecucion que NO importo estados.
        val screenLoadMs = if (success) System.currentTimeMillis() - loadStartMillis else null
        val processToCacheMs = if (success) System.currentTimeMillis() - processStartMillis else null
        val udpFailure = run.trace.any { it.event in setOf(
            "H_UDP_SEND_FAILURE", "H_UDP_RECEIVE_FAILURE", "H_WIFI_ROUTE_LOST") } ||
            (run.connectionError?.contains("EPERM", ignoreCase = true) == true)
        if (udpFailure) udpErrorCooldownUntilMs = System.currentTimeMillis() + 30_000L
        val diagnostic = when {
            success -> "H08 PASS: ${run.received}/${run.total} en ${run.elapsedMs} ms; caché ${screenLoadMs} ms; cierre OK."
            udpFailure -> "H08.2.2: fallo UDP o ruta WiFi cambiada. Sin importar estados. " +
                "Socket cerrado localmente; si DISCONNECT falló, el túnel remoto puede tardar en liberarse. " +
                "Espera 30 s y comprueba WiFi antes del reintento MANUAL. Exporta CSV."
            run.connectionError?.contains("Sin CONNECT_RESPONSE") == true ->
                "H08.2.2: sin respuesta CONNECT en 4 s. Socket UDP cerrado; exporta CSV y verifica WiFi."
            run.trace.any { it.event == "H_WIFI_BIND_ERROR" } ->
                "H08.2.2: no se pudo vincular socket a WiFi. No se abrió túnel; exporta CSV."
            !closed -> "H08 NO VALIDADA: cierre de túnel sin confirmación. Revisa el CSV; no se importan estados."
            !accepted -> "H08 NO VALIDADA: ${run.received}/${run.total}; ACK, reintentos o tramas anómalas. Sin importar estados."
            else -> "H08 NO VALIDADA: $imported/${H08Safety.EXPECTED_READS} estados importados. Revisa el CSV."
        }
        val startedStatus = mutableStatus.value
        mutableStatus.value = H08Status(
            configured = true,
            running = false,
            success = success,
            lastRunSuccess = success,
            lastRunId = startedStatus.lastRunId,
            lastRunSequence = startedStatus.lastRunSequence,
            runStartedAtEpochMillis = startedStatus.runStartedAtEpochMillis,
            runFinishedAtEpochMillis = System.currentTimeMillis(),
            received = run.received,
            total = run.total,
            imported = imported,
            message = diagnostic,
            lastRun = run,
            lastEndpoint = endpoint,
            screenLoadMs = screenLoadMs,
            processToCacheMs = processToCacheMs
        )
    }

    private fun isoUtc(epochMillis: Long): String {
        if (epochMillis <= 0) return "---"
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(epochMillis))
    }

    fun exportCsv(): String {
        val s = status.value
        val run = s.lastRun ?: return "H08.2.2;sin_lectura_completada\n"
        if (s.running || s.lastRunId.isBlank()) return "H08.2.2;lectura_en_curso_o_sin_id\n"
        // El ID y el veredicto proceden exclusivamente del resultado completado;
        // pulsar Guardar IP no puede modificar ni falsificar su PASS/NO_VALIDADO.
        if (lastExportedRunId != s.lastRunId) {
            lastExportedRunId = s.lastRunId
            exportNumberForCurrentRun = 0
        }
        val exportOrdinal = ++exportNumberForCurrentRun
        return buildString {
            appendLine("OneHouse H08.2.2 / Entrega 34 - Auditoria ruta WiFi/UDP y cache TEST")
            appendLine("estado;${if (s.lastRunSuccess) "PASS" else "NO_VALIDADO"}")
            appendLine("lectura_id;${s.lastRunId}")
            appendLine("lectura_numero;${s.lastRunSequence}")
            appendLine("lectura_inicio_utc;${isoUtc(s.runStartedAtEpochMillis)}")
            appendLine("lectura_fin_utc;${isoUtc(s.runFinishedAtEpochMillis)}")
            appendLine("csv_exportacion_numero_misma_lectura;$exportOrdinal")
            appendLine("csv_tipo;${if (exportOrdinal == 1) "PRIMERA_EXPORTACION" else "REEXPORTACION_MISMA_LECTURA"}")
            appendLine("csv_exportado_utc;${isoUtc(System.currentTimeMillis())}")
            appendLine("csv_nota;Comparar lectura_id: si coincide, son exportaciones de una misma lectura, no ejecuciones diferentes.")
            appendLine("duracion_h_ms;${run.elapsedMs}")
            appendLine("duracion_hasta_cache_real_ms;${s.screenLoadMs ?: -1}")
            appendLine("duracion_desde_inicio_proceso_hasta_cache_ms;${s.processToCacheMs ?: -1}")
            appendLine("solicitudes;${run.states.sumOf { it.groupRequests }}")
            appendLine("reintentos_ack;${run.states.sumOf { it.ackRetransmits }}")
            appendLine("respuestas;${run.received}/${run.total}")
            appendLine("importados_en_pantallas;${s.imported}")
            appendLine("tunnel_disconnect_ok;${run.trace.any { it.event == "H_DISCONNECT_OK" }}")
            appendLine("h0821_wifi_bind;${when {
                run.trace.any { it.event == "H_WIFI_BIND_OK" } -> "OK"
                run.trace.any { it.event == "H_WIFI_BIND_ERROR" } -> "ERROR"
                else -> "NO_CONFIRMADO"
            }}")
            appendLine("h0821_connect_intentos;${run.trace.count { it.event == "H_CONNECT_TX" }}")
            appendLine("h0821_connect_respuestas;${run.trace.count { it.event == "H_CONNECT_RESPONSE_RX" }}")
            appendLine("h0821_error_conexion;${run.connectionError ?: "ninguno"}")
            appendLine("h0821_conectar_reintento;SOLO_MANUAL_TRAS_CIERRE_SOCKET_SIN_AUTO_REINTENTO")
            appendLine("h0821_tiempo_inicio_lectura_hasta_cache_ms;${s.screenLoadMs ?: -1}")
            appendLine("h0821_tiempo_desde_arranque_app_ms;SOLO_DIAGNOSTICO_NO_COMPARAR_CON_10S")
            appendLine("h0822_error_envio_udp;${run.trace.any { it.event == "H_UDP_SEND_FAILURE" }}")
            appendLine("h0822_error_recepcion_udp;${run.trace.any { it.event == "H_UDP_RECEIVE_FAILURE" }}")
            appendLine("h0822_ruta_wifi_perdida;${run.trace.any { it.event == "H_WIFI_ROUTE_LOST" }}")
            appendLine("h0822_ruta_al_cerrar;${run.trace.lastOrNull { it.event == "H_DISCONNECT_ROUTE" }?.detail ?: "sin_dato"}")
            appendLine("h0822_retransmisiones_estrictas;${run.states.sumOf { it.ackRetransmits }}")
            appendLine("h0822_cache_actualizada;${if (s.lastRunSuccess && s.imported == H08Safety.EXPECTED_READS) "SI" else "NO"}")
            appendLine("h0822_tiempo_hasta_cache;${s.screenLoadMs ?: "NO_ACTUALIZADA"}")
            appendLine("h0822_politica_aceptacion;35_DE_35_35_GVR_35_ACK_SIN_RETX_Y_DISCONNECT_OK")
            appendLine("h0822_recuperacion;SOLO_MANUAL_SIN_RECONEXION_AUTOMATICA")
            appendLine("sensor_lluvia;solo_evento_sin_groupvalueread")
            appendLine("sensor_viento_excesivo;solo_evento_sin_groupvalueread")
            appendLine("comandos;SOLO_DOS_LUCES_COCINA_CONTROLADAS_CON_H07")
            appendLine("control_ultimo;${H082LightControlCoordinator.exportSummaryForReadCsv()}")
            appendLine("segundo_plano;NO_VALIDADO")
            appendLine("nota;Los estados son instantáneos tras la lectura H; no hay monitor en tiempo real todavía.")
            appendLine()
            append(LabCsv.generate(run))
        }
    }
}

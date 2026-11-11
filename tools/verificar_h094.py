#!/usr/bin/env python3
"""Comprobaciones estáticas de Entrega 38; no sustituye compilación Android/KNX real."""
from pathlib import Path
import hashlib
r=Path(__file__).resolve().parents[1]
s=r/'app/src/main/java/com/onehouse'
read=(s/'knxlab/knx/UnifiedReceiverRunner.kt').read_bytes()
codec=(s/'knxlab/knx/KnxProtocol.kt').read_bytes()
monitor=(s/'knxlab/knx/H091PassiveMonitorRunner.kt').read_text(encoding='utf-8')
coord=(s/'app/feature/h08/H091MonitorCoordinator.kt').read_text(encoding='utf-8')
h08=(s/'app/feature/h08/H08Coordinator.kt').read_text(encoding='utf-8')
control=(s/'app/feature/h08/H082LightControlCoordinator.kt').read_text(encoding='utf-8')
ui=(s/'app/feature/h08/H08Toolbar.kt').read_text(encoding='utf-8')
app=(s/'app/MainActivity.kt').read_text(encoding='utf-8')
gradle=(r/'app/build.gradle.kts').read_text(encoding='utf-8')
manifest=(r/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
checks={
    'Lector H08.2.2 IDENTICO a Entrega34':hashlib.sha256(read).hexdigest()=='d63f4c01a1f950560bea11690de3577b163fc51eb09fc98f846180012c4c82f8',
    'Codec KNX intacto desde E34':hashlib.sha256(codec).hexdigest()=='205a6d6eb384be6301acc3bee425cbe8b14d73a37cb76f95bd50d6afb3fb0eb7',
    'GAs piloto exactas': 'setOf("1/2/6", "1/2/5")' in monitor,
    'Límite 90 s': 'MAX_MONITOR_MS: Long = 90_000L' in monitor,
    'Sin GroupValueRead/Write en monitor': 'tunnelReadRequest(' not in monitor and 'tunnelWrite' not in monitor and 'connectionStateRequest(' in monitor,
    'Rechazo de tramas no GroupValueWrite': '!frame.isGroupWrite' in monitor,
    'Filtros de duplicados y secuencias': 'lastSeq == meta.sequence' in monitor and 'SEQUENCE_GAP' in monitor,
    'Filtro booleano 0/1': 'raw !in 0..1' in monitor,
    'Mismo WiFi y ruta durante sesión': 'initialRoute.network.bindSocket(socket)' in coord and 'current.network != initialRoute.network' in coord,
    'Bloqueos lectura/control/IP': h08.count('H091MonitorCoordinator.isBusy()')>=2 and 'H091MonitorCoordinator.isBusy()' in control,
    'Fin primer plano': 'override fun onStop()' in app and 'activityLifecycle.stopped(visibilityToken, isChangingConfigurations)' in app,
    'Sólo iniciativa usuario': 'H091MonitorCoordinator.start(context)' in ui and 'H091MonitorCoordinator.start(' not in app,
    'CSV Eventos separado': 'OneHouse_H094_COCINA_EVENTOS_' in ui and 'fun exportCsv()' in coord,
    'GAs H08 respetadas': 'EXPECTED_READS = 35' in h08 and '15/0/13", "15/0/15' in h08,
    'Legacy / background desactivados': 'READ_ONLY: Boolean = true' in h08 and manifest.count('android:enabled="false"')>=4,
    'ID experimental y versión nueva': 'applicationId = "com.onehouse.app.h08"' in gradle and 'versionCode = 20904' in gradle,
    'Etiqueta H09.4': 'OneHouse H09.4 TEST' in (r/'app/src/main/res/values/strings.xml').read_text(encoding='utf-8'),
    'LEEME H09.4': (r/'LEEME_H09_4_ENTREGA38_Ciclo_Vida_Rotacion_TEST.txt').is_file(),
}
checks.update({
    'Inicio de secuencia estricto 0': 'val expected = lastSeq?.let { (it + 1) and 255 } ?: 0' in monitor,
    'Plazo absoluto y monotónico': 'recoveryDeadline = recoveryStarted + sequenceRecoveryMs' in monitor and 'checkSequenceDeadline()' in monitor and 'System.nanoTime()' in monitor,
    'Misma política en cierre': 'tunnel(bytes, closing = true)' in monitor,
    'Recuperación sin saltar secuencias': 'SEQUENCE_RECOVERY_OK' in monitor and 'lastSeq = meta.sequence' in monitor and 'SEQUENCE_RECOVERY_TIMEOUT' in monitor,
    'Nueva lectura tras fallo y STOP sin recuperar': 'snapshot.freshReadRequired || snapshot.stopReason.startsWith("ERROR")' in coord,
    'Resultado recuperación retenido en CSV': 'ultima_recuperacion;' in coord and 'recuperaciones_completadas;' in coord,
    'Barrera STOP y cola H09.2': 'synchronized(admission)' in monitor and 'ArrayBlockingQueue<Pair<Sample,Int>>(64)' in monitor,
    'Traza acotada 320': 'MAX_EVENTS = 320' in monitor and 'lostRecords++' in monitor,
    'Diagnóstico ACK/endpoint': 'ACK_TX' in monitor and 'ENDPOINT_DISCARD' in monitor and 'UNSOLICITED_TUNNEL_ACK' in monitor,
    'Simulador reproducible': (r/'tools/test_h093_udp.sh').is_file(),
})
policy=(s/'app/feature/h08/H09ActivityLifecycle.kt').read_text(encoding='utf-8')
checks.update({
    'Activity distingue configuracion': 'activityLifecycle.stopped(visibilityToken, isChangingConfigurations)' in app,
    'Reemplazo visible conserva sesion': 'activityLifecycle.started(visibilityToken)' in app and 'cancelPending()' in policy,
    'Gracia acotada sin Activity retenida': 'RECREATION_GRACE_MS = 1_000L' in policy and 'mutableSetOf<Any>()' in policy,
    'Pantalla bloqueada detiene': 'Intent.ACTION_SCREEN_OFF' in coord and 'activityLifecycle.screenOff()' in coord,
    'Creacion sin arranque repetido': 'consumeInitialLaunch(savedInstanceState != null)' in app and 'launchHandled = true' in policy,
})
for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+' '+name)
if not all(checks.values()): raise SystemExit(1)

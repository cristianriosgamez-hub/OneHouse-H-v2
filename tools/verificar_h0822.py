#!/usr/bin/env python3
"""Comprobaciones ESTATICAS H08.2.2; no son compilacion Android ni pruebas KNX reales."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/com/onehouse'
engine=(j/'knxlab/knx/UnifiedReceiverRunner.kt').read_text()
coord=(j/'app/feature/h08/H08Coordinator.kt').read_text()
manifest=(r/'app/src/main/AndroidManifest.xml').read_text()
gradle=(r/'app/build.gradle.kts').read_text()
light=(j/'app/feature/h08/H082LightControlCoordinator.kt').read_text()
runner=(j/'knxlab/knx/H07LightControlRunner.kt').read_text()
checks={
 'App experimental separada de estable': 'applicationId = "com.onehouse.app.h08"' in gradle,
 'H08.2.2 versionCode': 'versionCode = 20804' in gradle,
 'Launcher TEST naranja': 'OneHouse H08.2.2 TEST' in (r/'app/src/main/res/values/strings.xml').read_text() and '#F47714' in (r/'app/src/main/res/drawable/ic_launcher_background.xml').read_text(),
 '35 GAs y dos solo eventos': 'EXPECTED_READS = 35' in coord and 'setOf("15/0/13", "15/0/15")' in coord,
 'Motor H 160 ms': 'val minGap = 160L' in engine,
 'Sin mas de una retransmision de transporte': 'if (p.retransmits == 0)' in engine and 'STOP_NO_ACK' in engine,
 'Ruta Wifi anclada': 'wifiRoute.network.bindSocket(udp)' in coord,
 'Guardia ruta sin segunda conexion': 'routeHealthCheck = {' in coord and 'event("WIFI_ROUTE_LOST"' in engine,
 'Fallo UDP registra fase': 'H_UDP_SEND_FAILURE' in coord and 'event("UDP_SEND_FAILURE"' in engine,
 'No importar cache tras fallo': 'val imported = if (accepted)' in coord and 'val screenLoadMs = if (success)' in coord,
 'Cooldown manual tras UDP': 'udpErrorCooldownUntilMs' in coord and '30_000L' in coord,
 'Motor antiguo y automatizaciones bloqueados': 'const val READ_ONLY: Boolean = true' in coord and manifest.count('android:enabled="false"') >= 4,
 'Solo luces de Cocina': 'consumedRunId' in light and '"1/1/6", "1/2/6"' in runner and '"1/1/5", "1/2/5"' in runner,
 'Sin lluvia/viento activamente leidos': 'GaStatus.MONITOR_ONLY' in engine,
 'Csv de auditoria h0822': 'h0822_cache_actualizada' in coord and 'h0822_retransmisiones_estrictas' in coord,
 'LEEME Entrega34': (r/'LEEME_H08_2_2_ENTREGA34_UDP_Recuperacion_Segura.txt').exists(),
}
for label,ok in checks.items():print(('PASS' if ok else 'FAIL')+' '+label)
assert all(checks.values())

#!/usr/bin/env python3
"""Auditoría estática H08.2; NO equivale a compilar Android ni a probar KNX físico."""
from pathlib import Path
import hashlib,zipfile
p = Path(__file__).resolve().parent.parent
j = p/'app/src/main/java/com/onehouse/app'
co = (j/'feature/h08/H08Coordinator.kt').read_text()
control = (j/'feature/h08/H082LightControlCoordinator.kt').read_text()
room = (j/'feature/rooms/detail/RoomDetailScreen.kt').read_text()
ui=(j/'feature/h08/H08Toolbar.kt').read_text()
gradle=(p/'app/build.gradle.kts').read_text()
manifest=(p/'app/src/main/AndroidManifest.xml').read_text()
runner=(p/'app/src/main/java/com/onehouse/knxlab/knx/H07LightControlRunner.kt').read_text()
checks = {
    'App experimental no sobrescribe estable': 'applicationId = "com.onehouse.app.h08"' in gradle,
    'Version correcta': 'versionCode = 20802' in gradle,
    'Nombre launcher TEST': 'OneHouse H08.2 TEST' in (p/'app/src/main/res/values/strings.xml').read_text(),
    'Icono adaptativo naranja': '#F47714' in (p/'app/src/main/res/drawable/ic_launcher_background.xml').read_text(),
    'Lector antiguo permanentemente bloqueado': 'const val READ_ONLY: Boolean = true' in co,
    'Servicio de clima y tareas deshabilitado': manifest.count('android:enabled="false"') >= 4,
    'No polling antiguo al crear repositorio': 'if (!H08Safety.READ_ONLY) PeriodicKnxStateRefresh.ensureStarted' in (j/'knx/KnxHomeStateRepository.kt').read_text(),
    'Direcciones eventos excluidas': 'setOf("15/0/13", "15/0/15")' in co,
    '35 direcciones y mismo runner H': 'UnifiedReceiverRunner()' in co and 'EXPECTED_READS = 35' in co,
    'Bloqueo durante escritura': 'H082LightControlCoordinator.isBusy()' in co,
    'Lista blanca dos luces': 'enum class Light' in runner and '"1/1/6", "1/2/6"' in runner and '"1/1/5", "1/2/5"' in runner,
    'Una sola orden por lectura': 'control.consumedRunId' in control and 'consumedRunId = read.lastRunId' in control,
    'Solo estados recibidos y misma GA': 'light.statusGa' in control and 'light.commandGa' in control,
    'Sin comandos si no WiFi local': 'H08Coordinator.wifiOnly(app)' in control,
    'Confirmacion explicita en pantalla real Cocina': 'confirmPilotLight' in room and 'AlertDialog(' in room and 'RoomLightCard(' in room,
    'Mandos restantes deshabilitados': 'interactive = !H08Safety.READ_ONLY' in room,
    'CSV de control exportable': 'H082LightControlCoordinator.exportControlCsv()' in ui,
    'Sin reintento write H07': 'val frame = KnxProtocol.tunnelWriteBooleanRequest' in runner and 'writeCount = 1' in runner,
    'LEEME en entrega': (p/'LEEME_H08_2_ENTREGA32_Controles_Cocina_TEST.txt').exists(),
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL')+' - '+label)
assert all(checks.values()), 'Falla una comprobacion H08.2'

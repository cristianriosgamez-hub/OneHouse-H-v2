#!/usr/bin/env python3
"""Auditoría estática H08.1. No sustituye el build Android ni una prueba real."""
from pathlib import Path
import hashlib
root = Path(__file__).resolve().parent.parent
p = root / 'app/src/main/java/com/onehouse/app'
coord = (p/'feature/h08/H08Coordinator.kt').read_text()
ui = (p/'feature/h08/H08Toolbar.kt').read_text()
gradle = (root/'app/build.gradle.kts').read_text()
checks = {
    'Motor H intacto': hashlib.sha256((root/'app/src/main/java/com/onehouse/knxlab/knx/UnifiedReceiverRunner.kt').read_bytes()).hexdigest() == 'f649130f2318326a8f11833757047657c7443df3986a178e5e3e1a344999c024',
    'ID independiente de OneHouse estable': 'applicationId = "com.onehouse.app.h08"' in gradle,
    'Version actualizada': 'versionCode = 20801' in gradle and '2.0.0-H08.1-Integracion' in gradle,
    'Una lectura tiene ID': 'lastRunId' in coord and 'H081-$startedAt-$sequence' in coord,
    'Exportacion registra ID': 'appendLine("lectura_id;' in coord,
    'Exportacion diferencia repeticion': 'REEXPORTACION_MISMA_LECTURA' in coord,
    'CSV no depende de success mutable': 'if (s.lastRunSuccess)' in coord,
    'IP cambiada invalida ultima lectura': 'if (endpointChanged)' in coord and 'mutableStatus.value = H08Status(' in coord,
    'Misma IP conserva resultado': 'val previous = status.value' in coord and 'mutableStatus.value = previous.copy(' in coord,
    'Pantalla informa ID': 'ID ${state.lastRunId}' in ui,
    'Bloqueo de comandos': 'READ_ONLY: Boolean = true' in coord,
    'GAs de eventos excluidas': '15/0/13", "15/0/15' in coord,
    'Motor antiguo bloqueado': 'if (!H08Safety.READ_ONLY) PeriodicKnxStateRefresh.ensureStarted' in (p/'knx/KnxHomeStateRepository.kt').read_text(),
    'Servicio de control deshabilitado': (root/'app/src/main/AndroidManifest.xml').read_text().count('android:enabled="false"') >= 4,
}
for name,passed in checks.items(): print(('PASS' if passed else 'FAIL')+' - '+name)
if not all(checks.values()): raise SystemExit(1)

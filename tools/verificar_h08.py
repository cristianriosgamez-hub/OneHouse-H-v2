#!/usr/bin/env python3
# Auditoría estática, no reemplaza la compilación Android ni la prueba física.
from pathlib import Path
import hashlib
root = Path(__file__).resolve().parent.parent
p = root/'app/src/main/java/com/onehouse/app'
reader = root/'app/src/main/java/com/onehouse/knxlab/knx/UnifiedReceiverRunner.kt'
checks = {
    'Motor H intacto': hashlib.sha256(reader.read_bytes()).hexdigest() == "f649130f2318326a8f11833757047657c7443df3986a178e5e3e1a344999c024",
    'ID independiente H08': 'applicationId = "com.onehouse.app.h08"' in (root/'app/build.gradle.kts').read_text(),
    'Refresco antiguo cerrado': 'if (!H08Safety.READ_ONLY) PeriodicKnxStateRefresh.ensureStarted' in (p/'knx/KnxHomeStateRepository.kt').read_text(),
    'Backend KNX antiguo bloqueado': 'if (H08Safety.READ_ONLY)' in (p/'knx/KnxConnectionManager.kt').read_text(),
    'Comandos bloqueados': 'if (H08Safety.READ_ONLY)' in (p/'knx/KnxCommandExecutor.kt').read_text(),
    'H08 usa cache real': 'HResultBridge.mergeReadOnly(run, repository)' in (p/'feature/h08/H08Coordinator.kt').read_text(),
    'Sensores evento excluidos': '15/0/13", "15/0/15' in (p/'feature/h08/H08Coordinator.kt').read_text(),
    'Servicios desactivados': (root/'app/src/main/AndroidManifest.xml').read_text().count('android:enabled="false"') >= 4,
    'Motor lector unico': (root/'app/src/main/java/com/onehouse/knxlab/knx/UnifiedReceiverRunner.kt').exists(),
    'Controles H08 solo lectura': 'READ_ONLY: Boolean = true' in (p/'feature/h08/H08Coordinator.kt').read_text(),
}
for name,passed in checks.items():print(('PASS' if passed else 'FAIL')+' - '+name)
if not all(checks.values()):raise SystemExit(1)

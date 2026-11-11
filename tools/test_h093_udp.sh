#!/usr/bin/env bash
# Run from the repository root. Uses kotlinc, or the compiler bundled in Gradle 9.5.0.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"
sim_dir="$(mktemp -d)"
trap 'rm -rf "$sim_dir"' EXIT
sources=(app/src/main/java/com/onehouse/knxlab/knx/{KnxProtocol,H074ClimateCodec,H077ClimateFanCodec,H091PassiveMonitorRunner}.kt tools/test_h091_gateway_simulado.kt)
if command -v kotlinc >/dev/null; then
    kotlinc "${sources[@]}" -include-runtime -d "$sim_dir/sim.jar"
    java -jar "$sim_dir/sim.jar"
else
    : "${H093_GRADLE_HOME:?Set H093_GRADLE_HOME to an extracted Gradle 9.5.0 distribution, or install kotlinc}"
    java -cp "$H093_GRADLE_HOME/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        -no-stdlib -no-reflect -classpath "$H093_GRADLE_HOME/lib/kotlin-stdlib-2.3.20.jar" \
        "${sources[@]}" -d "$sim_dir/sim.jar"
    java -cp "$sim_dir/sim.jar:$H093_GRADLE_HOME/lib/*" com.onehouse.knxlab.knx.Test_h091_gateway_simuladoKt
fi

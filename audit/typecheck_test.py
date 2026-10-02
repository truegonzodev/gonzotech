#!/usr/bin/env python3
"""
Локальный типчек (0.3.93): ECJ-компиляция сетевого пакета против MC-стабов.

Предыстория: 0.3.90/0.3.92 упали в сборке АВТОРА на внутренних несоответствиях
типов (старая расстановка аргументов addMachineLane; Level vs ServerLevel в
PipeFlowWarnings.mark) — регэксп-пины типы не проверяют, а полной компиляции
в песочнице нет. Гейт теперь: PipeRouting + PipeFlowWarnings + PipeType +
PipeMode + PipeLoss + PipeCarrier компилируются целиком против стабов
(audit/stubs) с сигнатурами, сверенными реальной сборкой 1.21.4. Любое
расхождение типов внутри пакета — FAIL ЗДЕСЬ, а не у автора.

Границы: стабы проверяют ВНУТРЕННЮЮ консистентность пакета; соответствие
стабов реальному MC-API гарантируется только сверкой с реальным кодом
(сборка автора — финальный гейт). Не расширять стабы «по памяти».
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STUBS = ROOT / "audit" / "stubs"
NET = ROOT / "src/main/java/com/gonzotech/machines/network"

JAVA_HOME = os.environ.get("JAVA_HOME")
java = str(Path(JAVA_HOME) / "bin/java") if JAVA_HOME else shutil.which("java")
javac = str(Path(JAVA_HOME) / "bin/javac") if JAVA_HOME else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")

real_files = [
    NET / "PipeRouting.java",
    NET / "PipeFlowWarnings.java",
    NET / "PipeType.java",
    NET / "PipeMode.java",
    NET / "PipeLoss.java",
    NET / "PipeCarrier.java",
    NET / "PipeFlowLedger.java",
    NET / "FlowTracker.java",
    ROOT / "src/main/java/com/gonzotech/core/registry/ModParticles.java",
    NET / "ItemRouting.java",
    NET / "ItemFlowTracker.java",
]
for f in real_files:
    assert f.is_file(), f"нет файла сетевого пакета: {f}"

stub_files = sorted(STUBS.rglob("*.java"))
assert len(stub_files) >= 30, f"стабов стало меньше: {len(stub_files)}"
# ключевые стабы на месте (чтобы гейт не выродился тихо)
for must in (
    STUBS / "net/minecraft/world/level/Level.java",
    STUBS / "net/minecraft/server/level/ServerLevel.java",
    STUBS / "net/neoforged/neoforge/event/tick/LevelTickEvent.java",
    STUBS / "net/neoforged/neoforge/registries/DeferredRegister.java",
):
    assert must.is_file(), f"нет ключевого стаба: {must}"

sources = [str(p) for p in real_files] + [str(p) for p in stub_files]

with tempfile.TemporaryDirectory(prefix="gonzotech-typecheck-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none", "-nowarn"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21", "-nowarn"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    proc = subprocess.run(compiler + ["-d", output] + sources,
                          capture_output=True, text=True)
    if proc.returncode != 0:
        print(proc.stdout)
        print(proc.stderr)
        raise SystemExit("TYPECHECK FAILED: сетевой пакет не компилируется против стабов")

# ── Пины двух регрессий, которые этот гейт ловит (0.3.93) ──
routing = (NET / "PipeRouting.java").read_text()
warnings = (NET / "PipeFlowWarnings.java").read_text()
# 1) старая до-рефакторочная расстановка аргументов addMachineLane не вернулась
assert "addMachineLane(level, next, port," not in routing
# 2) mark принимает Level (distributeLanes работает с Level, не ServerLevel)
assert "public static void mark(Level level, BlockPos pos, long untilTick)" in warnings

print(f"typecheck ok: {len(real_files)} реальных файлов + {len(stub_files)} стабов, 0 ошибок")

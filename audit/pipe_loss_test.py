#!/usr/bin/env python3
"""Per-block GTU/GTH losses (0.3.58, author's numbers): pure math + wiring pins.

Compiles the dependency-free PipeLoss class plus a scenario harness (author's
battery example) and pins the routing wiring strings. Needs JDK 21 in
JAVA_HOME/PATH or a Java 21 runtime + ECJ_JAR.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
JAVA_HOME = os.environ.get("JAVA_HOME")
java = str(Path(JAVA_HOME) / "bin/java") if JAVA_HOME else shutil.which("java")
javac = str(Path(JAVA_HOME) / "bin/javac") if JAVA_HOME else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")
loss_src = ROOT / "src/main/java/com/gonzotech/machines/network/PipeLoss.java"
sources = [loss_src, ROOT / "audit/PipeLossSelfTest.java"]
with tempfile.TemporaryDirectory(prefix="gonzotech-pipe-loss-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "PipeLossSelfTest"], check=True)

# ── Пины продакшен-проводки (PipeRouting) ──
routing = (ROOT / "src/main/java/com/gonzotech/machines/network/PipeRouting.java").read_text()
for pin in (
    "private static Transfer.Receiver recording(Level level, Transfer.Receiver real, PipeType type, List<PathStep> path,\n"
    "                                               long[] lossCells) {",
    "long delivered = PipeLoss.delivered(amount, lossMilli);",
    "long flow = PipeLoss.flow(accepted, lossMilli);",
    "private static long pathLoss(Level level, List<PathStep> path, PipeType type) {",
    "if (st.getBlock() instanceof UniversalNodeBlock) continue;",
    "perCell[i] = PipeLoss.perCell(st.getBlock() instanceof SecondTierPipe, type == PipeType.HEAT);",
    "lanes.add(new Lane(raw, null, 0));",
    "long[] lossCells = pathLossCells(level, path, type);",
    "return PipeLoss.sum(pathLossCells(level, path, type));",
    "recording(level, raw, type, path, lossCells)",
):
    assert pin in routing, "routing pin: " + pin.splitlines()[0]
# все четыре вида дорожек считают потерю; прямые соседи — без потерь
assert routing.count("pathLossCells(level, path,") == 5, routing.count("pathLossCells(level, path,")
# числа автора — единственный источник констант
for const in ("WIRE_T1 = 80;", "WIRE_T2 = 90;", "HEAT_T1 = 220;", "HEAT_T2 = 180;"):
    assert const in loss_src.read_text(), const
# 0.3.73: тик-барьер FlowTracker чистит ОБЕ карты в обоих методах записи —
# иначе потери «(+N)» на HUD ключа накапливаются через тики и растут вечно.
tracker = (ROOT / "src/main/java/com/gonzotech/machines/network/FlowTracker.java").read_text()
assert tracker.count("h.loss.clear();") == 2, tracker.count("h.loss.clear();")
assert tracker.count("h.tick = t;") == 2
hud = (ROOT / "src/main/java/com/gonzotech/machines/client/WrenchHud.java").read_text()
# Простые единицы вместо «К»-тиров: 0.44 вместо «0.4К», «33.3» вместо «33.3К».
assert '"%.2f", milli / 1000.0' in hud
assert '"%.1f", total / 1000.0' in hud
assert "GtFormat.formatRate" not in hud
# Пробел между «GTH/т» и хвостом потерь.
assert 'Component.literal(" (+" + lossText(e.lossMilli) + ")")' in hud
# 0.3.74: «(+N)» — кумулятив РАСТЁТ вдоль потока (источник→приёмник) и не
# умножается на число дорожек/величину потока.
assert "java.util.Collections.reverse(steps);" in routing
assert "next = cur;" in routing and "cur = parent.get(cur.asLong());" in routing
assert "+= lossMilli" not in tracker
assert "if (lossMilli > byType[type.ordinal()]) byType[type.ordinal()] = lossMilli;" in tracker
print("Pipe losses wiring passed (0.08/0.09 GTU, 0.22/0.18 GTH; universal node exempt)")

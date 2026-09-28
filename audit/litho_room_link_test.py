#!/usr/bin/env python3
"""Litho machine <-> room ledger link: tooltip, ledger and reject roll agree.

Compiles the actual pure-Java clean-room core plus a scenario harness that
replicates CleanRoomSystem ticks (filter improve, player dirt dump), the
machine's room read and the tooltip/reject math. Needs JDK 21 (JAVA_HOME/PATH)
or a Java 21 runtime + ECJ_JAR.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
JAVA_HOME = os.environ.get("JAVA_HOME")
java = str(Path(JAVA_HOME) / "bin/java") if JAVA_HOME else shutil.which("java")
javac = str(Path(JAVA_HOME) / "bin/javac") if JAVA_HOME else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")
src = ROOT / "src/main/java/com/gonzotech/cleanroom"
sources = [src / (name + ".java") for name in ("RoomTopology", "RoomLedger")]
sources.append(ROOT / "audit/LithoRoomLinkSelfTest.java")
with tempfile.TemporaryDirectory(prefix="gonzotech-litho-room-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "LithoRoomLinkSelfTest"], check=True)

# ── Строковые пины: симуляция не должна разойтись с продакшеном ──
system_code = re.sub(r"/\*.*?\*/|//[^\n]*", "", (src / "CleanRoomSystem.java").read_text(), flags=re.S)
filter_be = re.sub(r"/\*.*?\*/|//[^\n]*", "", (src / "AirFilterBlockEntity.java").read_text(), flags=re.S)
litho_be = re.sub(r"/\*.*?\*/|//[^\n]*", "",
                  (ROOT / "src/main/java/com/gonzotech/machines/litho/SiliconFactoryBlockEntity.java").read_text(),
                  flags=re.S)
litho_screen = re.sub(r"/\*.*?\*/|//[^\n]*", "",
                      (ROOT / "src/main/java/com/gonzotech/machines/client/SiliconFactoryScreen.java").read_text(),
                      flags=re.S)

# Единственный источник роста качества — фильтр; единственный слив — игрок.
assert "CleanRoomSystem.improve(server, be.room, FilterCycle.QUALITY_PER_TICK);" in filter_be
assert "data(level).ledger.adjust(room, -(before - dirt.dirt()) * 3.0);" in system_code
assert "dirt.setDirt(dirt.dirt() - 11.5);" in system_code
assert "dirt.setDirt(dirt.dirt() + 5.0);" in system_code
# Машина и фильтр резолвят одну и ту же комнату (одни соседи-INTERIOR).
assert "be.room = CleanRoomSystem.filterRoom(server, pos);" in litho_be
assert "be.qualityHundredths = be.room == null ? -1 : (int) Math.round(be.room.quality() * 100);" in litho_be
# Тултип и бросок брака используют одни сотые (/100 ровно один раз).
assert "nextDouble() * 100.0 < rejectPercent(be.qualityHundredths / 100.0)" in litho_be
assert "rejectPercent(quality / 100.0)" in litho_screen
assert "Math.round(quality / 100f)" in litho_screen
# Формула брака не изменилась без ведома автора (опорные точки 0.3.42).
for line in ("if (qualityPercent < 0) return 26.0;",
             "if (qualityPercent >= 100) return 1.0;",
             "if (qualityPercent >= 50) return 9.0 + (qualityPercent - 50) * (1.0 - 9.0) / 50.0;",
             "if (qualityPercent >= 10) return 24.0 + (qualityPercent - 10) * (9.0 - 24.0) / 40.0;",
             "return 36.0 + qualityPercent * (24.0 - 36.0) / 10.0;"):
    assert line in litho_be, "reject anchor: " + line
# Зажим чистоты игрока в реплике совпадает с Cleanliness (0..100).
cleanliness = (src / "Cleanliness.java").read_text()
assert "Math.max(0.0, Math.min(100.0, value))" in cleanliness
assert "Math.max(0.0, Math.min(100.0, v))" in (ROOT / "audit/LithoRoomLinkSelfTest.java").read_text()
print("Machine room link passed (tooltip = ledger = reject roll; dirt dump pinned)")

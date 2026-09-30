#!/usr/bin/env python3
"""Open hermetic-door physics (0.3.61): outside drain -8%/s, cross-room
equalization conserving the sum, no-ops. Compiles the pure core
(RoomTopology, RoomLedger, DoorLeaks) + scenario harness."""
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
sources = [src / (n + ".java") for n in ("RoomTopology", "RoomLedger", "DoorLeaks")]
sources.append(ROOT / "audit/DoorLeaksSelfTest.java")
with tempfile.TemporaryDirectory(prefix="gonzotech-door-leak-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "DoorLeaksSelfTest"], check=True)

# ── Пины: продакшен использует чистый класс; числа автора; топология не рвётся ──
detector = (src / "CleanRoomDetector.java").read_text()
assert "public static boolean isOpenHermeticDoor(BlockState state)" in detector
assert "state.is(SEALS)" in detector and "HeavyDoorBlock.OPEN" in detector
system = (src / "CleanRoomSystem.java").read_text()
system_code = re.sub(r"/\*.*?\*/|//[^\n]*", "", system, flags=re.S)
assert "processDoorLeaks(level);" in system_code
assert "DoorLeaks.settle(ledger, doors);" in system_code
assert "doors.add(new DoorLeaks.Door(first, second));   // дверь между двумя контурами" in system
loss = (src / "DoorLeaks.java").read_text()
assert "OUTSIDE_LOSS_PER_SECOND = 8.0;" in loss
assert "EQUALIZE_PER_SECOND = 0.08;" in loss
print("Door leak wiring passed (-8%/s outside, equalize 0.08, topology intact)")

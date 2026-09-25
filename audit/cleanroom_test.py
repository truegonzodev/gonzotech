#!/usr/bin/env python3
"""Compile/run the actual pure-Java clean-room core, without Gradle/Minecraft.

Needs JDK 21 (JAVA_HOME or PATH). Alternatively a Java 21 runtime + ECJ_JAR.
Generated classes are temporary and never enter Git.
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
src = ROOT / "src/main/java/com/gonzotech/cleanroom"
sources = [src / (name + ".java") for name in ("RoomTopology", "RoomLedger", "FilterCycle", "CleanerPulse")]
sources.append(ROOT / "audit/CleanRoomSelfTest.java")
with tempfile.TemporaryDirectory(prefix="gonzotech-cleanroom-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "CleanRoomSelfTest"], check=True)

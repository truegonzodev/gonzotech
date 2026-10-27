#!/usr/bin/env python3
"""Compile the matrix, loaded metrics, ReaCalc 1.7 dynamics, and Java oracles."""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/com/gonzotech/machines/reactor/MatrixCoefficients.java"
OPERATING = ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorOperatingMetrics.java"
TEST = ROOT / "audit/MatrixCoefficientsTest.java"
OPERATING_TEST = ROOT / "audit/ReactorOperatingMetricsTest.java"
HEAT_CURVE = ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorHeatOutputCurve.java"
HEAT_DYNAMICS = ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorHeatDynamics.java"
NEUTRONS = ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorNeutronMetrics.java"
DYNAMICS_TEST = ROOT / "audit/ReactorHeatDynamicsTest.java"

home = os.environ.get("JAVA_HOME")
java = str(Path(home) / "bin/java") if home else shutil.which("java")
javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")
if os.environ.get("ECJ_JAR"):
    compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
elif javac and Path(javac).is_file():
    compiler = [javac, "--release", "21"]
else:
    raise SystemExit("javac or ECJ_JAR required")

with tempfile.TemporaryDirectory(prefix="gonzotech-reactor-matrix-") as out:
    compile_result = subprocess.run(
        compiler + ["-encoding", "UTF-8", "-d", out, str(MAIN), str(OPERATING),
         str(HEAT_CURVE), str(HEAT_DYNAMICS), str(NEUTRONS), str(TEST),
         str(OPERATING_TEST), str(DYNAMICS_TEST)],
        text=True, capture_output=True,
    )
    if compile_result.returncode:
        sys.stderr.write(compile_result.stderr)
        sys.stdout.write(compile_result.stdout)
        raise SystemExit(compile_result.returncode)

    result = subprocess.run(
        [java, "-cp", out, "com.gonzotech.machines.reactor.MatrixCoefficientsTest"],
        text=True, capture_output=True,
    )
    sys.stdout.write(result.stdout)
    if result.returncode:
        sys.stderr.write(result.stderr)
        raise SystemExit(result.returncode)

    assert "PASS:" in result.stdout, "Java oracle did not report a passing assertion count"
    print("reactor coefficient engine and superposition tests passed")

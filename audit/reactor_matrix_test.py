#!/usr/bin/env python3
"""Compile the matrix, loaded metrics, ReaCalc 1.7 dynamics, and Java oracles."""
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

javac = shutil.which("javac")
java = shutil.which("java")
if not javac or not java:
    print("Java 21 required: java/javac not found", file=sys.stderr)
    raise SystemExit(1)

with tempfile.TemporaryDirectory(prefix="gonzotech-reactor-matrix-") as out:
    compile_result = subprocess.run(
        [javac, "-encoding", "UTF-8", "-d", out, str(MAIN), str(OPERATING),
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

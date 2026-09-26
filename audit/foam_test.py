#!/usr/bin/env python3
"""Compile/run the actual pure-Java fluid-foam core, without Gradle/Minecraft.

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
src = ROOT / "src/main/java/com/gonzotech/core/fluid"
sources = [src / (name + ".java") for name in ("FoamSurface", "FoamPopulation")]
sources.append(ROOT / "audit/FoamSelfTest.java")
with tempfile.TemporaryDirectory(prefix="gonzotech-foam-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "FoamSelfTest"], check=True)


# Static wiring checks are not a substitute for compiling/running Minecraft.
client = (ROOT / 'src/main/java/com/gonzotech/core/fluid/client/FluidFoamClient.java').read_text()
block = (ROOT / 'src/main/java/com/gonzotech/core/fluid/ModFluidBlock.java').read_text()
assert 'value = Dist.CLIENT' in client and 'ClientTickEvent.Post' in client
assert 'public void animateTick' not in block and 'DustParticleOptions' not in block
assert 'mc.isPaused()' in client and 'world != mc.level' in client
assert 'case DECREASED -> 0.5' in client and 'case MINIMAL -> 0.0' in client
assert client.index('!world.hasChunkAt(pos)') < client.index('world.getBlockState(pos)')
assert 'roof.isFaceSturdy(world, above, Direction.DOWN)' in client
assert 'surfaceKind(cell) != kind' in client and '!cell.near(observer)' in client
assert 'getHeight(world, pos)' in client and 'RANDOM.nextDouble() * 0.05' in client
assert 'KINDS[kind].particleColor' in client and 'x, y, z, 0.0, 0.015, 0.0' in client
assert 'particleEngine.createParticle' in client and 'particle.isAlive()' in client and 'particle.getLifetime()' in client
assert '.setLifetime(' not in client and '.setParticleSpeed(' not in client
for colour in ('0xB4E58E', '0x8AFFE9', '0x8374A6', '0xB84A28', '0xFFFFFF', '0x8BD3FC'):
    assert colour in block
print('Foam client/server isolation, colour, height, vanilla physics and settings checks passed (static)')

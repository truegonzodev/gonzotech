#!/usr/bin/env python3
"""Compile/run the actual pure-Java radiation-contour core, without Gradle/Minecraft.

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
src = ROOT / "src/main/java/com/gonzotech/radiation"
sources = [src / (name + ".java") for name in ("RadiationContour",)]
sources.append(ROOT / "audit/RadiationContourSelfTest.java")
with tempfile.TemporaryDirectory(prefix="gonzotech-radiation-") as output:
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "RadiationContourSelfTest"], check=True)

# Static integration checks complement the compiled core, not a Minecraft runtime test.
src = ROOT / "src/main/java/com/gonzotech"
containment = (src / "radiation/Containment.java").read_text()
system = (src / "radiation/RadiationSystem.java").read_text()
hook = (src / "mixin/LevelChunkCleanRoomMixin.java").read_text()
chunk = (src / "radiation/ChunkRadiationData.java").read_text()
assert "Map<ServerLevel, Map<Long, Entry>>" in containment and "WeakHashMap" in containment
assert containment.index("if (!level.hasChunkAt(block))") < containment.index("return cell(level.getBlockState(block))")
assert "onChunkUnload" in containment and "onChunkLoad" in containment and "onLevelUnload" in containment
assert "Containment.blockChanged(level, pos, old, actual)" in hook
assert "state.getValue(BlockStateProperties.OPEN)" in containment and "instanceof net.minecraft.world.level.block.TrapDoorBlock" in containment
assert "DEPENDENCY_RANGE" in containment and "cache.entrySet().removeIf" in containment
assert "for (var pos : result.interior())" in containment
assert "Containment.cachedResult(level, pos)" in chunk and "probed++ < MAX_PROBES_PER_CHUNK" in chunk
assert system.index("+ Containment.insideDose(level, BlockPos.containing(player.getEyePosition()))") < system.index("rawDose *= PsycheChemical.doseMultiplier(player)") < system.index("Hazmat.factor(player, rawDose)")
print("Radiation cache/lifecycle/dose integration checks passed: 9 (static)")

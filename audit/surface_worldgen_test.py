#!/usr/bin/env python3
"""Static contract checks for the all-biome surface ruin and waste generators."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"


def read(path: str) -> str:
    return (JAVA / path).read_text(encoding="utf-8")


failures: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


mixin = read("mixin/ChunkDecorationDesolationMixin.java")
helper = read("core/worldgen/DesolationWorldgenSupport.java")
ruins = read("core/worldgen/SurfaceRuinGenerator.java")
waste = read("core/worldgen/SurfaceWasteGenerator.java")
conversion = read("core/worldgen/DesolationConversionFeature.java")
radiation = read("radiation/ChunkRadiationData.java")

# Runtime integration occurs after normal biome decoration, for each chunk.
check('method = "applyBiomeDecoration", at = @At("RETURN")' in mixin,
      "surface generators are not called after biome decoration")
check("SurfaceRuinGenerator.generateForChunk" in mixin and "SurfaceWasteGenerator.generateForChunk" in mixin,
      "one or both surface generators are not wired into the chunk hook")
check("AtomicBoolean" in mixin and "LOGGER.error(message, error)" in mixin,
      "worldgen failure logging must include a stack trace and be bounded")

# Surface filter rejects water, ocean/river biomes, and finds natural ground from
# the world-surface heightmap rather than scanning a cave-floor feature position.
for token in ("WORLD_SURFACE_WG", "BiomeTags.IS_OCEAN", "BiomeTags.IS_RIVER",
              "getFluidState().isEmpty()", "isNaturalTerrain(state)"):
    check(token in helper, f"surface/water/cave guard missing {token}")
check("convertColumnWithoutBiome" in conversion and "requireDesolationBiome" in conversion,
      "shared desolation conversion lacks a no-biome entry point")
check("boolean surfaceOnly" in conversion and "enteredSolidSurface" in conversion
      and "!surfaceOnly &&" in conversion,
      "waste conversion can flow into caves or alter water")
check("clearSurfaceVegetation" in helper and "state.canBeReplaced()" in helper
      and "level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)" in helper,
      "replaceable grass/flowers are not removed above converted surface blocks")
check("clearSurfaceVegetation" in conversion and "setSurfaceBlock" in ruins
      and "clearSurfaceVegetation" in waste,
      "vegetation cleanup is not wired into conversion, ruins and barrel placement")
check("convertColumnWithoutBiome(" in waste,
      "waste patch does not reuse the desolation block-conversion routine")
check("ChunkAccess chunk = level.getChunk(chunkX, chunkZ)" in helper
      and "chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))" in helper
      and "if (!level.hasChunk(chunkX, chunkZ))" in helper,
      "surface biome lookup must stay within the position's available chunk")
check("level.getBiome" not in helper,
      "surface helper still uses fuzzy biome sampling that can cross worldgen-region bounds")

# Ruins: requested base rate, Desolation multiplier, dimensions, vein palette,
# sunk machinery/plastic bowl, buried silicon-cache layer, and detached fragments.
for token in ("1.0 / 444.0", "desolation ? 2.0 : 1.0", "1.5 + random.nextDouble() * 2.0",
              "6.0 + random.nextDouble() * 10.0", "5.0 + random.nextDouble() * 5.0",
              "ModBlocks.SILICON_CACHE", "ModBlocks.WEATHERED_MECHANISMS",
              "ModBlocks.PLASTIC_WASTE", "floorY = groundY - depth",
              "markerY = floorY - depth - 1", "createScatter",
              "DesolationWorldgenSupport.noise", "coreRadius * 0.72", "< 0.32"):
    check(token in ruins, f"ruin contract missing {token}")
for source, name in ((ruins, "ruins"), (waste, "waste")):
    check("level.hasChunk(anchorX, anchorZ)" in source,
          f"{name} generator can request a chunk outside WorldGenRegion dependencies")
    check("DesolationWorldgenSupport.isBiomeAt(" in source and "level.getBiome" not in source,
          f"{name} candidate must use bounded, chunk-local biome sampling")
    candidate = source.split("private static void generateCandidate", 1)[1]
    check(candidate.index("double chanceRoll") < candidate.index("findGroundY"),
          f"{name} candidate reads terrain before its maximum-rate fast rejection")

baseline = (ROOT / "docs/WORLDGEN-SURFACE-RUINS.md").read_text(encoding="utf-8")
check("bb4dea7161f79ce3eb2a4d845a1fccaa721a9cfc" in baseline
      and "int depth = 1 + (int) Math.floor(2.0" in baseline
      and "1 / 444" in baseline,
      "tested crater-style ruins fallback is not documented with its source commit and parameters")
for block in ("MECHANISMS", "WEATHERED_PLATING", "DEBRIS", "WEATHERED_DEBRIS",
              "WEATHERED_MECHANISMS", "PLASTIC_WASTE"):
    check(f"ModBlocks.{block}" in ruins, f"ruin palette missing {block}")

# Waste sites: slightly higher base rate, ×12 in Desolation, 1–10 barrels in a
# 1–4-wide patch, 4–16-block conversion radius, and 1–5% debris chance.
for token in ("3.0 / 1024.0", "desolation ? 12.0 : 1.0", "1 + random.nextInt(10)",
              "1 + random.nextInt(4)", "2 + random.nextInt(3)",
              "4 + random.nextInt(13)", "0.01 + random.nextDouble() * 0.04",
              "ModBlocks.WASTE_BARREL", "ModBlocks.DEBRIS", "ModBlocks.WEATHERED_DEBRIS"):
    check(token in waste, f"waste contract missing {token}")

# Waste barrels yield a cached, stable 1–10 mZt/t chunk baseline.
check("ModBlocks.WASTE_BARREL.get()" in radiation and "chunk.getBlockState(mp).is" in radiation,
      "baseline scan does not detect waste barrels")
check("(1.0 + rnd.nextDouble() * 9.0) * RadUnits.MILLI" in radiation,
      "waste-bearing chunk baseline is not 1–10 mZt/t")

version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
check("mod_version=0.3.241" in version, "content-change version was not bumped")

if failures:
    for failure in failures:
        print(f"FAIL: {failure}")
    raise SystemExit(1)
print("Surface worldgen checks passed")

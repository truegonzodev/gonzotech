#!/usr/bin/env python3
"""Regression pins for vanilla and custom snowfall support checks."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
MIXIN = ROOT / "src/main/java/com/gonzotech/mixin/ServerLevelSunEventSnowMixin.java"
source = MIXIN.read_text(encoding="utf-8")
handler = source.split("private void gonzotech$sunEventProtectGround", 1)[1].split("\n    }", 1)[0]

checks = {
    "precipitation still requires rain": "if (!level.isRaining())" in handler,
    "snow-window handling remains gated": "if (!SunEventServer.snowWindowDay(level))" in handler,
    "blocked snow supports are checked before the custom snow-window gate":
        "BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON" in handler
        and handler.index("BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON")
        < handler.index("if (!SunEventServer.snowWindowDay(level))"),
    "only air or an existing snow layer may occupy the target":
        "!surfaceState.isAir() && !surfaceState.is(Blocks.SNOW)" in handler,
    "the block directly below the target is the support":
        "BlockPos supportPos = surface.below();" in handler
        and "BlockState supportState = level.getBlockState(supportPos);" in handler,
    "liquid supports remain rejected":
        "!supportState.getFluidState().isEmpty()" in handler,
    "support must have a full collision shape during the custom snow event":
        "!supportState.isCollisionShapeFullBlock(level, supportPos)" in handler,
}

family = {
    "gonzotech:noble_moss",
    "gonzotech:smooth_noble_moss",
    "gonzotech:polished_noble_moss",
    "gonzotech:chiseled_noble_moss",
    "gonzotech:noble_moss_slab",
    "gonzotech:noble_moss_stairs",
    "gonzotech:noble_moss_pillar",
}
tag_path = RES / "data/minecraft/tags/block/snow_layer_cannot_survive_on.json"
snow_tag = json.loads(tag_path.read_text(encoding="utf-8"))
checks["vanilla snow-survival tag blocks the entire noble-moss family"] = family <= set(snow_tag["values"])

for label, passed in checks.items():
    assert passed, f"FAIL: {label}"
    print(f"PASS: {label}")

print(f"Snowfall support regression pins passed: {len(checks)}")

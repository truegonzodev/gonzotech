#!/usr/bin/env python3
"""Regression pins for artificial snow placement during the sun-event window."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIXIN = ROOT / "src/main/java/com/gonzotech/mixin/ServerLevelSunEventSnowMixin.java"
source = MIXIN.read_text(encoding="utf-8")
handler = source.split("private void gonzotech$sunEventProtectGround", 1)[1].split("\n    }", 1)[0]

checks = {
    "snow-window and rain gates remain":
        "!SunEventServer.snowWindowDay(level) || !level.isRaining()" in handler,
    "only air or an existing snow layer may occupy the target":
        "!surfaceState.isAir() && !surfaceState.is(Blocks.SNOW)" in handler,
    "the block directly below the target is the support":
        "BlockPos supportPos = surface.below();" in handler
        and "BlockState supportState = level.getBlockState(supportPos);" in handler,
    "liquid supports remain rejected":
        "!supportState.getFluidState().isEmpty()" in handler,
    "support must have a full collision shape":
        "!supportState.isCollisionShapeFullBlock(level, supportPos)" in handler,
}

for label, passed in checks.items():
    assert passed, f"FAIL: {label}"
    print(f"PASS: {label}")

print(f"Sun-event snow support regression pins passed: {len(checks)}")

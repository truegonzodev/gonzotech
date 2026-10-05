#!/usr/bin/env python3
"""Static regression contract for 0.3.159 custom sky-star rotation."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/gonzotech"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


effects = (SRC / "space/client/SpaceSkyEffects.java").read_text(encoding="utf-8")
client = (SRC / "space/client/SpaceClient.java").read_text(encoding="utf-8")
rotation = effects.split("private Matrix4f starRotationMatrix", 1)[1].split(
    "private static float wrappedDegrees", 1
)[0]

pin("STAR_ROTATIONS_PER_DAY_CYCLE = 0.8D" in effects,
    "base star speed is 0.8 revolutions per day/night cycle")
pin("STAR_X_RELATIVE_SPEED = 0.20D" in effects
    and "STAR_Y_RELATIVE_SPEED = 0.40D" in effects,
    "X and Y star rotation speeds are 20% and 40% of the Z/base speed")
pin("double cycleTicks = DEFAULT_STAR_CYCLE_TICKS;" in rotation
    and "DEFAULT_STAR_CYCLE_TICKS = 24000.0D" in effects
    and "fixedDaylight < 0.0F && primarySun != null" in rotation
    and "primarySun.cycleDays()" in rotation
    and "Double.isFinite(sunCycleDays) && sunCycleDays > 0.0D" in rotation,
    "dynamic sun cycles set the period while fixed/no-sun skies fall back to 24,000 ticks")
pin("level.getDayTime() + (double) partialTick" in rotation,
    "rotation is derived from absolute game time including render partial ticks")
pin("wrappedDegrees(baseDegrees)" in rotation
    and "wrappedDegrees(baseDegrees * STAR_X_RELATIVE_SPEED)" in rotation
    and "wrappedDegrees(baseDegrees * STAR_Y_RELATIVE_SPEED)" in rotation,
    "each axis phase is independently wrapped before applying its speed ratio")
pin("new Matrix4f(baseMatrix)" in rotation
    and "starMatrix.rotate(Axis.ZP.rotationDegrees(zDegrees));" in rotation
    and "starMatrix.rotate(Axis.XP.rotationDegrees(xDegrees));" in rotation
    and "starMatrix.rotate(Axis.YP.rotationDegrees(yDegrees));" in rotation
    and "renderStars(starRotationMatrix(level, partialTick, modelViewMatrix), starBrightness);" in effects,
    "only the star draw matrix receives simultaneous Z/X/Y rotation")
registration = client.split("public static void onRegisterDimensionEffects", 1)[1].split(
    "\n    }", 1
)[0]
pin("event.register(Level.OVERWORLD.location(), overworld());" in registration
    and "SpaceDimensions." in registration
    and "event.register(Level.END" not in registration,
    "Overworld and custom skies are registered without changing End")
pin("mod_version=0.3.159" in (ROOT / "gradle.properties").read_text(encoding="utf-8"),
    "micropatch version is 0.3.159")

print(f"space sky rotation audit: {checks} pins passed")

#!/usr/bin/env python3
"""Static contracts for the ReaCalc 1.7 runtime, pipe I/O, and live reactor GUI."""
import json
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"


def read(path: str) -> str:
    return (JAVA / path).read_text(encoding="utf-8")


def method(source: str, signature: str) -> str:
    start = source.index(signature)
    body_start = source.index("{", start)
    depth = 0
    for index in range(body_start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


_ver.at_least("0.3.242", "ReaCalc 1.7 reactor runtime")

runtime = read("machines/reactor/ReactorRuntime.java")
dynamics = read("machines/reactor/ReactorHeatDynamics.java")
curve = read("machines/reactor/ReactorHeatOutputCurve.java")
neutrons = read("machines/reactor/ReactorNeutronMetrics.java")
operating = read("machines/reactor/ReactorOperatingMetrics.java")
panel = read("machines/block/entity/ReactorControlPanelBlockEntity.java")
structure = read("machines/reactor/ReactorStructure.java")
routing = read("machines/network/PipeRouting.java")
defs = read("machines/energy/MachineDefs.java")
menu = read("machines/menu/ReactorMenu.java")
screen = read("machines/client/ReactorScreen.java")

# Runtime automatically samples a formed controller, advances heat, fills GTH,
# and sends it through integrated heat-pipe ports. The stored water buffer is not
# referenced in the simulation tick at all.
tick = method(runtime, "public void serverTick(")
assert "if (!panel.isRuntimeReady()) return;" in tick
assert "refreshSnapshot(level, panel, gameTime)" in tick
assert "heat.advance(1.0)" in tick
assert "accumulateGeneration(generationRate)" in tick
assert "pushGth(level, gameTime)" in tick
assert "water" not in tick.lower(), "stored reactor water must stay outside simulation"
assert "MachineDefs.REACTOR_FUEL_SNAPSHOT_INTERVAL" in tick
assert "private boolean accumulateGeneration(" in runtime and "gth.receive(wholeMilli, false)" in runtime
assert "PipeType.HEAT" in runtime and "sink::receiveGth" in runtime
assert "ReactorStructure::isMember" in runtime
assert "drainFromMultiblockPort" in runtime
assert "gth.extract(moved, false)" in runtime

# ReaCalc 1.7 target/tau/quadratic acceleration are present; the explicit game
# rule makes 10,000 C a display scale, not a shutdown/destruction threshold.
assert "TEMP_SCALE = 2838.43365245" in operating
assert "TEMP_EXPONENT = 1.37798040865" in operating
assert "Math.max(0.001, K * Math.max(1, mass) * (target - AMBIENT) / values.power())" in dynamics
assert "RUN_GAIN * values.power() / Math.max(1, mass) * (values.htv() - 1.0 + EPSILON)" in dynamics
assert "excess += velocity * active + parameters.acceleration() * active * active" in dynamics
assert "public boolean advance(double dt)" in dynamics
assert "FAILURE" not in dynamics and "destroy" not in dynamics.lower()
advance = method(dynamics, "public boolean advance(")
assert "temperature() != before" in advance
curve_at = method(curve, "public Output at(")
assert "temperature <= anchorTemperature" in curve_at
assert "anchor.exchange() * scale" in curve_at and "anchor.useful() * scale" in curve_at
assert "OUTPUT_GAIN * water.output()" in curve
assert "OUTPUT_GAIN = 1.84" in operating

# Flow/leak estimates derive from the live matrix and TVEL ratio, not a fake
# constant; empty zones remain zero. Input water is only stored through its ports.
assert "peakProduction" in neutrons and "operating.htv()" in neutrons
assert "scaledEscape / (scaledEscape + halfScale)" in neutrons
receive_water = method(runtime, "public long receiveWater(")
assert "waterPortSet.contains(port.asLong())" in receive_water
assert "water.receive(amount, simulate)" in receive_water
assert "water.extract(" not in runtime
assert "water.save(tag, \"ReactorWater\")" in runtime
assert "water.load(tag, \"ReactorWater\")" in runtime
assert "tag.putDouble(\"ReactorHeatBase\"" in runtime
assert "tag.getDouble(\"ReactorHeatBase\")" in runtime
assert "runtime.save(tag)" in panel and "runtime.load(tag)" in panel
assert "runtime.serverTick(serverLevel, this)" in panel

assert "REACTOR_WATER_CAPACITY_PER_ZONE_SIDE = 1_024" in defs
assert "REACTOR_GTH_CAPACITY_PER_ZONE_SIDE = 5_484" in defs
assert "REACTOR_MAX_OUTPUT_ROUTE_ATTEMPTS = 8" in defs
assert "heatPorts.stream().mapToLong" in structure
assert "waterPorts.stream().mapToLong" in structure
assert "NodeClumpIndex.detachPorts(level, ports)" in structure
assert "public static Transfer.Receiver waterReceiverAt(" in structure
assert "public static long gthPortLimit(" in structure
water_route = method(routing, "private static int collectLanes(")
assert "case WATER -> {" in water_route
assert "steamGen != null ? steamGen : ReactorStructure.waterReceiverAt(level, pipe)" in water_route

# The first resource indicator is real GTH (not a static GTU scale); all four
# positions and the per-indicator help match the requested 512x512 sheet layout.
assert "drawVBarTex(graphics, gthX, barY, 16, barH, menu.gthFraction(), BAR_GTH)" in screen
assert "drawVBarTex(graphics, waterX, barY, 16, barH, menu.waterFraction(), BAR_WATER)" in screen
assert "x + 44" in screen and "x + 53" in screen
assert "sheet x=172, 7px wide" in screen and "sheet x=181, 7px wide" in screen
assert "TEMP_COLOR = 0xED3261" in screen
assert "NEUTRON_COLOR = 0x6C54E3" in screen
tooltip_keys = (
    "generation", "loaded_tvels", "water_buffer", "hot_temperature",
    "mean_temperature", "strongest_flow", "average_flow",
    "strongest_leak", "average_leak",
)
for suffix in tooltip_keys:
    key = f"gui.gonzotech.reactor.{suffix}"
    assert key in screen, f"missing live reactor tooltip key: {key}"
    for locale in ("en_us", "ru_ru"):
        translations = json.loads((ROOT / f"src/main/resources/assets/gonzotech/lang/{locale}.json").read_text(encoding="utf-8"))
        assert key in translations, f"missing {locale} translation: {key}"
assert "GtUnits.gthPair(menu.gthAmount(), menu.gthCapacity())" in screen
assert "return data.get(2) + (long) data.get(3) * 10_000L;" in menu
assert "return data.get(4) + (long) data.get(5) * 10_000L;" in menu
assert "case 14 -> rateUnits(generationRate)" in runtime
assert "private static int rateUnits(double rate)" in runtime
assert "Math.round(rate)" in runtime
assert "case 15 -> Math.max(0, lastLoadedTvels)" in runtime

print("PASS reactor runtime: thermal dynamics, nonfatal 10,000 C rule, GTH output, isolated water storage, neutron metrics, pipe routing, GUI contracts")

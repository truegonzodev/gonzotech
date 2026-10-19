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
# and sends it through integrated heat-pipe ports. Since 0.3.244 the water gauge
# modulates output and heat exchange (explicit game rule), but the simulation
# still never consumes stored water — only the relief valve removes it.
tick = method(runtime, "public void serverTick(")
assert "if (!panel.isRuntimeReady()) return;" in tick
assert "refreshSnapshot(level, panel, gameTime)" in tick
assert "heat.advance(1.0)" in tick
assert "accumulateGeneration(generationRate)" in tick and "drainParasiticLoss()" in tick
assert "pushGth(level, gameTime)" in tick
assert "double gaugeFill = coolantFill();" in tick
assert "output.exchange() * outputFactor(gaugeFill)" in tick
assert "water.extract(" not in tick and "water.receive(" not in tick
assert "MachineDefs.REACTOR_FUEL_SNAPSHOT_INTERVAL" in tick
assert "private boolean accumulateGeneration(" in runtime and "gth.receive(wholeMilli, false)" in runtime
assert "PipeType.HEAT" in runtime and "sink::receiveGth" in runtime
assert "ReactorStructure::isMember" in runtime
assert "drainFromMultiblockPort" in runtime
assert "gth.extract(moved, false)" in runtime

# ReaCalc 1.7 target/tau/quadratic acceleration are present. The dynamics core
# itself stays free of failure logic; since 0.3.246 the runtime treats 10,000 C
# as the meltdown trigger and hands the accident to ReactorAccident (level-side).
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
# Вода уходит ТОЛЬКО через клапанный сброс (20%×N за 1.75 сек) и через обнуление
# показаний в суб-тике аварии; симуляция воду не расходует.
vent = method(runtime, "private boolean valveVentTick(")
assert runtime.count("water.extract(") == 2 and "water.extract(takeWater, false)" in vent
assert "water.extract(water.amount(), false)" in method(runtime, "void clearGaugesForAccident(")
assert "water.save(tag, \"ReactorWater\")" in runtime
assert "water.load(tag, \"ReactorWater\")" in runtime
assert "tag.putDouble(\"ReactorHeatBase\"" in runtime
assert "tag.getDouble(\"ReactorHeatBase\")" in runtime
assert "runtime.save(tag)" in panel and "runtime.load(tag)" in panel
assert "runtime.serverTick(serverLevel, this)" in panel

assert "REACTOR_WATER_CAPACITY_PER_ZONE_SIDE = 1_024" in defs
assert "REACTOR_GTH_CAPACITY_PER_ZONE_SIDE = 5_484" in defs
assert "REACTOR_GTH_CAPACITY_ZONE_DIVISOR = 2.3" in defs
assert "zone * zone / MachineDefs.REACTOR_GTH_CAPACITY_ZONE_DIVISOR" in runtime
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
assert "GtUnits.gthPair(GtUnits.x1(menu.gthAmountTenths() / 10.0)," in screen
assert "return data.get(2) + (long) data.get(3) * 10_000L;" in menu
assert "return data.get(4) + (long) data.get(5) * 10_000L;" in menu
assert "case 14 -> (int) (rateTenths % ENERGY_SYNC_BASE)" in runtime
assert "private static long rateTenths(double rate)" in runtime
assert "case 16 -> Math.max(0, lastLoadedTvels)" in runtime

# 0.3.243: X.X third-format numbers, white tooltip text with colored values only,
# water-model temperature/pressure lines, TVEL durability, GTH back-pressure
# layer and the redstone relief valve.
_ver.at_least("0.3.243", "reactor back-pressure layer, relief valve and TVEL durability")

assert "GtUnits.x1(" in screen
assert "withStyle(ChatFormatting.WHITE)" in screen
assert "private static Component white(String key, Object value)" in screen
assert "PRESSURE_COLOR = 0x6A89A3" in screen
assert "temperatureValue(menu.hottestTemperatureTenths())" in screen
assert "percentValue(menu.strongestFlowTenthsPercent())" in screen
assert "menu.modelWaterTemperatureTenths() / 10.0" in screen
assert "menu.modelWaterPressureTenthsMpa() / 10.0" in screen

for key in ("model_water_temperature", "model_water_pressure", "pressure_value"):
    for locale in ("en_us", "ru_ru"):
        translations = json.loads((ROOT / f"src/main/resources/assets/gonzotech/lang/{locale}.json").read_text(encoding="utf-8"))
        assert f"gui.gonzotech.reactor.{key}" in translations, (locale, key)
assert "Выработка модели: %s" in json.loads((ROOT / "src/main/resources/assets/gonzotech/lang/ru_ru.json").read_text(encoding="utf-8"))["gui.gonzotech.reactor.generation"]

# GTH >97% grows a separate overheat layer by generation/2000 C per tick; it is
# never permanent: below 97% or after TVEL removal it fully wilts over 20 s.
assert "GTH_BACKPRESSURE_FILL = 0.97" in runtime
assert "GTH_BACKPRESSURE_DIVISOR = 2000.0" in runtime
assert "GTH_BACKPRESSURE_FADE_TICKS = 400" in runtime
assert "backpressureBonus += generationRate / GTH_BACKPRESSURE_DIVISOR" in runtime
assert "backpressureFadePerTick = backpressureBonus / GTH_BACKPRESSURE_FADE_TICKS" in runtime
assert "if (fuelChanged && snapshot.loadedTvels() < lastLoadedTvels)" in runtime
assert "heat.output(Math.max(0.0, backpressureBonus))" in runtime
assert "public ReactorHeatOutputCurve.Output output(double extraHeat)" in dynamics

# Redstone-powered panel + GTH >99.9% opens the valve: air-cleaner-style poof
# geyser outward from the active zone and 20% of the bar per valve released
# over 1.75 s (shared cycle, valve count scales the amount).
assert "VALVE_TRIGGER_FILL = 0.999" in runtime
assert "VALVE_VENT_TICKS = 35" in runtime
assert "VALVE_VENT_FIFTHS = 5" in runtime
assert "gthFillFraction() <= VALVE_TRIGGER_FILL" in runtime
assert "level.hasNeighborSignal(panel.getBlockPos())" in runtime
assert "gth.amountAsLong() * valves / VALVE_VENT_FIFTHS" in vent
assert "ParticleTypes.POOF" in runtime and "spawnValveGeysers" in runtime
assert "outwardDirection(pos, min, max)" in runtime
valve_block = read("core/block/ReactorValveBlock.java")
assert "FACING = BlockStateProperties.FACING" in valve_block
assert "ReactorStructure.partChanged(level, pos)" in valve_block
assert "valveFacesZone" in structure
assert "block instanceof com.gonzotech.core.block.ReactorValveBlock" in structure
assert "info.valvePorts()" in structure
assert "heatPorts, waterPorts, info.valvePorts());" in structure
assert "runtime.configure(max.getX() - min.getX() - 1, heatPorts, waterPorts, valvePorts)" in panel

# TVELs carry a vanilla durability component (12608) and lose 1 point per second
# inside the reactor; an exhausted rod is replaced by the spent TVEL item.
assert "UF_TVEL_DURABILITY = 12_608" in read("core/registry/ModItems.java")
assert "props.stacksTo(1).durability(UF_TVEL_DURABILITY)" in read("core/registry/ModItems.java")
assert "if (++fuelAgeTicks >= 20)" in tick
assert "public static boolean ageLoadedFuel(" in read("machines/reactor/ReactorFuelStorage.java")
assert "public boolean ageFuel()" in read("machines/block/entity/ReactorFuelSheathBlockEntity.java")
assert "stack.getDamageValue() + 1" in read("machines/block/entity/ReactorFuelSheathBlockEntity.java")
assert "new ItemStack(ModItems.DEPRESSED_UF_TVEL.get())" in read("machines/block/entity/ReactorFuelSheathBlockEntity.java")

# 0.3.244: the water gauge ties into heat exchange and output (explicit game
# rule): 100% unchanged, 50% -> output x0.6 with an intensified bath, 0% -> x0.1.
# The valve additionally vents 20% of current water, its trigger stays GTH-only
# (no self-runaway temperature trigger), a parasitic 1 GTH/t loss drains the
# buffer every tick (since 0.3.247), and the GTH buffer is 5484 x side^2 / 2.3.
_ver.at_least("0.3.244", "water-gauge heat exchange link, valve water vent, 16^3 active zone")

assert "public static double outputFactor(double fill)" in runtime
assert "public static double exchangeFactor(double fill)" in runtime
out_factor = method(runtime, "public static double outputFactor(")
assert "0.1 + gauge" in out_factor and "0.6 + (gauge - 0.5) * 0.8" in out_factor
assert "2.0 - outputFactor(fill)" in runtime
assert "boostedBathTemperature(output.waterTemperature(), gaugeFill)" in runtime
assert "ReactorOperatingMetrics.pressure(modelWaterTemperature)" in runtime
assert "usefulGenerationRate = output.useful() * outputFactor(gaugeFill)" in runtime
assert "water.amount() * valves / VALVE_VENT_FIFTHS" in vent
assert "water.extract(takeWater, false)" in vent
assert "ReactorValveVentWater" in runtime
assert "temperature" not in vent.lower() and "heat" not in vent.lower()
assert "gthFillFraction() <= VALVE_TRIGGER_FILL" in vent
assert "MIN_SIZE = 4" in structure and "MAX_SIZE = 18" in structure

# 0.3.245: valve count scales the relief — 20% x N of GTH and of water per
# 1.75 s cycle (cap 4 valves), no production overpower: the "bar stays full,
# valve wastes water" regime remains the default for undersized relief.
_ver.at_least("0.3.245", "multi-valve relief scaling without overpower")

assert "VALVE_SCALING_CAP = 4" in runtime
assert "private int ventValves()" in runtime
assert "Math.min(valvePorts.length, VALVE_SCALING_CAP)" in runtime
assert "generationRate" not in vent and "PARASITIC_GTH_PER_TICK" not in vent

# 0.3.246: meltdown scenario at 10,000 C. Sub-tick T0 clears the GTH/water/
# temperature/flow gauges ("already 100%, nothing happened yet") and queues the
# accident on the level (it survives structure and panel destruction): T1 water
# -> dead/radioactive slime 80/20 in radius 4 from the breach; T2 chaotic core
# sphere radius 2 -> corium/lava (sheaths+graphite -> corium, barium+boron ->
# lava), shell/nodes radius 2 -> lava 30%, breach -> corium 100%, then
# explosion_emitter + TNT-power-2 blast; T3 outward geyser 2-3x faster than the
# valve + lava particles + buckshot of falling dead-slime blocks (5-20 blocks,
# they land as blocks); white dust particles (1-5/tick, radius 0-3, size 1-4,
# 10-15 s) for the accident ticks + 2 more; chunk gets a one-time +5 mZt dose.
_ver.at_least("0.3.246", "reactor meltdown accident scenario at 10,000 C")

accident = read("machines/reactor/ReactorAccident.java")
accident_data = read("machines/reactor/ReactorAccidentData.java")
dust_particle = read("core/client/particle/ReactorDustParticle.java")

assert "MELTDOWN_TEMPERATURE = 10_000.0" in runtime
assert "double hotNow = hotWithBackpressure();" in runtime
assert "if (hotNow >= MELTDOWN_TEMPERATURE)" in runtime
assert "clearGaugesForAccident" in runtime and "triggerAccident" in runtime
assert "heat.restore(new ReactorHeatDynamics.Saved(false, 0.0," in runtime
assert "neutronMetrics = ReactorNeutronMetrics.Values.ZERO" in runtime
assert "accidentQueued" in runtime and "ReactorAccidentQueued" in runtime
assert "accidentQueued = false;" in method(runtime, "public void deconfigure(")
assert "ReactorAccident.nearestShellBlock(hottest, min, max)" in runtime
assert "raw().heatProduction()" in runtime

assert "WATER_SLIME_RADIUS = 4" in accident and "DEAD_SLIME_CHANCE = 0.8" in accident
assert "CORE_SPHERE_RADIUS = 2.0" in accident
assert "SHELL_LAVA_RADIUS = 2" in accident and "SHELL_LAVA_CHANCE = 0.3" in accident
assert "EXPLOSION_POWER = 2.0F" in accident
assert "GEYSER_SPEED = 1.15" in accident
assert "FALLING_SLIME_MIN = 8" in accident and "FALLING_SLIME_MAX = 16" in accident
assert "DUST_MIN_PER_TICK = 1" in accident and "DUST_MAX_PER_TICK = 5" in accident
assert "DUST_RADIUS = 3" in accident and "DUST_TICKS = 5" in accident
assert "ModBlocks.DEAD_SLIME_BLOCK" in accident and "ModBlocks.RADIOACTIVE_SLIME_BLOCK" in accident
assert "ModBlocks.CORIUM" in accident and "Blocks.LAVA" in accident
assert "isMetalBlock(block, \"boron_block\")" in accident and "isMetalBlock(block, \"barium_block\")" in accident
assert "ParticleTypes.EXPLOSION_EMITTER" in accident
assert "Level.ExplosionInteraction.BLOCK" in accident
assert "FallingBlockEntity.fall(" in accident and "setDeltaMovement(" in accident
assert "ParticleTypes.LAVA" in accident and "ParticleTypes.POOF" in accident
assert "LevelTickEvent.Post" in accident
assert "ACCIDENT_DOSE_NZT = 5.0 * com.gonzotech.radiation.RadUnits.MILLI" in accident
assert "addContamination" in accident_data and "ACCIDENT_DOSE_NZT" in accident_data
assert "gonzotech_reactor_accidents" in accident_data
assert "MIN_SIZE = 0.5F" in dust_particle and "MAX_SIZE = 3.0F" in dust_particle
assert "MIN_LIFETIME_TICKS = 60" in dust_particle and "MAX_LIFETIME_TICKS = 100" in dust_particle
assert "setColor(1.0F, 1.0F, 1.0F)" in dust_particle
assert "REACTOR_DUST" in read("core/registry/ModParticles.java")
assert "ReactorDustParticle.Provider::new" in read("core/client/particle/ModParticleClient.java")

# 0.3.247: meltdown polish — the hottest point and graphite melt into MOLTEN
# corium; a one-shot 1alarm.ogg siren (pitch 0.9-1.1, volume 16, ~128 blocks)
# fires exactly when the temperature crosses 9608 C upward; reactor dust is
# 0.5-3 blocks and lives 3-5 s; slime buckshot flies 3-10 blocks and drops
# nothing on an unsuitable landing; the parasitic loss is a visible 1 GTH/t
# buffer self-discharge.
_ver.at_least("0.3.247", "meltdown polish: molten corium, 9608 C siren, dust/slime tuning, visible parasitic drain")

assert "ALARM_TEMPERATURE = 9608.0" in runtime
assert "ALARM_VOLUME = 16.0F" in runtime
assert "ALARM_PITCH_MIN = 0.9F" in runtime and "ALARM_PITCH_MAX = 1.1F" in runtime
assert "ModSounds.REACTOR_ALARM" in runtime and "SoundSource.BLOCKS" in runtime
assert "alarmArmed" in runtime and "ReactorAlarmArmed" in runtime
assert "REACTOR_ALARM" in read("core/registry/ModSounds.java")
assert "1alarm" in json.loads((ROOT / "src/main/resources/assets/gonzotech/sounds.json").read_text(encoding="utf-8"))
assert "private boolean drainParasiticLoss(" in runtime
assert "PARASITIC_GTH_PER_TICK * MachineDefs.MILLI" in runtime
core_repl = method(accident, "private static BlockState coreReplacement(")
assert "GRAPHITE_BLOCK" in core_repl and "MOLTEN_CORIUM" in core_repl
assert "ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3)" in accident
assert "dropItem = false" in accident
assert "FALLING_SPEED_MIN = 0.2" in accident and "FALLING_SPEED_MAX = 0.7" in accident

print("PASS reactor runtime: thermal dynamics, 10,000 C meltdown accident, 9608 C siren, GTH output, water-gauge exchange link, neutron metrics, pipe routing, GUI contracts, back-pressure layer, multi-valve relief with water vent, TVEL durability")

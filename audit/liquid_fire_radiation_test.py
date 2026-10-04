#!/usr/bin/env python3
"""Static regression contract for 0.3.151 corium-source radiation and liquid fire."""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources/assets/gonzotech"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


def read_java(relative):
    return (SRC / relative).read_text(encoding="utf-8")


# Corium's shared block ID must be resolved using the actual fluid state.
rad_sources = read_java("radiation/RadSources.java")
state_emission = rad_sources.split("public static double blockEmission(BlockState state)", 1)[1].split("\n    /**", 1)[0]
pin('Map.entry("molten_corium", 3.0 * RadUnits.MILLI)' in rad_sources,
    "molten-corium source rate remains 3 mZt/t")
pin('id.getPath().equals("molten_corium") && !state.getFluidState().isSource()' in state_emission,
    "flowing molten corium is excluded while its source is radioactive")
chunk = read_java("radiation/ChunkRadiationData.java")
pin("RadSources.blockEmission(level.getBlockState(pos))" in chunk,
    "placed-source reconciliation, dose, and diagnostics use actual block states")
pin("RadSources.blockEmission(level.getBlockState(candidate))" in chunk,
    "piston-neighbour recovery rejects flowing corium")
containment = read_java("radiation/Containment.java")
pin("RadSources.blockEmission(state)" in containment,
    "containment screening uses state-aware emissions")
system = read_java("radiation/RadiationSystem.java")
pin("trackPlacedBlock(level, event.getPos(), event.getPlacedBlock())" in system
    and "RadSources.blockEmission(event.getState())" in system,
    "ordinary place/break events use their actual states")
pin("trackPlacedBlock(ServerLevel level, BlockPos pos" in system,
    "direct world mutations have a source-indexing hook")
bucket = read_java("mixin/BucketItemMixin.java")
thermal = read_java("machines/nuclear/ThermalHazards.java")
pin("RadiationSystem.trackReplacedBlock(serverLevel, lavaPos, oldState, finalState)" in bucket,
    "bucket placements register a corium source and unindex a replaced one")
pin("RadiationSystem.trackReplacedBlock(level, melted, oldState, replacement)" in thermal,
    "thermal corium placement registers a source and unindexes the melted block")
pin("RadSources.blockEmission(oldState)" in system
    and "RadSources.blockEmission(newState)" in system,
    "direct replacements use state-aware removal and placement emissions")
pin("if (!set.add(pos.asLong())) return;" in chunk,
    "bucket hook and placement event cannot double-count one position")
pin("LongOpenHashSet claimed = new LongOpenHashSet();" in chunk
    and "claimed.contains(candidate.asLong())" in chunk,
    "source reconciliation cannot migrate multiple stale entries onto one live source")

# Each liquid gets a separately registered fire and its own texture path/color.
blocks = read_java("core/registry/ModBlocks.java")
pin('"rectificate_fire"' in blocks and "ModFluids.ETHANOL, 0x2D66FF" in blocks,
    "rectificate has its own blue fire block")
pin('"formaldehyde_fire"' in blocks and "ModFluids.FORMALDEHYDE, 0x55E9FF" in blocks,
    "formaldehyde has its own cyan fire block")
fluid_block = read_java("core/fluid/ModFluidBlock.java")
pin("Items.FLINT_AND_STEEL" in fluid_block and "Items.FIRE_CHARGE" in fluid_block,
    "both source liquids accept flint and steel and fire charges")
pin("instanceof BaseFireBlock" in fluid_block and "fire.ignite(level, pos)" in fluid_block,
    "neighboring ordinary or custom fire can ignite an exposed source")
pin("instanceof ServerLevel serverLevel" in fluid_block
    and "serverLevel.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)" in fluid_block,
    "fire-tick gamerule is read from ServerLevel rather than Level")
fire = read_java("core/fluid/LiquidFireBlock.java")
pin("private static final int BURN_STEPS = 15" in fire
    and "MIN_STEP_DELAY = 7" in fire and "STEP_DELAY_RANGE = 7" in fire,
    "AGE-based timer consumes the source after 106–196 ticks")
pin("return state.isSource() && state.getType() == fuel.get();" in fire,
    "flames only burn their matching source state")
pin("level.setBlock(fuelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)" in fire,
    "fuel source is removed only at burn-out")
pin("BaseFireBlock.getState(level, vanillaFirePos)" in fire
    and "combustible.ignitedByLava()" in fire,
    "combustible blocks receive vanilla fire, never custom liquid fire")
pin("fluid.getHeight(level, fuelPos)" in fire,
    "colored sparks use the liquid surface height")

for block, model, texture in (
    ("rectificate_fire", "fire/rectificate_flame.json", "fire/rectificate_fire.png"),
    ("formaldehyde_fire", "fire/formaldehyde_flame.json", "fire/formaldehyde_fire.png"),
):
    blockstate = json.loads((RES / "blockstates" / f"{block}.json").read_text(encoding="utf-8"))
    model_data = json.loads((RES / "models/block" / model).read_text(encoding="utf-8"))
    pin(bool(blockstate.get("multipart")), f"{block} has a blockstate model")
    pin(any(element["from"][1] == -2 for element in model_data["elements"]),
        f"{block} flame model is lowered to the fluid surface")
    texture_path = RES / "textures/block" / texture
    data = texture_path.read_bytes()
    pin(data.startswith(b"\x89PNG\r\n\x1a\n"), f"{texture} is a PNG placeholder")
    offset = 8
    image_header = None
    image_data = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack(">I", data[offset + 8 + length:offset + 12 + length])[0]
        pin(zlib.crc32(kind + payload) & 0xFFFFFFFF == crc, f"{texture} has valid PNG checksums")
        if kind == b"IHDR":
            image_header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            image_data.extend(payload)
        offset += 12 + length
        if kind == b"IEND":
            break
    width, height, bit_depth, color_type, *_ = image_header
    decoded = zlib.decompress(bytes(image_data))
    pin((width, height, bit_depth, color_type) == (16, 16, 8, 6),
        f"{texture} is a 16x16 32-bit RGBA image")
    pin(len(decoded) == height * (1 + width * 4) and not any(decoded),
        f"{texture} remains a fully transparent user-drawn placeholder")

print(f"liquid fire / corium radiation contract passed: {checks} checks (static)")

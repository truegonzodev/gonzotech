#!/usr/bin/env python3
"""Static regression contract for 0.3.154 per-cell liquid fire and corium radiation."""
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

# Each liquid has a separately registered overlay; its timer entity supports both variants.
blocks = read_java("core/registry/ModBlocks.java")
pin('"rectificate_fire"' in blocks and "ModFluids.ETHANOL, 0x2D66FF" in blocks,
    "rectificate has its own blue fire block")
pin('"formaldehyde_fire"' in blocks and "ModFluids.FORMALDEHYDE, 0x55E9FF" in blocks,
    "formaldehyde has its own cyan fire block")
pin("state.getValue(LiquidFireBlock.ACTIVE) ? 12 : 0" in blocks
    and "state.getValue(LiquidFireBlock.ACTIVE) ? 11 : 0" in blocks,
    "hidden delayed overlay emits no light until ignition")
fluid_block = read_java("core/fluid/ModFluidBlock.java")
pin("Items.FLINT_AND_STEEL" in fluid_block and "Items.FIRE_CHARGE" in fluid_block,
    "both source and flowing cells accept flint and steel and fire charges")
pin("state.getFluidState().isEmpty()" in fluid_block
    and "fire.ignite(serverLevel, pos, 0, level.random)" in fluid_block
    and "return InteractionResult.CONSUME;" in fluid_block,
    "manual ignition supports all matching cells and blocks fallback vanilla fire placement")
pin("40 + level.random.nextInt(21)" in fluid_block
    and "serverLevel.scheduleTick(pos, this" in fluid_block
    and "Leave vanilla fire visible" in fluid_block,
    "vanilla fire remains visible while liquid ignition waits 40–60 ticks")
pin("instanceof BaseFireBlock" in fluid_block
    and "!(neighbor.getBlock() instanceof LiquidFireBlock)" in fluid_block
    and "40 + level.random.nextInt(21)" in fluid_block,
    "only ordinary BaseFireBlock sources start the separate 2–3 second ignition delay")

fire = read_java("core/fluid/LiquidFireBlock.java")
pin("BooleanProperty.create(\"active\")" in fire
    and 'IntegerProperty.create("surface", 0, 8)' in fire,
    "overlay has an independent visibility flag and nine surface variants")
pin("fluid == ModFluids.FLOWING_ETHANOL.get()"
    and "fluid == ModFluids.FLOWING_FORMALDEHYDE.get()" in fire,
    "both flowing fluid IDs are recognized as burnable cells")
pin("if (state.isEmpty() || state.isSource()) return 0" in fire
    and "if (amount >= 8) return 8" in fire
    and "8 - amount" in fire,
    "source, levels 1–7, and full-height falling states use distinct models")
pin("ModBlockEntities.LIQUID_FIRE.get()" in fire
    and "fire.tickServer(server, blockState, server.random)" in fire
    and "scheduleTick(pos, this" not in fire,
    "fire timers tick through the registered server BlockEntity ticker, not block scheduling")
pin("Do not delegate to FireBlock.updateShape" in fire
    and "return state.setValue(SURFACE, surfaceIndex(fuelState));" in fire,
    "custom liquid overlay survives without vanilla solid-support checks")
pin("candidates.add(neighborFuel.immutable())" in fire
    and "targetFire.ignite(level, target, 0, random)" in fire,
    "each liquid-fire pulse ignites no more than one neighboring liquid cell")
pin("MIN_SPREAD_TICKS = 8" in read_java("core/fluid/LiquidFireBlockEntity.java")
    and "MAX_SPREAD_TICKS = 32" in read_java("core/fluid/LiquidFireBlockEntity.java"),
    "custom propagation uses the specified 8–32 tick interval")
pin("level.removeBlock(firePos, false)" in fire
    and "level.setBlock(fuelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)" in fire,
    "burnout removes only its overlay and matching supporting fluid cell")
pin("support.ignitedByLava()" in fire
    and "supportFire.ignite(level, supportPos, 0, random)" in fire
    and "BaseFireBlock.getState(level, fuelPos)" in fire,
    "post-burn attempts valid vanilla or custom fire on the support")
pin("state.getAmount()" in fire and "surfaceHeight(fluid)" in fire,
    "colored sparks follow the current source/flowing surface")

fire_entity = read_java("core/fluid/LiquidFireBlockEntity.java")
pin("MIN_BURN_TICKS = 8 * 20" in fire_entity
    and "MAX_BURN_TICKS = 17 * 20" in fire_entity
    and "BURN_MODE_TICKS = 11 * 20" in fire_entity,
    "per-cell fire lifetime has an 8–17 second range and approximately 12 second mean")
pin("sampleBurnTicks(random)" in fire_entity
    and "Math.sqrt" in fire_entity
    and "sampleSpreadTicks(random)" in fire_entity,
    "burn clocks are independently randomized and persisted as absolute game times")
pin("scheduleNext" not in fire_entity and "level.scheduleTick" not in fire_entity
    and "void tickServer(ServerLevel level" in fire_entity,
    "per-cell fire lifecycle is not rescheduled through vanilla block ticks")
pin('tag.putLong("BurnOutAt"' in fire_entity
    and 'tag.putLong("NextSpreadAt"' in fire_entity
    and 'tag.putLong("ActivationAt"' in fire_entity,
    "ignition, spread, and burnout timers survive save/reload")
block_entities = read_java("machines/registry/ModBlockEntities.java")
pin('register("liquid_fire"' in block_entities
    and "ModBlocks.RECTIFICATE_FIRE.get()" in block_entities
    and "ModBlocks.FORMALDEHYDE_FIRE.get()" in block_entities,
    "shared liquid-fire BlockEntity type is registered for both overlays")
version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
pin("mod_version=0.3.154" in version, "micropatch version should be 0.3.154")

# Surface heights supplied by the author are encoded as nine vanilla-style fire assemblies per liquid.
heights = [0.875, 0.71875, 0.60625, 0.5, 0.3875, 0.28125, 0.16875, 0.05625, 1.0]
for block, model_stem, texture in (
    ("rectificate_fire", "rectificate_flame", "fire/rectificate_fire.png"),
    ("formaldehyde_fire", "formaldehyde_flame", "fire/formaldehyde_fire.png"),
):
    blockstate = json.loads((RES / "blockstates" / f"{block}.json").read_text(encoding="utf-8"))
    parts = blockstate.get("multipart", [])
    pin(len(parts) == 45, f"{block} has a floor cross and four side faces for each surface height")
    for index, height in enumerate(heights):
        group = parts[index * 5:(index + 1) * 5]
        condition = {"active": "true", "surface": str(index)}
        pin(all(part.get("when") == condition for part in group),
            f"{block} floor and side faces use active/surface state {index}")
        floor_name = model_stem if index == 0 else f"{model_stem}_{index}"
        pin(group[0]["apply"].get("model") == f"gonzotech:block/fire/{floor_name}",
            f"{block} surface {index} retains its lowered crossed-plane floor flame")
        floor_data = json.loads((RES / "models/block/fire" / f"{floor_name}.json").read_text(encoding="utf-8"))
        offset = round((height - 1.0) * 16.0, 5)
        pin(floor_data.get("render_type") == "minecraft:cutout"
            and all(round(element["from"][1], 5) == offset
                    and round(element["to"][1], 5) == round(22.4 + offset, 5)
                    and round(element["rotation"]["origin"][1], 5) == round(8.0 + offset, 5)
                    for element in floor_data["elements"]),
            f"{block} floor model {index} uses alpha cutout and exact liquid-height offset")

        side_name = f"{model_stem}_side_{index}"
        side_data = json.loads((RES / "models/block/fire" / f"{side_name}.json").read_text(encoding="utf-8"))
        side_element = side_data["elements"][0]
        pin(group[1]["apply"].get("model") == f"gonzotech:block/fire/{side_name}"
            and [part["apply"].get("y", 0) for part in group[1:]] == [0, 90, 180, 270]
            and side_data.get("render_type") == "minecraft:cutout"
            and round(side_element["from"][1], 5) == offset
            and round(side_element["to"][1], 5) == round(22.4 + offset, 5)
            and set(side_element["faces"]) == {"north", "south"},
            f"{block} side model {index} has four vanilla-oriented faces, correct UV planes and alpha")

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

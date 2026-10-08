#!/usr/bin/env python3
"""Regression pins for the gunpowder block and industrial TNT content."""
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
DATA = RES / "data/gonzotech"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


blocks = (JAVA / "core/registry/ModBlocks.java").read_text(encoding="utf-8")
items = (JAVA / "core/registry/ModItems.java").read_text(encoding="utf-8")
tabs = (JAVA / "core/registry/ModCreativeTabs.java").read_text(encoding="utf-8")
custom_tnt = (JAVA / "core/block/CustomTntBlock.java").read_text(encoding="utf-8")
gunpowder = (JAVA / "core/block/GunpowderBlock.java").read_text(encoding="utf-8")
industrial = (JAVA / "core/block/IndustrialTntBlock.java").read_text(encoding="utf-8")
primed = (JAVA / "core/block/IndustrialPrimedTnt.java").read_text(encoding="utf-8")
gunpowder_primed = (JAVA / "core/block/GunpowderPrimedTnt.java").read_text(encoding="utf-8")
mod_particles = (JAVA / "core/registry/ModParticles.java").read_text(encoding="utf-8")
particle_client = (JAVA / "core/client/particle/ModParticleClient.java").read_text(encoding="utf-8")
scaled_emitter = (JAVA / "core/client/particle/ScaledExplosionEmitterParticle.java").read_text(encoding="utf-8")
blast_dust = (JAVA / "core/client/particle/IndustrialBlastDustParticle.java").read_text(encoding="utf-8")
explosive_effects = (JAVA / "core/event/ExplosiveEffects.java").read_text(encoding="utf-8")
mod_entry = (JAVA / "GonzoTechMod.java").read_text(encoding="utf-8")

# Registrations and requested creative-tab placement.
pin('"gunpowder_block", GunpowderBlock::new' in blocks, "gunpowder block is registered")
pin('"industrial_tnt", IndustrialTntBlock::new' in blocks, "industrial TNT block is registered")
pin('"gunpowder_block", ModBlocks.GUNPOWDER_BLOCK' in items, "gunpowder BlockItem is registered")
pin('"industrial_tnt", ModBlocks.INDUSTRIAL_TNT' in items, "industrial TNT BlockItem is registered")
components = tabs.split('"components"', 1)[1].split('.build()', 1)[0]
component_items = re.findall(r"output\.accept\(([^;]+)\);", components)
graphite_block_index = component_items.index("ModItems.GRAPHITE_BLOCK_ITEM.get()")
pin(component_items[graphite_block_index + 1] == "ModItems.GUNPOWDER_BLOCK_ITEM.get()",
    "gunpowder block immediately follows graphite in Components")
adaptations = tabs.split('"adaptations"', 1)[1].split('.build()', 1)[0]
accepted_items = re.findall(r"output\.accept\(([^;]+)\);", adaptations)
pin(accepted_items[-1] == "ModItems.INDUSTRIAL_TNT_ITEM.get()",
    "industrial TNT is the last Adaptations tab item")

# Powder block: one-tick, block-breaking strength-5.4 blast, plus full-grid and reverse recipes.
pin("EXPLOSION_STRENGTH = 5.4F" in gunpowder, "gunpowder blast power is nerfed to 5.4")
pin("FUSE_TICKS = 1" in gunpowder
    and "extends PrimedTnt" in gunpowder_primed
    and "setFuse(GunpowderBlock.FUSE_TICKS)" in gunpowder_primed
    and "serverLevel.addFreshEntity(primedTnt)" in gunpowder
    and "serverLevel.explode(" not in gunpowder,
    "gunpowder primes a one-tick TNT entity instead of excluding its igniter as explosion source")
forward = load(DATA / "recipe/gunpowder_block.json")
pin(forward["type"] == "minecraft:crafting_shaped", "gunpowder block requires a shaped recipe")
pin(forward["pattern"] == ["GGG", "GGG", "GGG"], "gunpowder block uses all nine crafting slots")
pin(forward["key"] == {"G": "minecraft:gunpowder"}, "gunpowder block uses vanilla gunpowder")
pin(forward["result"] == {"id": "gonzotech:gunpowder_block", "count": 1},
    "gunpowder block recipe output")
reverse = load(DATA / "recipe/gunpowder_from_gunpowder_block.json")
pin(reverse["type"] == "minecraft:crafting_shapeless", "reverse recipe is shapeless")
pin(reverse["ingredients"] == ["gonzotech:gunpowder_block"], "reverse recipe consumes one block")
pin(reverse["result"] == {"id": "minecraft:gunpowder", "count": 9},
    "reverse recipe returns nine gunpowder")

# Industrial TNT: exact recipe, doubled fuse, power 7.1, and native TNT explosion behavior.
recipe = load(DATA / "recipe/industrial_tnt.json")
pin(recipe["pattern"] == ["GPI", "PRP", "IPG"], "industrial TNT pattern matches the requested matrix")
pin(recipe["key"] == {
    "G": "minecraft:gravel",
    "P": "gonzotech:gunpowder_block",
    "I": "minecraft:iron_pickaxe",
    "R": "minecraft:redstone_block",
}, "industrial TNT ingredients match the requested matrix")
pin(recipe["result"] == {"id": "gonzotech:industrial_tnt", "count": 1},
    "industrial TNT recipe output")
pin("FUSE_TICKS = 160" in industrial, "industrial TNT fuse is 160 ticks")
pin("EXPLOSION_STRENGTH = 7.1F" in industrial, "industrial TNT strength is nerfed to 7.1")
pin("extends PrimedTnt" in primed and "setFuse(IndustrialTntBlock.FUSE_TICKS)" in primed,
    "industrial TNT uses the vanilla primed TNT entity and extended fuse")
pin('"explosion_power"' in primed and "IndustrialTntBlock.EXPLOSION_STRENGTH" in primed,
    "industrial blast power is persisted through vanilla primed TNT data")
pin("serverLevel.addFreshEntity(primedTnt)" in industrial,
    "industrial block primes an entity rather than exploding immediately")
for hook in ("onPlace", "neighborChanged", "onCaughtFire", "useItemOn", "onProjectileHit", "playerWillDestroy", "wasExploded"):
    pin(hook in custom_tnt, f"TNT ignition path is handled: {hook}")

# Both blasts replace only the large vanilla emitter, scaled via its own flash sprites.
pin("ModParticles.GUNPOWDER_EXPLOSION_EMITTER.get()" in explosive_effects
    and "ModParticles.INDUSTRIAL_TNT_EXPLOSION_EMITTER.get()" in explosive_effects,
    "each explosive uses its own custom emitter")
pin("GUNPOWDER_SIZE_SCALE = 1.22F" in scaled_emitter
    and "INDUSTRIAL_TNT_SIZE_SCALE = 1.55F" in scaled_emitter
    and "LIFETIME_TICKS = 8" in scaled_emitter
    and "PARTICLES_PER_TICK = 6" in scaled_emitter
    and "ParticleTypes.EXPLOSION" in scaled_emitter
    and "flash.scale(this.sizeScale)" in scaled_emitter,
    "custom emitters match vanilla emitter timing while scaling flashes as requested")
pin('PARTICLE_TYPES.register("gunpowder_explosion_emitter"' in mod_particles
    and 'PARTICLE_TYPES.register("industrial_tnt_explosion_emitter"' in mod_particles
    and "registerSpecial(ModParticles.GUNPOWDER_EXPLOSION_EMITTER.get()" in particle_client
    and "registerSpecial(ModParticles.INDUSTRIAL_TNT_EXPLOSION_EMITTER.get()" in particle_client,
    "both custom emitter types are registered with client providers")
pin("ParticleTypes.EXPLOSION" in explosive_effects and "SoundEvents.GENERIC_EXPLODE" in explosive_effects
    and "instanceof GunpowderPrimedTnt" in explosive_effects
    and "Level.ExplosionInteraction.BLOCK" in explosive_effects
    and "event.setCanceled(true)" in explosive_effects
    and "Level.ExplosionInteraction.TNT" in explosive_effects,
    "both one-tick custom blasts retain vanilla flash, sound, and their correct block interaction")

# Powder smoke is delayed, distributed through a sphere, and uses vanilla large smoke.
pin("GUNPOWDER_SMOKE_MIN = 20" in explosive_effects
    and "GUNPOWDER_SMOKE_MAX = 40" in explosive_effects
    and "GUNPOWDER_SMOKE_MAX_DELAY = 2" in explosive_effects,
    "powder schedules 20-40 smoke puffs with up to two ticks of delay")
pin("random.nextInt(GUNPOWDER_SMOKE_MAX - GUNPOWDER_SMOKE_MIN + 1)" in explosive_effects
    and "random.nextInt(GUNPOWDER_SMOKE_MAX_DELAY + 1)" in explosive_effects
    and "ParticleTypes.LARGE_SMOKE" in explosive_effects,
    "powder smoke count, timing, and vanilla particle are honored")
pin("randomPointInSphere(random, radius)" in explosive_effects
    and "randomPointInSphere(random, 0.5D)" in explosive_effects,
    "powder smoke is spherical with at most half-block positional jitter")

# Industrial dust is sampled only from blocks actually affected by this TNT.
pin("event.getAffectedBlocks()" in explosive_effects
    and "affectedBlocks.isEmpty()" in explosive_effects
    and "instanceof IndustrialPrimedTnt" in explosive_effects
    and "level.getBlockState(particle.mustBeAir).isAir()" in explosive_effects,
    "industrial dust requires affected block cells and an air-filled crater")
pin("INDUSTRIAL_DUST_MIN_DELAY = 2" in explosive_effects
    and "INDUSTRIAL_DUST_MAX_DELAY = 5" in explosive_effects
    and "LIFETIME_TICKS = 100" in blast_dust
    and "FADE_IN_TICKS = 3" in blast_dust and "MAX_ALPHA = 0.8F" in blast_dust
    and "this.gravity = 0.03F" in blast_dust
    and "setColor(0.58F, 0.58F, 0.58F)" in blast_dust
    and "fadeOut" in blast_dust
    and "this.quadSize = this.initialSize * (1.0F - 0.68F * lifeProgress)" in blast_dust,
    "industrial dust delay, grey tint, fall, fade, shrink, and five-second life are fixed")
pin('PARTICLE_TYPES.register("dust"' in mod_particles
    and "ModParticles.DUST.get(), IndustrialBlastDustParticle.Provider::new" in particle_client,
    "custom gonzotech:dust particle type uses its client provider")
dust_definition = load(ASSETS / "particles/dust.json")
pin(dust_definition["textures"] == ["gonzotech:dust"],
    "industrial blast dust reuses the registered gonzotech:dust sprite")
pin("NeoForge.EVENT_BUS.register(com.gonzotech.core.event.ExplosiveEffects.class)" in mod_entry,
    "explosion effects and delayed-particle scheduler are registered")

# Assets, loot, and both localizations are present for each placeable block.
en = load(ASSETS / "lang/en_us.json")
ru = load(ASSETS / "lang/ru_ru.json")
for block_id in ("gunpowder_block", "industrial_tnt"):
    state = load(ASSETS / f"blockstates/{block_id}.json")
    pin(set(state["variants"]) == {"unstable=false", "unstable=true"},
        f"blockstate covers both TNT unstable states for {block_id}")
    variants = list(state["variants"].values())
    pin(len({variant["model"] for variant in variants}) == 1,
        f"both unstable states use the same upright model for {block_id}")
    pin(all(not any(key in variant for key in ("x", "y", "uvlock")) for variant in variants),
        f"blockstate does not rotate {block_id}; top stays upward")
    pin((ASSETS / f"items/{block_id}.json").exists(), f"item definition exists for {block_id}")
    pin((DATA / f"loot_table/blocks/{block_id}.json").exists(), f"loot table exists for {block_id}")
    pin(f"block.gonzotech.{block_id}" in en and f"block.gonzotech.{block_id}" in ru,
        f"English and Russian names exist for {block_id}")

# Fixed-up cube models: powder uses a shared top/bottom, while industrial TNT
# has three independent faces. Both use custom 16x16 PNG placeholders.
gunpowder_model = load(ASSETS / "models/block/gunpowder_block.json")
industrial_model = load(ASSETS / "models/block/industrial_tnt.json")
pin(gunpowder_model["parent"] == "minecraft:block/cube_bottom_top",
    "gunpowder block is a fixed upright cube model")
pin(gunpowder_model["textures"].get("side") == "gonzotech:block/gunpowder_block_side"
    and gunpowder_model["textures"].get("top") == "gonzotech:block/gunpowder_block_topbottom"
    and gunpowder_model["textures"].get("bottom") == "gonzotech:block/gunpowder_block_topbottom",
    "gunpowder block uses side and shared topbottom textures")
pin(industrial_model["parent"] == "minecraft:block/cube_bottom_top",
    "industrial TNT is a fixed upright cube model")
pin(industrial_model["textures"].get("side") == "gonzotech:block/industrial_tnt_side"
    and industrial_model["textures"].get("bottom") == "gonzotech:block/industrial_tnt_bottom"
    and industrial_model["textures"].get("top") == "gonzotech:block/industrial_tnt_top",
    "industrial TNT uses separate side, bottom, and top textures")
for texture_id in (
    "gunpowder_block_side", "gunpowder_block_topbottom",
    "industrial_tnt_side", "industrial_tnt_bottom", "industrial_tnt_top",
):
    texture_path = ASSETS / f"textures/block/{texture_id}.png"
    image = texture_path.read_bytes() if texture_path.is_file() else b""
    pin(tuple(image[:8]) == (137, 80, 78, 71, 13, 10, 26, 10),
        f"PNG texture exists for {texture_id}")
    dimensions = struct.unpack(">II", image[16:24]) if len(image) >= 24 else None
    pin(dimensions == (16, 16), f"{texture_id} is a 16x16 Minecraft placeholder")

pin("extends TntBlock" in custom_tnt
    and "extends CustomTntBlock" in gunpowder
    and "extends CustomTntBlock" in industrial
    and "FACING" not in custom_tnt and "DirectionProperty" not in custom_tnt,
    "explosive blocks inherit TNT's non-directional upright placement")

pin("import net.minecraft.world.entity.EquipmentSlot;" in custom_tnt
    and "import net.minecraft.world.item.EquipmentSlot;" not in custom_tnt,
    "flint-and-steel damage uses the 1.21.4 EquipmentSlot package")

version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
pin("mod_version=0.3.180" in version, "version is bumped to 0.3.180")

print(f"OK: {checks} explosives-content checks passed")

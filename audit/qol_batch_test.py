#!/usr/bin/env python3
"""Static regression checks for the QOL batch and the 0.3.212 dead-slime fog rework."""
from itertools import combinations
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
DATA = RES / "data/gonzotech"


def read(path: str) -> str:
    return (JAVA / path).read_text(encoding="utf-8")


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))

# Dead slime uses depth-based world fog with powder-snow visibility, not a screen-space fill.
fog = read("core/client/DeadSlimeFogClient.java")
entry = read("GonzoTechMod.java")
assert "FOG_START = 0.0F" in fog and "FOG_END = 2.0F" in fog
assert "event.setNearPlaneDistance(FOG_START)" in fog
assert "event.setFarPlaneDistance(FOG_END)" in fog
assert "vanilla powder-snow fog interval" in fog
assert "getMapColor(level, cameraPos).col" in fog
assert "ViewportEvent.ComputeFogColor" in fog and "ViewportEvent.RenderFog" in fog
assert "event.setFogShape(FogShape.SPHERE)" in fog and "event.setCanceled(true)" in fog
assert "RenderGuiEvent" not in fog and "graphics.fill(" not in fog
assert "camera.getBlockAtCamera()" in fog
assert "cameraInsideDeadSlime" in fog and "ModBlocks.DEAD_SLIME_BLOCK.get()" in fog
assert "screen_effect_freeze" not in fog
assert "DeadSlimeFogClient.class" in entry

# Sound event volume scales only the sword impact event; no source audio needs editing.
sounds = load(ASSETS / "sounds.json")
assert sounds["sword_impact"]["sounds"][0]["volume"] == 0.75

# Plastic waste: every unordered pair of distinct inputs appears exactly once.
materials = {
    "minecraft:resin_clump", "minecraft:honeycomb", "gonzotech:resin",
    "gonzotech:rubber", "gonzotech:celluloid", "gonzotech:polyethylene",
    "gonzotech:polyvinyl_chloride",
}
recipe_files = sorted((DATA / "recipe").glob("plastic_waste*.json"))
found_pairs = set()
for path in recipe_files:
    recipe = load(path)
    assert recipe["type"] == "minecraft:crafting_shapeless", path.name
    ingredients = recipe["ingredients"]
    selected = [item for item in ingredients if item in materials]
    base = [item for item in ingredients if item not in materials]
    assert len(selected) == 2 and selected[0] != selected[1], path.name
    assert sorted(base) == ["minecraft:clay_ball", "minecraft:slime_ball", "minecraft:slime_ball"], path.name
    assert recipe["result"] == {"id": "gonzotech:plastic_waste", "count": 16}, path.name
    pair = frozenset(selected)
    assert pair not in found_pairs, f"duplicate plastic pair in {path.name}"
    found_pairs.add(pair)
expected_pairs = {frozenset(pair) for pair in combinations(materials, 2)}
assert len(recipe_files) == 21 and found_pairs == expected_pairs
unlocks = read("chalkboard/advancement/RecipeUnlocks.java")
start = unlocks.index("private static final List<String> RECIPES_AFTER_TWENTY_MINUTES")
end = unlocks.index("\n    );", start)
time_gate = set(re.findall(r'"(gonzotech:plastic_waste[^\"]*)"', unlocks[start:end]))
assert time_gate == {"gonzotech:plastic_waste"} | {
    path.stem.replace("plastic_waste", "gonzotech:plastic_waste", 1)
    for path in recipe_files if path.stem != "plastic_waste"
}

# Industrial blasts preserve item drops; tunnel blasts reuse the same filtered dust/visual path.
explosives = read("core/event/ExplosiveEffects.java")
assert "event.getAffectedEntities().removeIf(entity -> entity instanceof ItemEntity)" in explosives
assert "source instanceof IndustrialPrimedTnt" in explosives
assert "source instanceof TunnelPrimedTnt" in explosives
assert "emitDirectionalExplosionParticles(serverLevel)" in read("core/block/TunnelPrimedTnt.java")

# The two resolved design questions are documented: no extra clue cost and no hardcore death patch.
deferred = (ROOT / "docs/DEFERRED-2026-09-22.md").read_text(encoding="utf-8")
assert "отдельной цены нет" in deferred and "смерть" in deferred and "перманентна" in deferred
assert "Туннельный TNT" in load(ASSETS / "lang/ru_ru.json").values()

version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
assert "mod_version=0.3.226" in version
print("QOL batch checks passed: fog, sound, 21 plastic pairs, TNT item protection and design notes")

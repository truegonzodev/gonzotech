#!/usr/bin/env python3
"""0.3.320: real model/item/blockstate links after the author's III retexture.

Checks JSON/resource resolution, not Minecraft rendering. PNG bytes are not pinned
or rewritten; an author retexture at the same valid path remains allowed.
"""
import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gonzotech"

EXPECTED = {
    "bore_pipe": ("cube_column", {"end": "bore_pipe_top", "side": "bore_pipe"}),
    "work_block": ("cube_all", {"all": "work_block"}),
}
for name in ("geophone", "miner_drill", "plotnick", "seismic_source", "third_grinder", "third_accumulator"):
    EXPECTED[name] = ("cube_bottom_top", {"top": name + "_top", "bottom": name + "_top", "side": name})
EXPECTED["third_battery"] = ("cube", {
    "particle": "third_battery", "north": "third_battery_face",
    "south": "third_battery", "east": "third_battery", "west": "third_battery",
    "up": "third_battery_top", "down": "third_battery_bottom",
})
MOVED = ("bore_pipe", "geophone", "miner_drill", "plotnick", "seismic_source", "third_grinder", "work_block")


def check(overrides=None):
    overrides = overrides or {}

    def load(relative):
        if relative in overrides:
            return overrides[relative]
        return json.loads((ASSETS / relative).read_text())

    for name, (parent, slots) in EXPECTED.items():
        target = "gonzotech:block/" + name
        model = load(f"models/block/{name}.json")
        assert model["parent"] == "minecraft:block/" + parent, (name, "geometry parent changed")
        assert model["textures"] == {slot: "gonzotech:block/third/" + texture for slot, texture in slots.items()}, (name, "wrong texture path/face")
        assert load(f"items/{name}.json")["model"] == {"type": "minecraft:model", "model": target}, (name, "inventory model link")
        variants = load(f"blockstates/{name}.json")["variants"]
        if name in ("plotnick", "seismic_source", "third_grinder", "third_accumulator", "third_battery"):
            expected_variants = {"facing=" + face: {"model": target, **({"y": angle} if angle else {})}
                                 for face, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        else:
            expected_variants = {"": {"model": target}}
        assert variants == expected_variants, (name, "blockstate/orientation changed")

    # All production models, not only the seven known stale ones. No Minecraft
    # texture aliases or vanilla references are mistaken for local PNG paths.
    checked = 0
    for path in sorted((ASSETS / "models").rglob("*.json")):
        model = load(path.relative_to(ASSETS).as_posix())
        for ref in model.get("textures", {}).values():
            if not isinstance(ref, str) or not ref.startswith("gonzotech:"):
                continue
            texture = ref.split(":", 1)[1]
            assert texture == texture.lower(), (path, "uppercase texture", ref)
            assert (ASSETS / "textures" / (texture + ".png")).is_file(), (path, "missing texture", ref)
            checked += 1
    return checked


if __name__ == "__main__":
    count = check()
    print(f"Model texture paths PASS: 9 item/blockstate/model chains, {count} local texture slots; no client rendering claim")
    if "--mutation-check" in sys.argv:
        mutations = []
        for name in MOVED:
            relative = f"models/block/{name}.json"
            data = json.loads((ASSETS / relative).read_text())
            slot = next(iter(data["textures"]))
            data["textures"][slot] = data["textures"][slot].replace("block/third/", "block/second/")
            mutations.append(("stale second/ path: " + name, {relative: data}))
        relative = "models/block/third_battery.json"
        data = json.loads((ASSETS / relative).read_text())
        data["textures"]["north"] = data["textures"]["south"]
        mutations.append(("battery face points at valid but wrong PNG", {relative: data}))
        relative = "items/plotnick.json"
        data = json.loads((ASSETS / relative).read_text())
        data["model"]["model"] = "gonzotech:block/missing_plotnick"
        mutations.append(("inventory broken model link", {relative: data}))
        for label, overrides in mutations:
            try:
                check(copy.deepcopy(overrides))
            except AssertionError:
                print("REJECTED: " + label)
            else:
                raise AssertionError("mutation survived: " + label)
        print(f"Mutation sensitivity: {len(mutations)}/{len(mutations)} rejected by resource assertions")

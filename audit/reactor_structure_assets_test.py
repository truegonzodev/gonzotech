#!/usr/bin/env python3
"""Static contracts for reactor forming, shielded port rules, and owned textures."""
import hashlib
import json
import struct
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
ASSETS = ROOT / "src/main/resources/assets/gonzotech"
REACTOR_TEXTURES = ASSETS / "textures/block/third_1reactor"

_ver.at_least("0.3.234", "reactor shell formation and dedicated textures")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load(path: Path):
    return json.loads(read(path))


def png_info(path: Path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    width, height = struct.unpack(">II", data[16:24])
    assert data[24] == 8 and data[25] == 6, f"{path} must be 32-bit RGBA"
    return width, height, hashlib.sha256(data).hexdigest()


structure = read(JAVA / "machines/reactor/ReactorStructure.java")
assert "MIN_SIZE = 4" in structure and "MAX_SIZE = 16" in structure
assert "state.isAir()" in structure and "isAllowedInterior(block)" in structure
assert "THIRD_HEAT_NODE.get()" in structure
assert "THIRD_UNIVERSAL_FLUID_NODE.get()" in structure
assert "THIRD_UNIVERSAL_NODE.get()" in structure
assert "universalNodes < 1 && (heatNodes < 1 || fluidNodes < 1)" in structure
assert "panels != 1" in structure
assert "chunksLoaded(level" in structure
assert "BuiltInRegistries.BLOCK.getKey(block)" in structure
assert "THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()" in structure
assert "BlockEvent.EntityPlaceEvent" in structure
assert "BlockEvent.BreakEvent" in structure

panel = read(JAVA / "core/block/ReactorControlPanelBlock.java")
panel_be = read(JAVA / "machines/block/entity/ReactorControlPanelBlockEntity.java")
casing = read(JAVA / "core/block/ReactorCasingBlock.java")
assert "ReactorStructure.debugReportComponents(level, pos)" in panel
assert "new ReactorControlPanelBlockEntity(pos, state)" in panel
assert "ReactorStructure.panelLoaded" in panel_be and "StructureMin" in panel_be
assert "markValidationPending" in panel_be and "ReactorStructure.restorePanel(serverLevel, this)" in panel_be
assert "panel.clearValidationPending()" in structure
assert "ReactorStructure.partChanged(level, pos)" in casing
assert "ReactorStructure.partRemoved(level, pos)" in casing
assert "ReactorStructure::onBlockPlace" in read(JAVA / "GonzoTechMod.java")
assert "ReactorStructure::onBlockBreak" in read(JAVA / "GonzoTechMod.java")
assert "ReactorCasingBlock.FORMED" in read(JAVA / "machines/client/ctm/TurbineSmartCtmBakedModel.java")

names = (
    "third_uranium_reactor_casing",
    "third_uranium_reactor_control_panel",
    "third_uranium_reactor_fuel_sheath",
)
owned_texture_paths = []
for name in names:
    model = load(ASSETS / f"models/block/{name}.json")
    assert model["parent"] == "minecraft:block/cube_all", name
    texture_path = model["textures"]["all"]
    assert texture_path.startswith("gonzotech:block/third_1reactor/"), (name, texture_path)
    owned_texture_paths.append(texture_path)
    path = REACTOR_TEXTURES / (texture_path.rsplit("/", 1)[1] + ".png")
    assert path.is_file(), path
    width, height, _digest = png_info(path)
    assert (width, height) == (16, 16), f"{path} must be a 16x16 owned block texture"
assert len(set(owned_texture_paths)) == len(names), "reactor blocks must not share texture paths"
assert len({png_info(REACTOR_TEXTURES / (p.rsplit('/', 1)[1] + '.png'))[2]
            for p in owned_texture_paths}) == len(names), "reactor blocks must not duplicate image files"

# The three allowed shielded output nodes also use dedicated full-cube textures on all faces.
node_model_names = ("third_heat_node", "third_universal_fluid_node", "third_universal_node")
node_texture_paths = []
node_cube = load(ASSETS / "models/block/node_cube.json")
assert set(node_cube["elements"][0]["faces"]) == {"down", "up", "north", "south", "west", "east"}
assert all(face["texture"] == "#all" for face in node_cube["elements"][0]["faces"].values())
for name in node_model_names:
    model = load(ASSETS / f"models/block/{name}.json")
    assert model["parent"] == "gonzotech:block/node_cube"
    texture_path = model["textures"]["all"]
    assert texture_path.startswith("gonzotech:block/third/"), (name, texture_path)
    node_texture_paths.append(texture_path)
    path = ASSETS / "textures" / (texture_path.split(":", 1)[1] + ".png")
    assert path.is_file(), path
    assert png_info(path)[:2] == (16, 16), path
assert len(set(node_texture_paths)) == len(node_model_names)
assert len({png_info(ASSETS / "textures" / (p.split(":", 1)[1] + ".png"))[2]
            for p in node_texture_paths}) == len(node_model_names)

# A user-provided filled-casing texture and three distinct CTM roles are mandatory.
blockstate = load(ASSETS / "blockstates/third_uranium_reactor_casing.json")["variants"]
assert blockstate["formed=false"]["model"] == "gonzotech:block/third_uranium_reactor_casing"
assert blockstate["formed=true"]["model"] == "gonzotech:block/third_uranium_reactor_casing_ctm"
ctm = load(ASSETS / "models/block/third_uranium_reactor_casing_ctm.json")
assert ctm["loader"] == "gonzotech:smart_ctm"
textures = ctm["textures"]
assert set(textures) == {"base", "line", "outer_corner", "inner_corner", "particle"}
assert textures["base"].endswith("third_1reactor_casting_formed")
assert textures["particle"] == textures["base"]
assert len({textures[k] for k in ("base", "line", "outer_corner", "inner_corner")}) == 4
ctm_dimensions = {"base": (16, 16), "line": (16, 2), "outer_corner": (2, 2), "inner_corner": (2, 2)}
for slot, dimensions in ctm_dimensions.items():
    texture_id = textures[slot].split(":", 1)[1]
    path = ASSETS / "textures" / f"{texture_id}.png"
    assert path.is_file(), (slot, texture_id)
    assert png_info(path)[:2] == dimensions, (slot, path)

# Removed/relocated texture references must not remain in the reactor models.
reactor_models = "\n".join(read(path) for path in (ASSETS / "models/block").glob("third_uranium_reactor_*.json"))
for stale in (
    "block/first/uranium_reactor_control_panel",
    "block/third_1reactor/casing",
    "block/fullmetall/steel_block",
    "block/fullmetall/uranium_block",
):
    assert stale not in reactor_models, stale

# The corner files are separate CTM slots. Preserve the author's files, but report a byte-identical pair.
outer_hash = png_info(REACTOR_TEXTURES / "1reactor_ctm_outer_corner.png")[2]
inner_hash = png_info(REACTOR_TEXTURES / "1reactor_ctm_inner_corner.png")[2]
if outer_hash == inner_hash:
    print("NOTE: outer/inner CTM corner PNGs are byte-identical; kept as separate required slots, not shared by block models")

print("Reactor structure, formed CTM, and owned texture contracts passed")

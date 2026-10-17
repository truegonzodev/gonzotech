#!/usr/bin/env python3
"""Static regression checks for the reactor panel GUI follow-up."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / "src/main/resources/assets/gonzotech/textures/gui"

ASSETS = {
    "third_uranium_reactor_gui.png": (512, 512),
    "third_uranium_reactor_gui_bg.png": (512, 512),
    "third_uranium_reactor_gui_neo.png": (512, 512),
    "third_uranium_reactor_gui_bg_neo.png": (512, 512),
    "third_uranium_reactor_gui_slot.png": (18, 18),
    "third_uranium_reactor_gui_slot_neo1.png": (18, 18),
}


def parse_transparent_rgba(path: Path, expected_size: tuple[int, int]) -> None:
    data = path.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), f"{path.name}: invalid PNG signature"
    offset = 8
    width = height = bit_depth = color_type = None
    compressed = bytearray()
    saw_iend = False
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        assert crc == zlib.crc32(kind + payload) & 0xFFFFFFFF, f"{path.name}: bad {kind!r} CRC"
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            assert (compression, filtering, interlace) == (0, 0, 0)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            saw_iend = True
            break
        offset += 12 + length

    assert saw_iend, f"{path.name}: missing IEND"
    assert (width, height) == expected_size, f"{path.name}: expected {expected_size}, got {(width, height)}"
    assert (bit_depth, color_type) == (8, 6), f"{path.name}: must be 8-bit RGBA"
    raw = zlib.decompress(compressed)
    row_bytes = width * 4
    assert len(raw) == height * (row_bytes + 1), f"{path.name}: unexpected decoded data length"
    for row in range(height):
        row_start = row * (row_bytes + 1)
        assert raw[row_start] == 0, f"{path.name}: only filter-0 placeholder rows are expected"
        assert not any(raw[row_start + 1:row_start + 1 + row_bytes]), f"{path.name}: placeholder must be fully transparent RGBA"


for filename, size in ASSETS.items():
    parse_transparent_rgba(GUI / filename, size)

panel_block = (ROOT / "src/main/java/com/gonzotech/core/block/ReactorControlPanelBlock.java").read_text()
panel_be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/ReactorControlPanelBlockEntity.java").read_text()
structure = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorStructure.java").read_text()
menu = (ROOT / "src/main/java/com/gonzotech/machines/menu/ReactorMenu.java").read_text()
screen = (ROOT / "src/main/java/com/gonzotech/machines/client/ReactorScreen.java").read_text()
machine_screen = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineScreen.java").read_text()
menu_registry = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModMenus.java").read_text()
client_registry = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineClient.java").read_text()

assert "player.openMenu(panel" in panel_block
assert "!player.isShiftKeyDown() && panel.isFormedController()" in panel_block
assert "ReactorStructure.debugReport(level, pos)" in panel_block
assert "Containers.dropContents(level, pos, panel)" in panel_block

assert "implements WorldlyContainer, MenuProvider" in panel_be
assert "public int[] getSlotsForFace(Direction side)" in panel_be
assert "public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side)" in panel_be
assert "public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side)" in panel_be
assert "MAX_FUEL_SLOTS = 18" in panel_be
assert "sheathCount <= 0 ? 0 : Math.min(MAX_FUEL_SLOTS, sheathCount + 2)" in panel_be
assert "stack.is(ModItems.UF_TVEL.get())" in panel_be
assert 'tag.putInt("FuelSlotCount", fuelSlotCount())' in panel_be
assert "ContainerHelper.saveAllItems(tag, fuelItems, registries)" in panel_be
assert "ContainerHelper.loadAllItems(tag, fuelItems, registries)" in panel_be
assert "new ReactorMenu(id, inventory, this, fuelSlotCount())" in panel_be
assert "panel.configureStructure(info.min(), info.max(), info.sheaths())" in structure

assert "for (int slot = 0; slot < this.activeFuelSlots; slot++)" in menu
assert "return stack.is(ModItems.UF_TVEL.get())" in menu
assert "return panel.isFormedController()" in menu
assert "player.level().isClientSide()" in menu
assert 'MENUS.register("third_uranium_reactor"' in menu_registry
assert "ModMenus.REACTOR.get(), ReactorScreen::new" in client_registry

render_body = machine_screen.split("protected void renderBg(", 1)[1].split("/** Optional overlay", 1)[0]
layer_order = [
    "blitSheet(g, backgroundTexture(), x, y)",
    "drawMachine(g, x, y, mouseX, mouseY)",
    "blitSheet(g, foregroundTexture(), x, y)",
    "drawAfterForeground(g, x, y, mouseX, mouseY)",
]
positions = [render_body.index(token) for token in layer_order]
assert positions == sorted(positions), "machine GUI Z0–Z3 ordering changed"
assert "for (int slot = 3; slot < menu.activeFuelSlots(); slot++)" in screen
assert '"third_uranium_reactor_gui_slot.png"' in screen
assert '"third_uranium_reactor_gui_slot_neo1.png"' in screen

version = (ROOT / "gradle.properties").read_text()
assert "mod_version=0.3.236" in version

print(f"PASS reactor GUI: {len(ASSETS)} transparent RGBA placeholders, menu, NBT inventory, dynamic slots, Z-order and registrations")

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


def parse_visible_rgba(path: Path, expected_size: tuple[int, int]) -> None:
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

    def paeth(a: int, b: int, c: int) -> int:
        p = a + b - c
        distances = (abs(p - a), abs(p - b), abs(p - c))
        return (a, b, c)[distances.index(min(distances))]

    previous = bytearray(row_bytes)
    visible = False
    for row in range(height):
        row_start = row * (row_bytes + 1)
        filter_type = raw[row_start]
        encoded = raw[row_start + 1:row_start + 1 + row_bytes]
        decoded = bytearray(row_bytes)
        for index, value in enumerate(encoded):
            left = decoded[index - 4] if index >= 4 else 0
            up = previous[index]
            upper_left = previous[index - 4] if index >= 4 else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = up
            elif filter_type == 3:
                predictor = (left + up) // 2
            elif filter_type == 4:
                predictor = paeth(left, up, upper_left)
            else:
                raise AssertionError(f"{path.name}: unknown PNG filter {filter_type}")
            decoded[index] = (value + predictor) & 0xFF
        visible = visible or any(decoded[3::4])
        previous = decoded
    assert visible, f"{path.name}: texture must contain at least one visible pixel"


for filename, size in ASSETS.items():
    parse_visible_rgba(GUI / filename, size)

panel_block = (ROOT / "src/main/java/com/gonzotech/core/block/ReactorControlPanelBlock.java").read_text()
panel_be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/ReactorControlPanelBlockEntity.java").read_text()
structure = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorStructure.java").read_text()
menu = (ROOT / "src/main/java/com/gonzotech/machines/menu/ReactorMenu.java").read_text()
screen = (ROOT / "src/main/java/com/gonzotech/machines/client/ReactorScreen.java").read_text()
machine_screen = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineScreen.java").read_text()
menu_registry = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModMenus.java").read_text()
client_registry = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineClient.java").read_text()

sheath_be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/ReactorFuelSheathBlockEntity.java").read_text()
fuel_inventory = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorFuelInventory.java").read_text()
fuel_storage = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorFuelStorage.java").read_text()
sheath_block = (ROOT / "src/main/java/com/gonzotech/core/block/ReactorFuelSheathBlock.java").read_text()
block_entities = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModBlockEntities.java").read_text()

assert "player.openMenu(panel" in panel_block
assert "!player.isShiftKeyDown() && panel.isFormedController()" in panel_block
assert "ReactorStructure.debugReportComponents(level, pos)" in panel_block
assert "canOpenFuelInventory" in panel_block
assert "rawH=" in structure and "rawL=" in structure
assert "Pнагрева=" in structure and "Pполезный=" in structure
assert "ChatFormatting.RED" in structure and "ChatFormatting.GREEN" in structure
assert "Containers.dropContents(level, pos, panel)" in panel_block

assert "implements Container, MenuProvider" in panel_be
assert "MAX_FUEL_SLOTS = 18" in panel_be
assert "sheathCount <= 0 ? 0 : Math.min(MAX_FUEL_SLOTS, sheathCount + 2)" in panel_be
assert "fuelSlotsForSheaths(sheathCount, storedFuel)" in panel_be
assert "legacyFuelItems" in panel_be
assert "ContainerHelper.saveAllItems(tag, legacyFuelItems, registries)" in panel_be
assert "ContainerHelper.loadAllItems(tag, legacyFuelItems, registries)" in panel_be
assert "new ReactorMenu(id, inventory, this, fuelSlotCount())" in panel_be
assert "panel.configureStructure(info.min(), info.max(), info.sheaths(), Math.max(0, storedFuel))" in structure

assert "implements Container" in sheath_be and "MAX_TVEL_SLOTS = 18" in sheath_be
assert "ContainerHelper.saveAllItems(tag, fuelItems, registries)" in sheath_be
assert "ContainerHelper.loadAllItems(tag, fuelItems, registries)" in sheath_be
assert "new ReactorFuelInventory(panel, activeFuelSlots)" in menu
assert "return stack.is(ModItems.UF_TVEL.get())" in menu
assert "int sheathIndex = slot % sheaths.size()" in fuel_inventory
assert "int localSlot = slot / sheaths.size()" in fuel_inventory
assert "collectSheaths(server, panel.min(), panel.max())" in fuel_inventory
assert "ceilDivide(total, sheathCountBeforeBreak)" in fuel_storage
assert "beforeSheathRemoved" in sheath_block
assert "new ReactorFuelSheathBlockEntity(pos, state)" in sheath_block
assert '"third_uranium_reactor_fuel_sheath"' in block_entities
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
assert "mod_version=0.3.239" in version

print(f"PASS reactor GUI: {len(ASSETS)} visible RGBA textures, menu, NBT inventory, dynamic slots, Z-order and registrations")

#!/usr/bin/env python3
"""Pins 0.3.256 — метки автошахтера и Плотник.

Держит: вымпел-метку (квад 1,0×1,75 блока под 20×35, 80% прозрачности,
дисплей 14×14 в «слоте» (3,3) на 100%), листание руд 3 с, /gonzotech debug marker (колонна
1×1 до низа мира, тестовая метка 20 с), GUI Плотника по замеру автора и
хранилище 9×512 активных + 54×512 виртуальных.
"""
import re
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

RES = ROOT / "src/main/resources/assets/gonzotech"
GUI = RES / "textures/gui"
failures = []


def png_size(path: Path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path.name}: not a PNG"
    w, h = struct.unpack(">II", data[16:24])
    return w, h


# ── версия ──
props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
assert re.search(r"^mod_version=0\.3\.256$", props, re.M), "mod_version != 0.3.256"

# ── 1. Текстуры автора: вымпел 20×35, флажок 14×14 ──
assert png_size(GUI / "waypoint_marker.png") == (20, 35), "waypoint_marker.png != 20x35"
assert png_size(GUI / "marker_flag.png") == (14, 14), "marker_flag.png != 14x14"

# ── 2. GUI Плотника: classic + neo-копии ──
for name in ("plotnick_gui.png", "plotnick_gui_bg.png"):
    assert (GUI / "classic" / name).is_file(), f"нет classic {name}"
    neo = GUI / "neo" / name.replace(".png", "_neo.png")
    assert neo.is_file(), f"нет neo-копии {neo.name}"
    assert neo.read_bytes() == (GUI / "classic" / name).read_bytes(), f"{neo.name} != classic"

# ── 3. Рендер метки (MarkerRenderer) ──
ren = (ROOT / "src/main/java/com/gonzotech/machines/client/MarkerRenderer.java").read_text(encoding="utf-8")
assert "QUAD_HALF_W = 0.5F" in ren, "полуширина канвы != 0.5 (квад 1.0 блока)"
assert "QUAD_H = 1.75F" in ren, "высота канвы != 1.75 блока (пропорция 20:35)"
assert "QUAD_ALPHA = 0.8F" in ren, "альфа канвы != 0.8"
assert "SLOT_OFFSET_X = (3F / 20F)" in ren, "слот дисплея не в пикселе (3,3) канвы 20×35"
assert "SLOT_SIZE_X = (14F / 20F)" in ren, "иконка дисплея не 14×14 пикселей"
assert "SLOT_SIZE_Y = (14F / 35F)" in ren, "высота иконки не 14 пикселей канвы"
assert "ORE_SCALE = 0.65F" in ren, "изометрия руды не вписана в слот 0.7 блока"
assert "RenderSystem.depthMask(false)" in ren, "прозрачные края метки пишут глубину"
assert "прозрачные пиксели, иначе они рисуются чёрными полями" in ren, "фикс блендинга флажка потерян"
assert "ORE_CYCLE_TICKS = 60L" in ren, "листание руд != 60 тиков (3 с)"
assert "ItemDisplayContext.GUI" in ren, "руда не рисуется изометрией GUI"
assert "AFTER_PARTICLES" in ren, "полупрозрачные вымпелы не на стадии AFTER_PARTICLES"
assert "RenderSystem.disableCull()" in ren, "билборд метки зависит от одностороннего culling"
assert "MAX_DISTANCE_SQR = 512.0 * 512.0" in ren, "отсечение не 32 чанка (512 блоков)"

# ── 4. Виды меток и сеть ──
kind = (ROOT / "src/main/java/com/gonzotech/machines/miner/MinerMarker.java").read_text(encoding="utf-8")
assert re.search(r"enum Kind\s*\{[^}]*STATION[^}]*ORE[^}]*TEST", kind), "Kind != STATION/ORE/TEST"
service = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerService.java").read_text(encoding="utf-8")
assert "gonzotech_miner_markers" in service, "имя SavedData сдвинулось"
assert "removeAll(ServerLevel level, Collection<Long> ids)" in service, "нет пакетного удаления меток по id"
assert "removeStationMarkersInSector" in service, "нет уборки legacy-меток сектора"
net = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerNetwork.java").read_text(encoding="utf-8")
assert "MarkersPayload" in net and "broadcast" in net and "snapshot" in net, "MarkerNetwork потерял API"

# ── 5. /gonzotech debug marker ──
dbg = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerDebugCommand.java").read_text(encoding="utf-8")
assert "TEST_MARKER_TTL_TICKS = 20 * 20" in dbg, "тестовая метка живёт не 20 секунд"
assert "literal(\"marker\")" in dbg, "команда /gonzotech debug marker исчезла"
# колонна 1×1: скан только по x/z ног игрока
assert "for (int y = feet.getY() - 1; y >= minY; y--)" in dbg, "скан не сверху вниз"
assert "new BlockPos(feet.getX(), y, feet.getZ())" in dbg, "скан не колонной 1×1"

# ── 6. Плотник: блок/BE/хранилище ──
be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/PlotnickBlockEntity.java").read_text(encoding="utf-8")
assert "SLOTS_ACTIVE = 9" in be, "активных слотов != 9"
assert "VIRTUAL_SLOTS = 54" in be, "виртуальных стаков != 54"
assert "MAX_STACK = 512" in be, "стак Плотника != 512"
assert be.index("SLOTS_ACTIVE") < be.index("VIRTUAL_SLOTS"), "порядок констант сбился"
assert "BTN_MARK = 4" in be, "кнопка «Отметить» != id 4"
assert "case BTN_PACK_UP -> clearSectorMarks(server);" in be, "«Свернуться» не убирает разметку"
assert "placeSectorMarks" in be, "разметка сектора 3×3 чанка пропала"
assert "Kind.STATION" in be, "метки разметки не STATION"
assert "Mth.clamp(base.getX(), targetChunkMinX, targetChunkMinX + 15)" in be, "X метки не ближайший столбец целевого чанка"
assert "Mth.clamp(base.getZ(), targetChunkMinZ, targetChunkMinZ + 15)" in be, "Z метки не ближайший столбец целевого чанка"
assert "localX < 15 ? base.getX() + 1 : base.getX() - 1" in be, "центральная метка может уйти в соседний чанк"
assert "sectorMarkerIds.add(marker.id())" in be, "Плотник не запоминает свои метки"
assert '"SectorMarkers"' in be, "id меток Плотника не персистентны"
assert "ContainerHelper.saveAllItems(tag, items, registries)" in be, "пустые активные слоты снова кодируются вручную"
assert "ContainerHelper.loadAllItems(tag, items, registries)" in be, "загрузка активных слотов не парная сохранению"
plotnick_block = (ROOT / "src/main/java/com/gonzotech/machines/block/PlotnickBlock.java").read_text(encoding="utf-8")
assert "plotnick.onBroken();" in plotnick_block, "слом Плотника не убирает его разметку"

# ── 7. GUI Плотника по замеру автора ──
menu = (ROOT / "src/main/java/com/gonzotech/machines/menu/PlotnickMenu.java").read_text(encoding="utf-8")
assert "IMAGE_WIDTH = 176" in menu and "IMAGE_HEIGHT = 180" in menu, "окно GUI != 176x180"
assert "8 + 18 * k, 76" in menu, "активные слоты не в ряд y=76"
assert "SLOT_CARD_A, 98, 36" in menu, "карта A не в (98,36)"
assert "SLOT_CARD_B, 116, 36" in menu, "карта B не в (116,36)"
assert "addPlayerInventory(inventory, 8, 98)" in menu, "инвентарь не в (8,98)"
screen = (ROOT / "src/main/java/com/gonzotech/machines/client/PlotnickScreen.java").read_text(encoding="utf-8")
assert "return -128" in screen and "return -114" in screen, "смещение листа != (-128,-114)"
for cell in ("{26, 18}", "{44, 18}", "{26, 36}", "{44, 36}", "{26, 54}", "{44, 54}"):
    assert cell in screen, f"кнопка {cell} сдвинулась"
assert "PlotnickButtonPayload" in screen, "кнопки GUI не отправляются на сервер"

# ── 8. Регистрации ──
machines = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModMachines.java").read_text(encoding="utf-8")
assert 'registerBlock("plotnick"' in machines, "блок plotnick не зарегистрирован"
menus = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModMenus.java").read_text(encoding="utf-8")
assert 'register("plotnick"' in menus, "меню plotnick не зарегистрирован"
main = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
assert "MarkerDebugCommand::onRegisterCommands" in main, "команда меток не подключена"
assert "MarkerEvents.class" in main, "события меток не подключены"
assert "MarkerRenderer.class" in main, "рендерер меток не подключён"
assert "MarkersPayload.TYPE" in main, "пейлоад меток не зарегистрирован"
assert "PlotnickButtonPayload" in main, "пакет кнопок Плотника не зарегистрирован"

print("autominer_markers_test passed")

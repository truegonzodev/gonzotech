#!/usr/bin/env python3
"""Pins 0.3.256-0.3.257 — метки автошахтера, Плотник, сеть и сейсмика.

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
assert re.search(r"^mod_version=0\.3\.257$", props, re.M), "mod_version != 0.3.257"

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
assert "ORE_ICON_SCALE = 0.65F" in ren, "иконка руды не уменьшена до 65% слота"
assert "left = centerX - halfWidth" in ren and "top = centerY + halfHeight" in ren, "иконка руды не центрирована в рамке"
assert "DISPLAY_Z = 0.02F" in ren, "плоская иконка не зафиксирована в плоскости дисплея"
assert "drawFlatOreIcon" in ren, "нет плоской 2D-изометрии руды"
assert "addFlatFace" in ren, "грани 2D-изометрии не собираются плоскими квадами"
assert "points[i * 2 + 1], DISPLAY_Z" in ren, "вершины иконки не лежат в одной Z-плоскости"
assert "model.getQuads" in ren, "спрайты граней не берутся из baked-модели блока"
for face in ("Direction.UP", "Direction.WEST", "Direction.SOUTH"):
    assert face in ren, f"нет грани {face} у плоской изометрии"
assert ".renderStatic(" not in ren, "руда снова рисуется настоящей 3D-моделью предмета"
assert "ItemDisplayContext" not in ren, "остался GUI-рендер 3D-модели руды в мире"
assert "RenderSystem.depthMask(false)" in ren, "прозрачные края метки пишут глубину"
assert "прозрачные пиксели, иначе они рисуются чёрными полями" in ren, "фикс блендинга флажка потерян"
assert "ORE_CYCLE_TICKS = 60L" in ren, "листание руд != 60 тиков (3 с)"
assert "AFTER_PARTICLES" in ren, "полупрозрачные вымпелы не на стадии AFTER_PARTICLES"
assert "RenderSystem.disableCull()" in ren, "билборд метки зависит от одностороннего culling"
assert "MAX_DISTANCE_SQR = 512.0 * 512.0" in ren, "отсечение не 32 чанка (512 блоков)"
assert "marker.kind() == MinerMarker.Kind.STATION" in ren, "старые STATION-метки не получают иконку источника"
assert "ModMachines.SEISMIC_SOURCE_ITEM.get()" in ren, "метка чанка не показывает источник колебаний"

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
for button_pin in ("BTN_DRILL = 0", "BTN_SEISMO = 1", "BTN_NETWORK = 2",
                   "BTN_MARK = 3", "BTN_PACK_UP = 4", "BTN_DYNAMITE = 5"):
    assert button_pin in be, f"раскладка кнопок Плотника сбилась: {button_pin}"
assert "case BTN_PACK_UP -> packUp(server);" in be, "«Свернуться» не убирает разметку"
assert "case BTN_NETWORK -> buildNetwork(server, requester);" in be, "«Построить связи» не запускает сеть"
assert "case BTN_SEISMO -> seismicImpulse(server, requester);" in be, "«Произвести импульс» не подключён"
assert "placeSectorMarks" in be, "разметка сектора 3×3 чанка пропала"
assert "Kind.STATION" in be, "метки разметки не STATION"
assert "Mth.clamp(base.getX(), targetChunkMinX, targetChunkMinX + 15)" in be, "X метки не ближайший столбец целевого чанка"
assert "Mth.clamp(base.getZ(), targetChunkMinZ, targetChunkMinZ + 15)" in be, "Z метки не ближайший столбец целевого чанка"
assert "localX < 15 ? base.getX() + 1 : base.getX() - 1" in be, "центральная метка может уйти в соседний чанк"
assert "sectorMarkerIds.add(marker.id())" in be, "Плотник не запоминает свои метки"
assert '"SectorMarkers"' in be, "id меток Плотника не персистентны"
assert "ContainerHelper.saveAllItems(tag, items, registries)" in be, "пустые активные слоты снова кодируются вручную"
assert "ContainerHelper.loadAllItems(tag, items, registries)" in be, "загрузка активных слотов не парная сохранению"
assert "instanceof SeismicSourceBlockEntity" in be, "разведка не ищет источники по чанкам"
assert "sourcePos.above()" in be, "геофон не проверяется непосредственно над источником"
assert "ModMachines.GEOPHONE.get()" in be, "разведка не считает геофоны"
assert "List.of(ModMachines.SEISMIC_SOURCE_ITEM.get())" in be, "метка чанка без источника не несёт его изометрию"
assert "List.of(ModMachines.GEOPHONE_ITEM.get())" in be, "метка неполного источника не несёт изометрию геофона"
assert "message.gonzotech.plotnick.survey" in be, "нет отчёта разведки в чат"
assert "requester.sendSystemMessage(message)" in be, "отчёт разведки не уходит нажавшему игроку"
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
assert "BUTTON_TOOLTIPS" in screen, "у кнопок Плотника нет названий-тултипов"
for tooltip_key in ("button.drill", "button.impulse", "button.network",
                    "button.mark", "button.pack_up", "button.tnt"):
    assert tooltip_key in screen, f"нет тултипа кнопки {tooltip_key}"
assert "Component.translatable(BUTTON_TOOLTIPS[i])" in screen, "тултип кнопки не рендерится при наведении"
payload = (ROOT / "src/main/java/com/gonzotech/machines/menu/PlotnickButtonPayload.java").read_text(encoding="utf-8")
assert "handleButtonClick(payload.buttonId(), player)" in payload, "игрок-инициатор не передаётся в отчёт разведки"

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

# ── 9. Рабочий блок и сейсмическая пара «геофон над источником» ──
for block_id in ("work_block", "seismic_source", "geophone"):
    assert f'registerBlock("{block_id}"' in machines, f"блок {block_id} не зарегистрирован"
    assert f'registerSimpleBlockItem("{block_id}"' in machines, f"предмет {block_id} не зарегистрирован"
    for rel in (f"blockstates/{block_id}.json", f"models/block/{block_id}.json", f"items/{block_id}.json"):
        assert (RES / rel).is_file(), f"нет ресурса {rel}"
    loot = ROOT / "src/main/resources/data/gonzotech/loot_table/blocks" / f"{block_id}.json"
    assert loot.is_file(), f"нет лута блока {block_id}"
for texture in ("work_block.png", "seismic_source.png", "seismic_source_top.png",
                "geophone.png", "geophone_top.png"):
    assert png_size(RES / "textures/block/second" / texture) == (16, 16), f"{texture} != 16x16"
for name in ("seismic_source_gui.png", "seismic_source_gui_bg.png"):
    assert png_size(GUI / "classic" / name) == (512, 512), f"{name} != 512x512"
    neo = GUI / "neo" / name.replace(".png", "_neo.png")
    assert neo.is_file(), f"нет neo-копии {neo.name}"
    assert neo.read_bytes() == (GUI / "classic" / name).read_bytes(), f"{neo.name} != classic"

block_entities = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModBlockEntities.java").read_text(encoding="utf-8")
assert 'register("seismic_source"' in block_entities, "BlockEntity источника не зарегистрирован"
assert 'register("seismic_source"' in menus, "меню источника не зарегистрировано"
machine_client = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineClient.java").read_text(encoding="utf-8")
assert "ModMenus.SEISMIC_SOURCE.get(), SeismicSourceScreen::new" in machine_client, "экран источника не зарегистрирован"
source_be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/SeismicSourceBlockEntity.java").read_text(encoding="utf-8")
assert "CONTAINER_SIZE = 1" in source_be, "у источника не один слот"
assert "stack.is(Items.TNT)" in source_be, "источник принимает не только динамит"
assert "public int getMaxStackSize()" in source_be and "return 1;" in source_be, "слот источника вмещает больше одного динамита"
source_menu = (ROOT / "src/main/java/com/gonzotech/machines/menu/SeismicSourceMenu.java").read_text(encoding="utf-8")
assert "DynamiteSlot" in source_menu and "SeismicSourceBlockEntity.isDynamite(stack)" in source_menu, "GUI источника не фильтрует динамит"
assert ", 80, 35" in source_menu, "слот источника не в центре GUI"
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("block.gonzotech.work_block", "block.gonzotech.seismic_source",
                "block.gonzotech.geophone", "gui.gonzotech.plotnick.button.mark",
                "message.gonzotech.plotnick.survey"):
        assert key in lang, f"в {lang_name} нет ключа {key}"

# ── 10. Сеть 512, рабочие блоки, буры и карта памяти (0.3.257) ──
assert "getMaxStackSize()" in menu and "MAX_STACK" in menu, "активные слоты Плотника не поднимают стак до 512"
assert "CardSlot" in menu and "MEMORY_CARD.get()" in menu, "слоты карт не ограничены картой памяти"
assert "hasSectorCard()" in menu, "GUI не получает состояние Плотника для названий кнопок"
assert "BUTTON_DESCRIPTIONS" in screen, "у кновок нет текущих описаний в тултипах"
for desc_key in ("button.drill.desc", "button.impulse.desc", "button.network.sources",
                 "button.network.drills", "button.mark.sector", "button.mark.ores",
                 "button.pack_up.desc", "button.tnt.desc"):
    assert desc_key in screen, f"нет описания кнопки {desc_key}"
assert "menu.hasSectorCard()" in screen, "тултип не переключается на рудный режим по состоянию Плотника"

planner = (ROOT / "src/main/java/com/gonzotech/machines/miner/NetworkPlanner.java").read_text(encoding="utf-8")
assert "GRASS_BLOCK" in planner and "SNOW" in planner and "state.isAir()" in planner, \
    "дорожка теряет правило воздух/трава/снег"
assert "BFS_NODE_BUDGET" in planner, "у BFS нет лимита паутины"
assert "connectedWorkRegion" in planner, "нет переиспользования уже построенных ветвей"
assert "isBuildable" in planner, "solid-блоки не защищены от замены дорожкой"
assert "unreachable" in planner, "заставленные цели не исключаются из плана"

assert "be.processBuildQueue(server)" in be, "тики Плотника не расходуют очередь постройки"
assert '"message.gonzotech.plotnick.network.planned"' in be, "нет отчёта о кэшированном плане постройки"
assert "placeCooldown = 4 + server.random.nextInt(5)" in be, "между блоками нет случайной задержки 4-8 тиков"
assert "SoundType.METAL.getPlaceSound()" in be, "установка блоков сети не озвучена"
assert "ParticleTypes.BLOCK" in be, "установка блоков сети не посыпается частицами разрушения"
assert "buildQueue.add(0, entry)" in be, "нехватка рабочих блоков не сохраняет очередь на месте"
assert '"BuildQueue"' in be and '"DrillPoints"' in be and '"PlaceCooldown"' in be, \
    "очередь постройки/точки бурения не персистентны"
assert "MemoryCardItem.isFilled" in be, "режим кнопок не определяется состоянием карты"
assert '"message.gonzotech.plotnick.ore.done"' in be, "нет отчёта рудной разведки"
assert "pick < 9" in be, "на чанк приходится больше девяти точек бурения"
assert "MinerMarker.Kind.ORE" in be, "рудные точки не помечаются ORE-метками"
assert "ParticleTypes.DUST_PILLAR" in be, "импульс не поднимает пол вокруг источника"
assert "ModSounds.GEO_IMPACT.get()" in be, "импульс не играет geo_impact"
assert "source.setItem(SeismicSourceBlockEntity.SLOT_DYNAMITE, ItemStack.EMPTY)" in be, \
    "импульс не расходует динамит источника"
assert "MemoryCardItem.writeSector" in be, "импульс не записывает сектор на карту"
assert "countStored(ModMachines.WORK_BLOCK.get().asItem())" in be, "постройка не считает рабочие блоки в хранилище"

card = (ROOT / "src/main/java/com/gonzotech/machines/item/MemoryCardItem.java").read_text(encoding="utf-8")
assert "MinerSector" in card and "getIntArray" in card, "сектор карты не в NBT-компоненте"
assert "item.gonzotech.memory_card.sector" in card, "у заполненной карты нет описания сектора"

for block_id in ("miner_drill",):
    assert f'registerBlock("{block_id}"' in machines, f"блок {block_id} не зарегистрирован"
    assert f'registerSimpleBlockItem("{block_id}"' in machines, f"предмет {block_id} не зарегистрирован"
    for rel in (f"blockstates/{block_id}.json", f"models/block/{block_id}.json", f"items/{block_id}.json"):
        assert (RES / rel).is_file(), f"нет ресурса {rel}"
    assert (ROOT / "src/main/resources/data/gonzotech/loot_table/blocks" / f"{block_id}.json").is_file(), \
        f"нет лута блока {block_id}"
    assert f"gonzotech:{block_id}" in (ROOT / "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json") \
        .read_text(encoding="utf-8"), f"блок {block_id} не в теге кирки"
assert 'registerItem("memory_card"' in machines, "карта памяти не зарегистрирована"
assert (RES / "items/memory_card.json").is_file(), "нет item-модели карты памяти"
assert (RES / "models/item/memory_card.json").is_file(), "нет модели предмета карты памяти"
for texture in ("miner_drill.png", "miner_drill_top.png"):
    assert png_size(RES / "textures/block/second" / texture) == (16, 16), f"{texture} != 16x16"
assert png_size(RES / "textures/item/memory_card.png") == (16, 16), "memory_card.png != 16x16"
sounds = (RES / "sounds.json").read_text(encoding="utf-8")
assert "geo_impact" in sounds, "geo_impact не объявлен в sounds.json"
mod_sounds = (ROOT / "src/main/java/com/gonzotech/core/registry/ModSounds.java").read_text(encoding="utf-8")
assert 'sound("geo_impact")' in mod_sounds, "GEO_IMPACT не зарегистрирован"
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("block.gonzotech.miner_drill", "item.gonzotech.memory_card",
                "message.gonzotech.plotnick.network.planned", "message.gonzotech.plotnick.impulse.done",
                "message.gonzotech.plotnick.ore.done", "gui.gonzotech.plotnick.button.network.drills",
                "gui.gonzotech.plotnick.button.mark.ores"):
        assert key in lang, f"в {lang_name} нет ключа {key}"

print("autominer_markers_test passed")

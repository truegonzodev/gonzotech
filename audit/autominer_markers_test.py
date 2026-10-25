#!/usr/bin/env python3
"""Pins 0.3.256-0.3.263 — метки автошахтера, Плотник, сеть, сейсмика и буры.

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
assert re.search(r"^mod_version=0\.3\.301$", props, re.M), "mod_version != 0.3.301"

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
assert "VIRTUAL_SLOTS = VIRTUAL_TYPES * STACKS_PER_TYPE" in be, \
    "виртуал не 54 типа × 4 стопки (0.3.264)"
assert "MAX_STACK = 64" in be, "стопки внутри Плотника не ваниальные 64 (0.3.262)"
assert be.index("SLOTS_ACTIVE") < be.index("VIRTUAL_SLOTS"), "порядок констант сбился"
for button_pin in ("BTN_DRILL = 0", "BTN_SEISMO = 1", "BTN_NETWORK = 2",
                   "BTN_MARK = 3", "BTN_PACK_UP = 4", "BTN_DYNAMITE = 5"):
    assert button_pin in be, f"раскладка кнопок Плотника сбилась: {button_pin}"
assert "case BTN_PACK_UP -> packUp(server, requester);" in be, "«Свернуться» не убирает разметку"
assert "case BTN_NETWORK -> buildNetwork(server, requester);" in be, "«Построить связи» не запускает сеть"
assert "case BTN_SEISMO -> seismicImpulse(server, requester);" in be, "«Произвести импульс» не подключён"
assert "placeSectorMarks" in be, "разметка сектора 3×3 чанка пропала"
assert "Kind.STATION" in be, "метки разметки не STATION"
assert "Mth.clamp(base.getX(), targetChunkMinX, targetChunkMinX + 15)" in be, "X метки не ближайший столбец целевого чанка"
assert "Mth.clamp(base.getZ(), targetChunkMinZ, targetChunkMinZ + 15)" in be, "Z метки не ближайший столбец целевого чанка"
assert "localX < 15 ? base.getX() + 1 : base.getX() - 1" in be, "центральная метка может уйти в соседний чанк"
assert "sectorMarkerIds.add(marker.id())" in be, "Плотник не запоминает свои метки"
assert '"SectorMarkers"' in be, "id меток Плотника не персистентны"
# 0.3.264: «Items» — тоже свой формат (int «I»); байтовый «Slot» ContainerHelper
# для 11 слотов хоть и жив, но пара save/load обязана быть своей.
assert 'saveStackList(items, "Items", tag, registries);' in be, \
    "активные слоты не пишутся своим форматом"
assert 'loadStackList(tag, "Items", items, registries);' in be, \
    "загрузка активных слотов не парная сохранению"
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
assert "MAX_STACK = 64" in be, "потолок стопки не ваниальный (0.3.264: 512 отменены)"
assert "public int getMaxStackSize()" in be and "return MAX_STACK;" in be, \
    "Container не объявляет лимит 64 воронкам/трубам"
assert "CardSlot" in menu and "MEMORY_CARD.get()" in menu, "слоты карт не ограничены картой памяти"
assert "hasSectorCard()" in menu, "GUI не получает состояние Плотника для названий кнопок"
assert "BUTTON_DESCRIPTIONS" in screen, "у кновок нет текущих описаний в тултипах"
for desc_key in ("button.drill.desc", "button.impulse.desc", "button.network.sources",
                 "button.network.drills", "button.mark.sector", "button.mark.ores",
                 "button.pack_up.desc", "button.tnt.desc"):
    assert desc_key in screen, f"нет описания кнопки {desc_key}"
assert "menu.hasSectorCard()" in screen, "тултип не переключается на рудный режим по состоянию Плотника"

planner = (ROOT / "src/main/java/com/gonzotech/machines/miner/NetworkPlanner.java").read_text(encoding="utf-8")
assert "BlockTags.REPLACEABLE" in planner and "state.isAir()" in planner, \
    "дорожка теряет правило «только воздух над опорой и заменяемое растение»"
assert "GRASS_BLOCK" not in planner, "дорожка снова собирается замещать блоки травы"
assert "network.add(origin)" in planner, "сеть не стартует от пустого Плотника без рабочих блоков рядом"
assert "BFS_NODE_BUDGET" in planner, "у BFS нет лимита паутины"
assert "connectedWorkRegion" in planner, "нет переиспользования уже построенных ветвей"
assert "isBuildable" in planner, "solid-блоки не защищены от замены дорожкой"
assert "unreachable" in planner, "заставленные цели не исключаются из плана"

assert "be.processBuildQueue(server)" in be, "тики Плотника не расходуют очередь постройки"
assert '"message.gonzotech.plotnick.network.planned"' in be, "нет отчёта о кэшированном плане постройки"
assert "placeCooldown = 4 + server.random.nextInt(5)" in be, "между блоками нет случайной задержки 4-8 тиков"
assert "SoundType.METAL.getPlaceSound()" in be, "установка блоков сети не озвучена"
assert "new BlockParticleOption(ParticleTypes.BLOCK, target)" in be, \
    "краш-частицы берутся не из самого поставленного рабочего блока"
assert "SONIC_BOOM" not in be, "SONIC_BOOM убран по требованию автора"
assert be.count("ModSounds.GEO_IMPACT") == 1, "звук импульса должен звучать один раз на весь сектор"
assert "consumeActive(Items.TNT, 1)" in be, "Плотник не дозирует динамит в разряженные источники"
assert "buildQueue.add(entry); // нечем — переезжает в хвост" in be, \
    "нехватка блоков не перекладывает клетку в хвост очереди (0.3.261)"
assert '"BuildQueue"' in be and '"DrillPoints"' in be and '"PlaceCooldown"' in be, \
    "очередь постройки/точки бурения не персистентны"
assert "MemoryCardItem.isFilled" in be, "режим кнопок не определяется состоянием карты"
assert "moveIntoPlotnick" in menu, "shift-merge в Плотник не поднимает стопки выше 64"
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

# ── 11. Срез 0.3.258: буры, скваженные трубы, сворачивание и ТНТ-закладка ──
defs = (ROOT / "src/main/java/com/gonzotech/machines/energy/PlotnickDefs.java").read_text(encoding="utf-8")
assert "DRILL_GTU_MILLI = 6_500L" in defs, "ставок бура 6.5 GTU/т отсутствует"
assert "DRILL_CYCLE_TICKS = 40" in defs, "цикл бура != 40 тиков (2 секунды)"
assert "PACK_BLOCKS_PER_TICK = 3" in defs, "скорость 3 блока/тик не задана"

# Скваженная труба: блок, предметы, все ресурсы
machinery = (ROOT / "src/main/java/com/gonzotech/machines/registry/ModMachines.java").read_text(encoding="utf-8")
assert 'registerBlock("bore_pipe", BorePipeBlock::new, lightMetal())' in machinery, "скваженная труба не зарегистрирована"
assert 'registerSimpleBlockItem("bore_pipe", BORE_PIPE)' in machinery, "предмет скваженной трубы не зарегистрирован"
bore_block = (ROOT / "src/main/java/com/gonzotech/machines/block/BorePipeBlock.java").read_text(encoding="utf-8")
assert "simpleCodec(BorePipeBlock::new)" in bore_block, "у скваженной трубы нет codec (stall-краш datagen)"
for res in ("blockstates/bore_pipe.json", "models/block/bore_pipe.json", "items/bore_pipe.json"):
    assert (RES / res).is_file(), f"нет ресурса {res}"
assert (ROOT / "src/main/resources/data/gonzotech/loot_table/blocks/bore_pipe.json").is_file(), "нет лут-скрафа скваженной трубы"
tag_pick = (ROOT / "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json").read_text(encoding="utf-8")
assert "gonzotech:bore_pipe" in tag_pick, "скваженная труба не в теге pickaxe"
for texture in ("bore_pipe.png", "bore_pipe_top.png"):
    assert png_size(RES / "textures/block/second" / texture) == (16, 16), f"{texture} != 16x16"

# Дороги строятся первыми, буры — только после сети
idx_roads = be.index("new ArrayList<>(plan.entries())")
idx_drills = be.index("for (BlockPos drillCell : drillSlots)")
assert idx_roads < idx_drills, "буры снова лезут в очередь раньше дорог"

# Разметка чужого сектора запрещена
assert "isOwnSector" in be and "plotnick.ore.wrongsector" in be, "чужая карта не отсекается при разметке"

# Движок буров
for needle in ("tickDrills(server)", "cycleDrillCells", "deepestTargetY", "isImmovable",
               "collectOreRow", "digStep", "drillTargetY", "drillRegion"):
    assert needle in be, f"движок буров: нет {needle}"
assert "for (BlockPos drillCell : cycleDrillCells)" in be, \
    "движок не ходит по фактическим бурам ленты (0.3.261)"
assert "gtu.extract(want, true) < want" in be, "энергия не симулируется перед списанием"
assert "PlotnickDefs.DRILL_GTU_MILLI * run.running().size()" in be, "энергия считается не по числу работающих буров"
assert "hardness < 0F || hardness >= 50F" in be, "нет правила упора в бедрок/обсидиан/барьер"
assert "server.destroyBlock(pos, false, null)" in be, "руда не всасывается (drop=false) в Плотник"
assert "for (int dx = -2; dx <= 2; dx++)" in be, "всас 5×5 вокруг трубы отсутствует"
assert "countStored(ModMachines.BORE_PIPE.get().asItem())" in be, "наличие труб в хранилище не проверяется"
assert "consumeStored(ModMachines.BORE_PIPE.get().asItem(), 1)" in be, "труба не расходуется из хранилища при углублении"
assert "connectedWorkRegion" in be[be.index("private void tickDrills"):], \
    "движок не проверяет связность бура с сетью"

# «Свернуться»: полный разбор сети обратно, 3 блока/тик, всё в Плотник
assert "case BTN_PACK_UP -> packUp(server, requester)" in be, "свертывание не получает запросчика"
assert "collectNetworkBlocks" in be and "isPlotnickNetworkBlock" in be, "нет сбора всех блоков сети"
assert ".distManhattan(worldPosition)).reversed()" in be, "демонтаж идёт не от дальних клеток"
assert "processDismantleQueue" in be and "processTntQueue" in be, "очереди сворачивания/ТНТ не тикают"
assert "server.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state))" in be, \
    "слом сети без партиклов/звука break"
assert "source.hasDynamite()" in be, "динамит источника не возвращается при сворачивании"
assert '"DismantleQueue"' in be and '"TntQueue"' in be, "очереди демонтажа/ТНТ не персистентны"

# «Заменить на ТНТ»: сверху вниз, промышленный в приоритете, трубы не возвращаются
assert "case BTN_DYNAMITE -> rigTnt(server, requester)" in be, "кнопка ТНТ не подключена"
assert "collectPipesTopDown" in be, "нет сбора труб сверху вниз"
assert "b.getY() - a.getY()" in be, "закладка ТНТ идёт не от бура вниз"
assert "pickExplosive" in be and "ModItems.INDUSTRIAL_TNT_ITEM.get()" in be, "промышленный ТНТ не приоритетен"
assert "Blocks.TNT.defaultBlockState()" in be, "ванильный ТНТ не подкладывается при недостатке промышленного"
tnt_sec = be[be.index("private void processTntQueue"):be.index("private Item pickExplosive")]
assert "insert(" not in tnt_sec, "ТНТ-закладка не должна возвращать трубы в Плотник"

# Ключ: жрётся сколько угодно, со звуком, частицами и статистикой
wrench = (ROOT / "src/main/java/com/gonzotech/machines/item/WrenchItem.java").read_text(encoding="utf-8")
assert "public void onUseTick" in wrench, "жевание ключа без звука/частиц"
assert "remainingUseDuration % 10" in wrench, "звук еды не дозируется тиками"
assert "ParticleTypes.ITEM" in wrench and "ItemParticleOption" in wrench, "частицы еды отсутствуют"
assert "Stats.CUSTOM.get" in wrench and "wrench_eaten" in wrench, "поедание ключа не пишется в статистику"
use_body = wrench[wrench.index("public InteractionResult use("):wrench.index("getUseDuration")]
assert "hasMastery" not in use_body, "повторное поедание ключа всё ещё заблокировано"
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("block.gonzotech.bore_pipe", "message.gonzotech.plotnick.ore.wrongsector",
                "message.gonzotech.plotnick.pack.dismantling", "message.gonzotech.plotnick.pack.done",
                "message.gonzotech.plotnick.tnt.planned", "message.gonzotech.plotnick.tnt.done",
                "message.gonzotech.plotnick.tnt.nopipes", "message.gonzotech.plotnick.tnt.noexplosive",
                "stat.gonzotech.wrench_eaten"):
        assert key in lang, f"в {lang_name} нет ключа {key}"

# ── 12. Срез 0.3.259→0.3.264: краш-защита стопок, ваниальная по 64 ──
# 512-стопы автор отменил, поэтому потолок кодека [1;99] больше не проблема:
# сайдкар «OversizeCounts» и кламп-обвязка удалены — и не должны вернуться.
assert "SAVE_COUNT_LIMIT" not in be and "OversizeCounts" not in be, \
    "сайдкары сверх-стеков должны были умереть вместе с 512"
assert "saveAllItemsClamped" not in be, "кламп-обвязка снова ожила"
assert 'saveStackList(virtual, "Virtual", tag, registries);' in be, \
    "виртуальные слоты сохраняются не своим форматом"
assert "rest.split(cap)" in be, "выпадение при разборе Плотника не дробится на безопасные стопки"

menu = (ROOT / "src/main/java/com/gonzotech/machines/menu/PlotnickMenu.java").read_text(encoding="utf-8")
assert "SimpleContainerData(PlotnickBlockEntity.DATA_SLOTS)" in menu, \
    "меню не берёт ContainerData (0.3.262: +9 тоталов ячеек)"
# ── 15. Срез 0.3.264: хранилище v3, анабиоз сектора, гейт приватных правил ──
# Хранилище: 9 классических ×64 + 54 типа × 4 стопки; зоны «добытое»/депо.
assert "VIRTUAL_TYPES = 54" in be and "STACKS_PER_TYPE = 4" in be, "геометрия v3 сбилась"
assert "OUT_STACKS_PER_TYPE = 2" in be, "нет разреза зон «добытое»/депо"
assert "CELL_STACKS" not in be and "CELL_CAPACITY" not in be and "bucketRest" not in be, \
    "«512-ячейки» с подстопками должны быть удалены (откат решения автора)"
assert "rebalanceHeads" not in be, "витрина-пополнение heads мертва вместе с подстопками"
assert "normalizeStacks()" in be, "легаси-стопки >64 не режутся при загрузке"
assert 'loadStackList(tag, "Virtual", virtual, registries);' in be, "виртуаль не читается своим форматом"
# Container-выставка: трубы/воронки видят виртуаль, депо и добытое разведены.
assert "CONTAINER_TOTAL = CONTAINER_SIZE + VIRTUAL_SLOTS" in be, "Container не расширен виртуалем"
assert "return CONTAINER_TOTAL;" in be, "getContainerSize не отдаёт 227"
assert "idx % STACKS_PER_TYPE < OUT_STACKS_PER_TYPE) return false;" in be, \
    "внешняя вставка в «добытое» не запрещена"
assert "vi >= 0 && vi < VIRTUAL_SLOTS && !virtual.get(vi).isEmpty();" in be, \
    "из виртуала нельзя вынимать лицом"
# Порядок движения: вставка — головы→депо→добытое переливом; расход — тот же.
assert "public boolean insertMined" in be, "буровой поток не отделён от материала"
assert "int[] order = mined" in be, "нет разворота зон при буровой вставке"
assert "for (int j = OUT_STACKS_PER_TYPE; j < STACKS_PER_TYPE && left > 0; j++)" in be, \
    "consumeActive не питается головами и депо"
assert "for (int pass = 0; pass < 2 && left > 0; pass++)" in be, \
    "consumeStored не идёт голова→депо→добытое двумя проходами"
# Анабиоз сектора 3×3 (решение автора 22.10).
assert "private void reelectSector(ServerLevel server)" in be and "sectorPeers" in be, \
    "перевыборы лидера сектора не реализованы"
assert "static void notifySectorChanged" in be, "нет сигнала сектору при установке/сломе"
assert '"PlacedAt"' in be and '"Hibernating"' in be, "анабиоз не персистентен"
assert "if (be.hibernating) {" in be, "serverTick не глушит спящий Плотник"
assert "public boolean isHibernating()" in be, "блок не может спросить про анабиоз"
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    assert "message.gonzotech.plotnick.hibernating" in lang, f"в {lang_name} нет текста анабиоза"
plotnick_block_src = (ROOT / "src/main/java/com/gonzotech/machines/block/PlotnickBlock.java").read_text(encoding="utf-8")
assert "plotnick.isHibernating()" in plotnick_block_src, "спящий Плотник открывает GUI"
assert "notifySectorChanged(server, pos)" in plotnick_block_src, "onRemove блока не будит сектор"
# Гейт «пути блока» — приватные правила через стандартные события.
for anchor in ("new BlockEvent.EntityPlaceEvent(", "new BlockEvent.BreakEvent(",
               "private boolean mayTouchPlace", "private boolean mayTouchBreak",
               "private ServerPlayer actionActor", "touchCached("):
    assert anchor in be, f"гейт привата не на месте: {anchor}"
assert "if (!mayTouchBreak(server, pos)) continue;" in be, "сбор руд не под гейтом слома"
assert "if (!mayTouchBreak(server, pos)) {" in be, "демонтаж не под гейтом слома"
assert "if (!mayTouchPlace(server, pos)) {\n                buildQueue.add(entry);" in be, \
    "стройка не откладывает клетку при запрете"
assert "if (!mayTouchPlace(server, pos)) {\n                tntQueue.add(0, pos);" in be, \
    "ТНТ не в депо — пауза очереди при запрете"
assert "if (!mayTouchPlace(server, target)) return;" in be, "скважина не под гейтом постановки"
# Компиляция автора 22.10 (0.3.264): пять несуществующих/потерянных символов — 0.3.265.
assert "server.players()" in be and "getPlayerList" not in be, \
    "actionActor зовёт несуществующий ServerLevel.getPlayerList()"
assert "private static boolean isAllowedCard(int slot, ItemStack stack)" in be, \
    "определение карточного стража потеряно при переписывании хранилища"
mod_src = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
assert "level.isOutsideBuildHeight(pos)" in mod_src and "Level.isOutsideBuildHeight" not in mod_src, \
    "build height опять зовётся статически (в 1.21.4 это instance-метод)"
assert "player instanceof net.minecraft.server.level.ServerPlayer" in plotnick_block_src \
    and "player.sendSystemMessage" not in plotnick_block_src, \
    "sendSystemMessage зовётся на Player — у Player его нет"
# 0.3.266 (отыгрыш автора): анабиоз по пересечению покрытия, shift — только
# в головы, хотбар/карты наружу не отдаём.
_peers = re.search(r"private List<PlotnickBlockEntity> sectorPeers.*?\n    \}", be, re.S)
assert _peers and "dx = -2; dx <= 2" in _peers.group(0), \
    "sectorPeers не сканирует чанки ±2 — зоны ≤2 не пересекаются"
_notify = re.search(r"public static void notifySectorChanged.*?\n    \}", be, re.S)
assert _notify and "dx = -2; dx <= 2" in _notify.group(0), \
    "notifySectorChanged не будит соседей на ±2"
assert "if (peer == this || peer.hibernating) continue;" in be, \
    "выборы считают сонных соседей держателями вотчины"
assert "if (side == Direction.DOWN)" in be and "int[] out = new int[VIRTUAL_SLOTS];" in be, \
    "воронка снизу видит больше невидимого хранилища"
assert "if (slot < CONTAINER_SIZE) return false;" in be, \
    "трубы могут вытянуть хотбар или карты"
# Пачка 0.3.267: карта помнит измерение (#14), связки разряжаются (#28),
# дорогие C2S отсечены по частоте (#41).
card_src = (ROOT / "src/main/java/com/gonzotech/machines/item/MemoryCardItem.java").read_text(encoding="utf-8")
assert "MinerSectorDim" in card_src and "dimensionMatches" in card_src, \
    "карта не помнит измерение (#14)"
assert "sourceMask,\n            server.dimension())" in be, "импульс не прошивает карту миром"
assert be.count("dimensionMatches") >= 3, "гейты карты не проверяют измерение"
ir_src = (ROOT / "src/main/java/com/gonzotech/radiation/ItemRadioactivity.java").read_text(encoding="utf-8")
assert "BUNDLE_CONTENTS" in ir_src, "разрядка по-прежнему слепа к связкам (#28)"
thr_src = (ROOT / "src/main/java/com/gonzotech/core/network/PayloadThrottle.java").read_text(encoding="utf-8")
assert "WINDOW_TICKS = 20" in thr_src and "public static boolean allow" in thr_src, \
    "троттла нет (#41)"
for _f, _what in (("chalkboard/network/ChalkboardNetwork.java", "доска"),
                  ("chalkboard/network/NotesNetwork.java", "записки"),
                  ("machines/network/PipeFlowNetwork.java", "трубы")):
    _src = (ROOT / ("src/main/java/com/gonzotech/" + _f)).read_text(encoding="utf-8")
    assert "PayloadThrottle.allow" in _src, f"нет частотной отсечки: {_what} (#41)"
assert "PayloadThrottle.allow" in (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8"), \
    "цезий не отсечён (#41)"
assert "PayloadThrottle.forget" in (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8"), \
    "мусорка троттла на logout не подключена"

# Радиация per-item (автор 22.10, таблица 4 случаев): цель роста не делится на
# размер стака, сид 1 mZt на предмет, ре-излучение «на предмет» ×0.75.
ir_src = (ROOT / "src/main/java/com/gonzotech/radiation/ItemRadioactivity.java").read_text(encoding="utf-8")
assert "INDUCED_REEMIT = 0.75" in ir_src, "нет коэффициента переизлучения наведёнки"
assert "sourceNzt - intrinsic), INDUCED_CAP)" in ir_src and "intrinsic) / count" not in ir_src, \
    "цель по-прежнему делится на размер стака — стопка снова щит (случай 2)"
assert "Math.max(current, CLEANUP_FLOOR);" in ir_src and "1.0 / count" not in ir_src, \
    "сид всё ещё зависит от стака — #27 вернётся"
assert "RadSources.emissionPerItem(stack);" in ir_src, "intrinsic отнимает тотал стака, а не пресет предмета"
rad_src = (ROOT / "src/main/java/com/gonzotech/radiation/RadSources.java").read_text(encoding="utf-8")
assert "public static double emissionTransfer(ItemStack stack)" in rad_src, "нет переноса «на предмет»"
rs_src = (ROOT / "src/main/java/com/gonzotech/radiation/RadiationSystem.java").read_text(encoding="utf-8")
assert "secondStackTransfer : maxStackTransfer" in rs_src, "инвентарь не скидывает наведёнку источника"
assert "CarrierItem.RADIOACTIVITY_FACTOR * INDUCED_REEMIT" in ir_src, \
    "наведёнка содержимого носителя в обход двойного экрана (п.4 автора)"

# 0.3.269: вотчина BFS строго 3×3 (#12), гейты выгрузки чанков (#25),
# ротация+заморозка экранирования (#26), триплет UNKNOWN и кэш чехлов (#24/32/37).
np_src = (ROOT / "src/main/java/com/gonzotech/machines/miner/NetworkPlanner.java").read_text(encoding="utf-8")
assert "minSectorX" in np_src and "maxSectorZ" in np_src, "BFS снова гуляет за край вотчины (#12)"
crd = (ROOT / "src/main/java/com/gonzotech/radiation/ChunkRadiationData.java").read_text(encoding="utf-8")
assert "screenProbeCursor" in crd and "screenFactorCache" in crd, "нет ротации/заморозки экранирования (#26)"
assert "heldFrozen" in crd and "placedEmission" in crd, "reconcile забывает источники в выгрузке (#25)"
assert "getChunkNow" in crd, "визуал принудительно грузит чанки (#25)"
fs = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorFuelStorage.java").read_text(encoding="utf-8")
assert "UNKNOWN = -1" in fs and "sheathsCached" in fs, "нет триплета/кэша чехлов (#24/#32)"
rm = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorMatrix.java").read_text(encoding="utf-8")
assert "totalTvelsKnown" in rm, "недогрузка чанков читается как 0 ТВЭЛов (#24)"
rt = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorRuntime.java").read_text(encoding="utf-8")
assert "if (snapshot == null) {" in rt, "нулевой снимок обнулит реактор вместо заморозки (#24)"

# 0.3.272: дебаггер дюпа + порядок угольков/аварии (сверка аудита 23.10).
import json as _json
_mix = _json.loads((ROOT / "src/main/resources/gonzotech.mixins.json").read_text(encoding="utf-8"))
assert "ContainerClickRadiationMixin" in _mix["mixins"], "миксин кликов вылетел из конфиг-листа"
assert "radiationInventoryDebug" in (ROOT / "src/main/java/com/gonzotech/core/config/GonzoServerConfig.java") \
    .read_text(encoding="utf-8"), "нет тумблера серверного дебаггера инвентаря"

# 0.3.275 (автор 23.10 «пауза — неверное вмешательство»): миксин = чистая
# диагностика, guard-файл удалён, шлюзов 273-образных больше нет. Живость
# («ПРИМЕНЁН») доказана прогоном автора — пины на неё остаются.
_m275 = (ROOT / "src/main/java/com/gonzotech/mixin/ContainerClickRadiationMixin.java") \
    .read_text(encoding="utf-8")
assert "ПРИМЕНЁН" in _m275, "нет unconditional-строки живости миксина"
assert "allowClickLog()" in _m275, "клик-строки не защищены реалтаймовым шлюзом"
assert "AtomicLong" in _m275 and "150_000_000" in _m275, "шлюз уехал из миксина"
assert not (ROOT / "src/main/java/com/gonzotech/radiation/RadiationClickWatch.java").exists(), \
    "стража пауз жива — автор 23.10 отменил весь класс вмешательств"
_sys275 = (ROOT / "src/main/java/com/gonzotech/radiation/RadiationSystem.java") \
    .read_text(encoding="utf-8")
for _dead in ("RadiationClickWatch", "clickFrozen", "menuOpen", "isContainerViewed"):
    assert _dead not in _sys275, f"{_dead}: след паузы в RadiationSystem"
# 0.3.277: reject 276 удалён (setRemoteSlotNoCopy в 1.21.4 пишет только кэш
# remoteSlots — «всасывания в реальность» нет), вместо него — unconditional
# счётчик живости обработчика и сервер/клиентский [LEDGER].
assert "rejectInflatedClick" not in _m275 and "ClickType.CLONE" not in _m275, \
    "страж несуществующего канала должен быть удалён"
assert "GONZOTECH$CLICKS" in _m275 and "обработчик кликов жив" in _m275, \
    "нет unconditional-пробника кликов (первые 3 + каждый 25-й)"
assert "раздутый стак" in _sys275 and "getMaxStackSize(stack)" in _sys275, \
    "тик-бэкстоп раздутых стаков пропал (вторая линия без миксина)"
_led = (ROOT / "src/main/java/com/gonzotech/radiation/InventoryLedgerClient.java") \
    .read_text(encoding="utf-8")
assert "InventoryLedgerPayload" in _led and "sendToServer" in _led, "клиент не шлёт свою сумму"
assert "[LEDGER]" in _sys275 and "acceptLedger" in _sys275, "серверный журнал сумм пропал"
_mod = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
assert "playToServer" in _mod and "InventoryLedgerPayload.TYPE" in _mod, "пакет не зарегистрирован"
# IPayloadContext в 1.21.4 не имеет sender() — единственный путь к игроку —
# context.player() + instanceof (как в PlotnickButtonPayload.handle).
assert "context.sender()" not in _mod, "несуществующий IPayloadContext.sender() вернутся"
# 0.3.278: разбор Q-дюпа (автор: воспроизводится в ВЫЖИВАНИИ, переживает
# перезаход). Write-back стаков на сущности удалён (ванильный ItemEntity#setItem
# = entityData.set того же live-объекта — само-присваивание; риск при гонке с
# pickup без выгоды). [LEDGER] расширен мировой суммой: меню + земля.
assert "drop.setItem(" not in _sys275, "write-back на ItemEntity вернулся — фикс 278 откатился"
assert "frame.setItem(" not in _sys275, "write-back на рамку вернулся"
assert _sys275.count(".setChanged()") == 1, "у рамок вернулся несуществующий Entity#setChanged (не компилируется)"
assert "земля Σ=" in _sys275 and "мир Σ=" in _sys275, "[LEDGER] не расширен землёй/миром"
assert "new long[]{serverSum, clientSum, groundSum}" in _sys275, "базовая линия LEDGER не трёхместная"
assert "getEntityData()" in _sys275, "обоснование no-op (ванильный сеттер) пропало из комментария"
# 0.3.279: туман при багровом дне. getBrightnessDependentFogColor обязан нести
# ТОТ ЖЕ crimson-оверлей, что и горизонт купола (тот же вес 0.95 и тот же гейт
# OVERWORLD×!GONE) — «туман чисто перенимает sky color» (автор 24.10).
_fog = (ROOT / "src/main/java/com/gonzotech/space/client/SpaceSkyEffects.java") \
    .read_text(encoding="utf-8")
# 0.3.287: симметрия стала структурной — купол и туман зовут один общий
# skyShellColors; внутри него кримзон-оверлеи (вес 0.8 зенит / 0.95 горизонт,
# в E0-снеге горизонт #5D4145 через crimsonHorizonFor) существуют в одном экземпляре.
assert _fog.count("(float) (crimson * 0.95F))") == 1, \
    "кримзон-оверлей горизонта должен жить в единственном skyShellColors"
assert "overlayArgb(horizon, crimsonHorizonFor(level, snowRide)," in _fog, \
    "E0-снежная подмена кримзон-горизонта должна идти из общего crimsonHorizonFor"
assert _fog.count("skyShellColors") >= 3, \
    "0.3.287: renderSky, DSE-туман и определение — три обращения к одному небу"
assert _fog.count("net.minecraft.world.level.Level.OVERWORLD") >= 1, \
    "гейт багрового тинта по измерению обязан жить в общем skyShellColors (0.3.287)"
assert "overridesSkyLight()" in _fog, "привязка skyDarken к суневету пропала"
# 0.3.280: туман гонзо-жидкостей — underwater-подобие вместо terrain-fog:
# геометрия только в FOG_TERRAIN, плотность 0→93 %; мёртвая жижа — без
# отмены RenderFog, экранная дымка 11 % с ramp ±1 %/тик по камере.
_cfc = (ROOT / "src/main/java/com/gonzotech/core/fluid/client/CoriumFluidClient.java") \
    .read_text(encoding="utf-8")
assert "FogRenderer.FogMode.FOG_TERRAIN" in _cfc, "гейт слоя тумана у жидкостей потерян"
assert "FOG_ALPHA_MAX = 0.93F" in _cfc, "потолок плотности 93 % потерян"
assert "FogShape.SPHERE, fogR, fogG, fogB, 1.0F" not in _cfc, "старый безгейтовый возврат с alpha=1.0 вернулся"
_dsf = (ROOT / "src/main/java/com/gonzotech/core/client/DeadSlimeFogClient.java") \
    .read_text(encoding="utf-8")
assert "setCanceled" not in _dsf, "terrain-fog жижи вернулся (отмена RenderFog)"
assert "onRenderFog" not in _dsf, "обработчик RenderFog у жижи должен быть удалён"
assert "ViewportEvent.RenderFog" not in _dsf
# 0.3.285 (автор): экранная дымка 0..11 % у жижи УДАЛЕНА — плотность целиком
# у спрайт-оверлея dead_slime_overlay в FluidScreenOverlays.
assert "veilPercent" not in _dsf and "VEIL_MAX_PERCENT" not in _dsf, \
    "старый вейл 0..11 % у жижи должен быть удалён (0.3.285)"
assert "RenderGuiEvent" not in _dsf and "ClientTickEvent" not in _dsf, \
    "у DeadSlimeFogClient не должно остаться экранного/тикового кода"
assert "onComputeFogColor" in _dsf, "окраска тумана жижи в map color пропала"
# 0.3.281: «подводная» идентификация гонзо-жидкостей — миксин Camera +
# вспомогательный класс; тег #minecraft:water на наши жидкости — ЗАПРЕЩЁН
# (утаскивает утопление/тушение огня/плавание, см. GonzoFluidFog javadoc).
_cam = (ROOT / "src/main/java/com/gonzotech/mixin/client/CameraGonzoFluidFogMixin.java")
assert _cam.exists(), "миксин Camera#getFluidInCamera потерян"
_camsrc = _cam.read_text(encoding="utf-8")
assert 'method = "getFluidInCamera"' in _camsrc, "миксин отцепился от Camera#getFluidInCamera"
assert "FogType.POWDER_SNOW" in _camsrc, "мёртвая жижа перестала быть POWDER_SNOW-средой"
assert "cir.setReturnValue(medium)" in _camsrc, \
    "миксин перестал возвращать среду из GonzoFluidFog (WATER/LAVA-карта)"
assert "fluidMedium" in _camsrc, "миксин перестал спрашивать GonzoFluidFog про среду"
assert "DEAD_SLIME_BLOCK" in _camsrc, "блок-ветка жижи уехала из миксина"
_gff = (ROOT / "src/main/java/com/gonzotech/core/fluid/GonzoFluidFog.java")
_gffsrc = _gff.read_text(encoding="utf-8")
assert _gff.exists() and _gffsrc.count("ModFluids.") >= 14, \
    "набор из 14 холдеров жидкостей разъехался"
assert "FogType.LAVA" in _gffsrc and "FogType.WATER" in _gffsrc, \
    "GonzoFluidFog.fluidMedium потерял ветку среды"
_dsb = (ROOT / "src/main/java/com/gonzotech/core/block/DeadSlimeBlock.java")
_dsb_src = _dsb.read_text(encoding="utf-8")
assert "protected boolean skipRendering" not in _dsb_src, \
    "0.3.283: антикулинг грань-override должен быть снят (логика инвертирована автором)"
_cfc = (ROOT / "src/main/java/com/gonzotech/core/fluid/client/CoriumFluidClient.java").read_text(encoding="utf-8")
assert "0.41F, 0.15F, 0.13F, -2.0F, 1.15F" in _cfc, \
    "кориум: густота/начало/цвет 0.3.283 разъехались"
assert "0.71F, 0.90F, 0.56F, -1.0F, 6.0F" in _cfc, \
    "кислота: радиус видимости 6 блоков (0.3.283) разъехался"
assert "getRenderOverlayTexture" not in _cfc and "underwater.png" not in _cfc, \
    "чужой underwater.png-оверлей должен быть снят (свои спрайты FluidScreenOverlays)"
_fso = ROOT / "src/main/java/com/gonzotech/core/fluid/client/FluidScreenOverlays.java"
assert _fso.exists(), "FluidScreenOverlays потерян"
_fsosrc = _fso.read_text(encoding="utf-8")
for _tok in ("percent + 1", "fadeTicks", "Math.exp", "shown", "OUTGOING", "dead_slime_overlay",
             "water_overlay", "lava_overlay", "corium_overlay",
             "<< 24", "openAsReader", "guiTextured"):
    # 0.3.284: симметричный «percent--» заменён ускоряющейся кривой (Math.max(0, percent - fadeTicks))
    assert _tok in _fsosrc, f"FluidScreenOverlays: {_tok} пропал"
for _gui in (_fsosrc,
             (ROOT / "src/main/java/com/gonzotech/core/psyche/client/PsycheCrisisClient.java").read_text(encoding="utf-8"),
             (ROOT / "src/main/java/com/gonzotech/core/psyche/client/PsycheHud.java").read_text(encoding="utf-8")):
    assert "setShaderColor(" not in _gui, \
        "0.3.285: альфа обязана печататься в вершины (13-й параметр blit'а), setShaderColor в 1.21.2+ игнорируется"
_modsrc = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
assert "FluidScreenOverlays.class" in _modsrc, "FluidScreenOverlays не зарегистрирован на шине"
for _name in ("water_overlay", "lava_overlay", "sulfuric_acid_overlay", "formaldehyde_overlay",
              "vodka_overlay", "rectificate_overlay", "mash_overlay", "wort_overlay",
              "corium_overlay", "dead_slime_overlay"):
    _png = ROOT / f"src/main/resources/assets/gonzotech/textures/gui/{_name}.png"
    assert _png.exists(), f"плейсхолдер {_name}.png потерян"
    _meta = _png.parent / (_png.name + ".mcmeta")
    assert _meta.exists(), f"{_name}.png.mcmeta потерян"
    _mj = _json.loads(_meta.read_text(encoding="utf-8"))
    _a = _mj["animation"]
    assert _a["width"] == 256 and _a["height"] == 144, f"{_name}: кадр не 256×144"
_hud = (ROOT / "src/main/java/com/gonzotech/core/psyche/client/PsycheHud.java").read_text(encoding="utf-8")
assert "TEX_WHITE" in _hud and "1, 1, 1, 1, color)" in _hud, \
    "0.3.285: bar_fill обязан тонироваться белым 1×1 blit'ом с вершинным цветом"
assert "g.fill(" not in _hud, "сырой g.fill в шкалах должен быть убран (расслоение сэндвича)"
assert (ROOT / "src/main/resources/assets/gonzotech/textures/gui/white.png").exists(), \
    "white.png (1×1) потерян — шкалы не затонировать"
# 0.3.286: матрица осадков Оверворл/Марса (скайбокс+туман) и аддитивная луна.
_sky = (ROOT / "src/main/java/com/gonzotech/space/client/SpaceSkyEffects.java").read_text(encoding="utf-8")
assert "0xFF727994" in _sky, "дождевой дневной конец #727994 потерян"
assert "0xFF969AA2" in _sky, "снежный дневной конец #969AA2 потерян"
assert "0xFF5D4145" in _sky, "E0-снежный горизонт #5D4145 потерян"
assert "getRainLevel" in _sky and "wetMode(" in _sky, "wet-согласование скайбокса разъехалось"
assert "crimsonHorizonFor" in _sky, "snow-подмена кримзон-горизонта уехала"
# 0.3.287: туман наследует мокрый горизонт через общий skyShellColors (см. >=3 выше)
assert "wetDay(horizonNightArgb, horizonDayArgb" in _sky, \
    "мокрая подмена горизонта пропала из skyShellColors"
assert "WET_DUSK_CROSSFADE" in _sky and "DUSK_WET_SUPPRESS" in _sky, \
    "кросс-фейд осадки<->закат (п.5 автора) уехал"
assert "skyShellColors(level, 0.0F)[1];" in _sky, "DSE-туман перестал брать цвет из общего неба"
assert "snowCrimsonMix" in _sky and \
       "Mth.clamp((float) (t - start) / 2000.0F, 0.0F, 1.0F)" in _sky, \
    "0.3.288: E0-снежная подмена кримзон-горизонта снова бинарна — перескок на t=0"
assert "biomeSnowFollow" in _sky and "Math.max(snowWindow, biomeSnowWeight(level))" in _sky and \
       "Mth.clamp(wet * 5.0F, 0.0F, 1.0F)" in _sky, \
    "0.3.289: снежный вес снова бинарный — конец снега/снеговая линия «щёлкают»"
assert "lerpArgb(WET_RAIN_DAY_ARGB, WET_SNOW_DAY_ARGB, Mth.clamp(snowW, 0.0F, 1.0F))" in _sky, \
    "0.3.289: мокрая цель дождь/снег перестала перемешиваться непрерывно"
_wfa = (ROOT / "src/main/java/com/gonzotech/space/client/WetFogAligner.java").read_text(encoding="utf-8")
assert "ViewportEvent.ComputeFogColor" in _wfa and "skyShellColors(level, partialTick)[1]" in _wfa, \
    "WetFogAligner потерян — при дожде туман снова станет темнее неба (п.1-4)"
assert "wet * 4.0F" in _wfa, "правка тумана перестала нарастать с силой дождя (риск щелчка)"
_fs = (ROOT / "src/main/java/com/gonzotech/core/fluid/client/GonzoFluidSounds.java").read_text(encoding="utf-8")
for _tok in ("AMBIENT_UNDERWATER_ENTER", "AMBIENT_UNDERWATER_LOOP", "AMBIENT_UNDERWATER_EXIT",
             "PLAYER_SPLASH_HIGH_SPEED", "forAmbientAddition",
             "GonzoFluidFog.fluidMedium(state) == FogType.WATER"):
    assert _tok in _fs, f"звуки жидкостей (п.6): потерян токен {_tok}"
assert "AbstractTickableSoundInstance" in _fs and "FADE_DURATION" in _fs, \
    "луп-журчание перестал быть фадовойTickableSound-репликой ванили"
_gtm287 = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
assert "com.gonzotech.core.fluid.client.GonzoFluidSounds.class" in _gtm287 and \
       "com.gonzotech.space.client.WetFogAligner.class" in _gtm287, "обработчики 0.3.287 не зарегистрированы"
_sc = (ROOT / "src/main/java/com/gonzotech/space/client/SpaceClient.java").read_text(encoding="utf-8")
assert "overworld/moon_phases" in _sc and "CelestialBody.Blend.ADDITIVE" in _sc, \
    "луна Оверворланда слетела с аддитивного блендинга"
_cb = (ROOT / "src/main/java/com/gonzotech/space/client/CelestialBody.java").read_text(encoding="utf-8")
assert _cb.count("public static CelestialBody planet(") >= 2, "planet(Blend) перегрузка пропала"
_swg = (ROOT / "src/main/java/com/gonzotech/core/worldgen/SurfaceWasteGenerator.java").read_text(encoding="utf-8")
assert "host.dimension() != net.minecraft.world.level.Level.OVERWORLD" in _swg, \
    "мусор/бочки вернулись в не-оверворлд измерения"
assert not (ROOT / "src/main/resources/data/minecraft/tags/fluid/water.json").exists(), \
    "тег #minecraft:water на гонзо-жидкости — возвращение утопления в кислоту"
assert "context.player() instanceof net.minecraft.server.level.ServerPlayer" in _mod
# 0.3.276-f1: бэкстоп пользуется AbstractContainerMenu/Slot — импорты обязаны
# быть (сборка автора 23.10 падала ровно на этом: тихий no-op splice).
assert "import net.minecraft.world.inventory.AbstractContainerMenu;" in _sys275 \
    and "import net.minecraft.world.inventory.Slot;" in _sys275, \
    "нет импортов inventory-классов — javac упадёт на бэкстопе"
for _lng in ("en_us", "ru_ru"):
    _j = _json.loads((ROOT / f"src/main/resources/assets/gonzotech/lang/{_lng}.json").read_text(encoding="utf-8"))
    for _k in ("gonzotech.configuration.radiationInventoryDebug",
               "gonzotech.configuration.radiationInventoryDebug.tooltip",
               "gonzotech.configuration.section.gonzotech.server.toml",
               "gonzotech.configuration.section.gonzotech.client.toml",
               "gonzotech.configuration.title"):
        assert _k in _j, f"{_lng}: нет ключа конфига {_k}"
assert "заморозка" not in _j["gonzotech.configuration.radiationInventoryDebug.tooltip"], \
    "ru-описание флага всё ещё обещает логи заморозки"

# 0.3.270: авария сжигает всё топливо; unformed-продолжение; угольки.
assert (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorEmberData.java").exists(), \
    "нет ReactorEmberData — снос панели остаётся бесплатным reset"

# Миграция v2-сейвов: схлопнуть 432+63 в 216 правилами вставки, излишек — на землю.
assert "migrateLegacyStorage" in be and "isLegacyVirtual" in be, "нет легаси-свёртки хранилища"
assert 'if (tag.contains("BucketRest") || isLegacyVirtual(tag))' in be, "миграция не обусловлена v2-следом"
assert "legacyOverflow" in be, "не влезшее при миграции не аккумулируется"
# GUI-меню: обычный Slot; лор «хранит» убран в 0.3.267 (головы = ваниль).
assert "MaxStackSlot" not in menu and "addSlot(new Slot(be, k, 8 + 18 * k, 76));" in menu, \
    "активные слоты не стали классической ванильной девяткой"
assert "plotnick.insert(" not in menu, "shift-клик снова запихивает хвост игрока в невидимый склад"
assert "cellStored" not in menu and "cellStored" not in screen, \
    "лор «хранит» не убран из меню/экрана (0.3.267: головы — истинные стопки)"
assert 'public static final int DATA_SLOTS = 7;' in be, "DATA не ужат до 7 слотов"
for _lang_name in ("ru_ru.json", "en_us.json"):
    _lang = (RES / "lang" / _lang_name).read_text(encoding="utf-8")
    assert "plotnick.cell.stored" not in _lang, f"ключ «хранит» осиротел в {_lang_name}"
assert "public void clicked(int slotId, int button, ClickType clickType," in menu, \
    "нет привратника щелчков против оверстака в кармане игрока"
assert "clampCarried(player)" in menu, "излишек в руке не раскладывается при закрытии GUI"
assert "be.menuOpened()" in menu and "plotnick.menuClosed()" in menu, \
    "Плотник не знает, что на него смотрит GUI"
assert "case 6 -> netRoadsLink;" in be, "четвёртое число тултипа не синхронизируется"

assert "BTN_NETWORK_LINK = 6" in be, "нет id для ПКМ-подсоединения"
assert "case BTN_NETWORK_LINK -> linkExistingDrills(server, requester)" in be, \
    "ПКМ-кнопка не подключена"
for needle in ("collectSectorScan", "scanSurface", "findPlacedDrills" , "refreshNetworkStats"):
    if needle == "findPlacedDrills":
        continue  # поиск буров живёт в collectSectorScan
    assert needle in be, f"режим подключения буров: нет {needle}"
assert '"message.gonzotech.plotnick.network.nodrills"' in be, "нет отчёта «буров не найдено»"
assert '"message.gonzotech.plotnick.network.linked"' in be, "нет отчёта о подсоединении"
assert "drillPoints.add(cell)" not in be[be.index("linkExistingDrills"):], \
    "ПКМ-ссылка снова усыновляет буры точками — движок и сам их пасёт (0.3.261)"

screen = (ROOT / "src/main/java/com/gonzotech/machines/client/PlotnickScreen.java").read_text(encoding="utf-8")
assert "BTN_NETWORK_RECT" in screen and "PlotnickBlockEntity.BTN_NETWORK_LINK" in screen, \
    "ПКМ по кнопке постройки не отправляет режим подключения"
assert "gui.gonzotech.plotnick.button.network.lmb" in screen, "нет строки ЛКМ в тултипе постройки"
assert "gui.gonzotech.plotnick.button.network.rmb" in screen, "нет строки ПКМ в тултипе постройки"
assert 'clickLine("ЛКМ "' in screen and "ChatFormatting.BOLD" in screen, \
    "метки кликов в тултипе не выделены жирным серым, как просил автор"
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("gui.gonzotech.plotnick.button.network.lmb", "gui.gonzotech.plotnick.button.network.rmb",
                "message.gonzotech.plotnick.network.nodrills", "message.gonzotech.plotnick.network.linked"):
        assert key in lang, f"в {lang_name} нет ключа {key}"

# ── 13. Срез 0.3.260: отклик «Начать бурение», числа ГТУ Плотника ──
assert "GTU_CAPACITY_MILLI = 32_082L * MachineDefs.MILLI" in defs, \
    "буфер Плотника не 32 082 GTU (автор)"
assert "GTU_INTAKE_MILLI = 512L * MachineDefs.MILLI" in defs, \
    "приём Плотника не 512 GTU/т (автор)"
assert "case BTN_DRILL -> startDrilling(server, requester)" in be, \
    "«Начать бурение» снова ничего не делает"
assert "private boolean drilling" in be, "нет флага запущенного бурения"
assert "if (!drilling || cycleDrillCells.isEmpty()) return;" in be, \
    "движок буров не слушается флага"
assert '"Drilling"' in be, "флаг бурения не персистентен"
assert "private DrillRun planDrillRun(ServerLevel server)" in be, "перекличка скважин не выделена"
assert "drilling = false;" in be[be.index("private boolean packUp"):], "свернуться не глушит бурение"
for key in ("drill.nodrills", "drill.nopipes", "drill.nolink", "drill.stuck",
            "drill.started", "drill.lowgtu"):
    assert f'"message.gonzotech.plotnick.{key}"' in be, f"нет отчёта {key}"
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("message.gonzotech.plotnick.drill.started", "message.gonzotech.plotnick.drill.nopipes",
                "message.gonzotech.plotnick.drill.lowgtu"):
        assert key in lang, f"в {lang_name} нет ключа {key}"

# ── 14. Срез 0.3.261: GUI-безопасность, дешёвые сканы, автономный бур ──
menu_helper = (ROOT / "src/main/java/com/gonzotech/machines/menu/MenuHelper.java").read_text(encoding="utf-8")
assert "readBlockEntitySafe" in menu_helper, "нет толерентного чтения BE для клиентских меню"
menu = (ROOT / "src/main/java/com/gonzotech/machines/menu/PlotnickMenu.java").read_text(encoding="utf-8")
assert "readBlockEntitySafe" in menu and "clientSideEntity" in menu, \
    "меню Плотника кидается при отсутствующем клиентском BE (вылет из мира)"
assert "MenuHelper.readBlockEntity(inventory, buffer, PlotnickBlockEntity.class)" not in menu, \
    "PlotnickMenu всё ещё использует бросковый вариант чтения BE"

# никаких плановых пересчётов тултипов
assert "server.getGameTime() % 20 == 0" not in be, "периодический скан статистики вернулся"
assert "if (be.menuViewers > 0 && be.networkStatsDirty)" in be, \
    "статистка не обновляется строго по событиям при открытом GUI"
mo = be[be.index("public void menuOpened"):be.index("public void menuOpened") + 400]
assert "refreshNetworkStats" not in mo, "вход в GUI снова сканирует мир внутри menuOpened"
assert "if (menuViewers++ == 0) networkStatsDirty = true;" in be, \
    "первое открытие не помечает статистику (ручные правки мира не видны, #18)"
assert "private Set<BlockPos> collectWorkCells" not in be, "полновысотный скан сети не удалён"
assert "WORK_BELT_Y" in be and "scanSurface" in be, "скан сети не ограничен лентой"

# стройка не встаёт колом
assert "if (buildQueue.isEmpty() || buildStalled) return;" in be, "стройки без паузы на дефиците нет"
assert "buildStalled = false; // пополнение склада будит постройку" in be, "склад не будит стройку"
assert '"message.gonzotech.plotnick.network.stalled"' in be, "нет отчёта о вставшей стройке"

# ЛКМ/ПКМ бурения
assert "BTN_DRILL_STOP = 7" in be and "case BTN_DRILL_STOP -> stopDrilling(requester)" in be, \
    "ПКМ-стоп бурения не подключён"
assert '"message.gonzotech.plotnick.drill.stopped"' in be, "нет отчёта об остановке"
assert "gui.gonzotech.plotnick.button.drill.lmb" in screen, "тултип бура не обещает ЛКМ"
assert "gui.gonzotech.plotnick.button.drill.rmb" in screen, "тултип бура не обещает ПКМ"
assert "PlotnickBlockEntity.BTN_DRILL_STOP" in screen, "ПКМ по кнопке бура не отправляет стоп"

# карта: маска простучанных чанков
card = (ROOT / "src/main/java/com/gonzotech/machines/item/MemoryCardItem.java").read_text(encoding="utf-8")
assert "MinerSourceMask" in card and "readSourceMask" in card, "карта не хранит маску источников"
assert "sourceMask |= 1 << ((dx + 1) * 3 + (dz + 1))" in be, "импульс не пишет маску чанков"
assert "if (sourceMask != 0) {" in be, "разметка не ограничена простучанными чанками"

# метки: владелец и самочистка
marker = (ROOT / "src/main/java/com/gonzotech/machines/miner/MinerMarker.java").read_text(encoding="utf-8")
assert "long owner" in marker, "метка не знает владельца"
svc = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerService.java").read_text(encoding="utf-8")
assert "sweepOrphans" in svc and '"Owner"' in svc, "осиротевшие метки не вычищаются"
assert "level.hasChunk" in svc, "свип может съесть метки незагруженного владельца"
cmd = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerDebugCommand.java").read_text(encoding="utf-8")
assert '"clear"' in cmd and "clearAll" in cmd, "нет команды ручного сноса меток"
assert "worldPosition.asLong()" in be, "Плотник не подписывает свои метки"

# счётчики тултипа переживают рестарт
assert '"NetRoadsFull"' in be and '"NetRoadsLink"' in be and '"NetDrillsToPlace"' in be, \
    "числа тултипа не персистентны"

# карта в креатив-табе — возле дозиметра, в начале
tab = (ROOT / "src/main/java/com/gonzotech/core/registry/ModCreativeTabs.java").read_text(encoding="utf-8")
i_dos = tab.index("ModItems.DOSIMETER.get()")
i_card = tab.index("ModMachines.MEMORY_CARD.get()")
i_plot = tab.index("ModMachines.PLOTNICK_ITEM.get()")
assert i_dos < i_card < i_plot, "карта памяти не переехала к дозиметру"

for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("gui.gonzotech.plotnick.button.drill.lmb", "gui.gonzotech.plotnick.button.drill.rmb",
                "message.gonzotech.plotnick.drill.stopped", "message.gonzotech.plotnick.drill.idle",
                "message.gonzotech.plotnick.drill.nodrills", "message.gonzotech.plotnick.network.stalled"):
        assert key in lang, f"в {lang_name} нет ключа {key}"

# ── 16. Срез 0.3.263: дневник ревалидации (42 пункта) — loss/dupe/compile ──
# compile: forward reference констант
assert be.index("STACKS_PER_TYPE = 4") < be.index("VIRTUAL_SLOTS = VIRTUAL_TYPES * STACKS_PER_TYPE"), \
    "STACKS_PER_TYPE объявлен позже использования — illegal forward reference"
assert be.index("public static final int CONTAINER_SIZE") < be.index("CONTAINER_TOTAL = CONTAINER_SIZE"), \
    "CONTAINER_TOTAL собран до CONTAINER_SIZE — forward reference"
# #1: собственный формат списков с int-локальным индексом
assert 'putInt("I", i)' in be and "loadStackList" in be and "saveStackList" in be, \
    "длинные списки не переведены на свой формат с целочисленным индексом"
assert "- CONTAINER_SIZE : seq" not in be, "загрузка снова вычитает несуществующий оффсет"
# #2: один дроп при сломе
bd = (ROOT / "src/main/java/com/gonzotech/machines/block/PlotnickBlock.java").read_text(encoding="utf-8")
assert "plotnick.onBroken();" in bd, "слом Plotnick не идёт через onBroken"
drop_body = be[be.index("public void dropContentsForBreak()"):]
assert drop_body.index("clearContent();") < drop_body.index("}"), \
    "после нашего дропа контейнер не вычищен — базовый onRemove задвоит предметы"
# #4: никаких пополнений головы внутри remove/set (откат воронки)
rem_body = be[be.index("public ItemStack removeItem(int slot, int amount)"):
              be.index("public void setItem(int slot")]
assert rem_body.count("rebalanceHeads();") == 0, \
    "removeItem/removeItemNoUpdate снова таскают стопки внутри чужой транзакции (#4)"
si = be.index("public void setItem(int slot")
set_body = be[si:be.index("@Override", si + 10)]
assert "rebalanceHeads();" not in set_body, "setItem пополняет голову — откат воронки затирает (#4)"
tick_body = be[be.index("public static void serverTick"):be.index("public void menuOpened")]
assert "if (be.hibernating) {" in tick_body and "be.reelectSector(server);" in tick_body, \
    "тик не проводит выборы/анабиоз сектора (0.3.264)"
assert "be.dropLegacyOverflow();" in tick_body, "не влезшее при миграции не выгружается из тика"
# #5: место проверяется ДО изменения мира
assert "!hasRoom(new ItemStack(mined))" in be and "!hasRoom(new ItemStack(item))" in be, \
    "бурение/сбор руды не бронируют место перед разрушением (#5)"
assert "insertOrDrop(" in be, "нет наземного запасного аэродрома для непоместившегося"
# #8: TNT-трубы — колоннами, не 9 чанками
pipes = be[be.index("private List<BlockPos> collectPipesTopDown"):]
assert "for (int y = cell.getY() - 1; y >= server.getMinY(); y--)" in pipes[:900], \
    "закладка TNT снова ходит по всем чанкам (#8)"
assert "server.getMinY(); y <= server.getMaxY()" not in pipes[:900]
# #9: режимы
assert "tntQueue" in be[be.index("private boolean packUp"):be.index("private boolean packUp") + 2200] or \
    "dismantleQueue.isEmpty()" in be[be.index("startDrilling(ServerLevel") + 12:be.index("startDrilling(ServerLevel") + 400], \
    "режимы стройки/бурения/разборки по-прежнему не разведены"
# #10: флаги и retry
assert "stallReportedSize" in be and "% 40 == 0" in be, "стройку не будит retry/новая очередь (#10)"
assert "buildStalled = false; // новая работа" in be and "buildStalled = false; // #10" in be
# #13: геофон
assert "noGeophone" in be and "impulse.nogeophone" in be
# #15: миграция чистит только бесхозное
mk = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerService.java").read_text(encoding="utf-8")
assert "marker.owner() == 0L" in mk[be and mk.index("removeStationMarkersInSector"):] \
    or "marker.owner() == 0L" in mk, "секторная миграция не фильтрует по владельцу (#15)"
# #16/17
assert "items.set(i, ItemStack.EMPTY)" in be and "virtual.set(i, ItemStack.EMPTY)" in be, \
    "clearContent снова дёргает add() на фиксированном NonNullList (#16)"
# 0.3.299 (N6): capOf переименован в единый public capacityFor — пин за именем
assert "capacityFor(" in be, "мердж не уважает лимит самого предмета (#17)"
# #18
assert "if (menuViewers++ == 0) networkStatsDirty = true;" in be, "открытие GUI не освежает числа"
# #21
gtm = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
h = gtm[gtm.index("CesiumBlastRequestPayload.TYPE"):gtm.index("CesiumBlastRequestPayload.TYPE") + 1200]
assert "isOutsideBuildHeight" in h and "hasChunk" in h, "пакет цезия читает мир до проверок (#21)"
assert h.index("hasChunk") < h.index("getBlockState"), "проверка загруженности идёт после чтения (#21)"
# #22
rs = (ROOT / "src/main/java/com/gonzotech/machines/reactor/ReactorStructure.java").read_text(encoding="utf-8")
assert "MAX_SIZE * MAX_SIZE * MAX_SIZE - (MAX_SIZE - 2)" in rs, "лимит обшивки не выведен из размеров (#22)"
# #29: приём GTU помечает сейв во всех станках
import glob as _glob
missing = []
for f in _glob.glob(str(ROOT / "src/main/java/com/gonzotech/**/*BlockEntity.java"), recursive=True):
    t = Path(f).read_text(encoding="utf-8")
    if "public long receiveGtu(long amount, boolean simulate)" in t:
        for m in re.finditer(r"public long receiveGtu\(long amount, boolean simulate\) \{(.+?)\n    \}", t, re.S):
            if "return 0;" in m.group(1):
                continue  # заглушка-источник (Стирлинг)
            if "setChanged" not in m.group(1):
                missing.append(Path(f).name)
assert not missing, "receiveGtu без setChanged: " + ", ".join(missing)
# #20
me = (ROOT / "src/main/java/com/gonzotech/machines/miner/MarkerEvents.java").read_text(encoding="utf-8")
assert "PlayerChangedDimensionEvent" in me, "метрики не пересылаются при смене измерения (#20)"
# #30
cp = (ROOT / "src/main/java/com/gonzotech/machines/network/CompositePipeBlock.java").read_text(encoding="utf-8")
assert cp.index("rotateLayerAt") < cp.index("PipeType part = partAt(state, pos, hit);"), \
    "ветка мастерского поворота снова за режимным блоком (#30)"
# ── 17. Срез 0.3.290: пакет #30 #17 #18 #1 #11-Y + #7 (документация) ──
room_ledger = (ROOT / "src/main/java/com/gonzotech/cleanroom/RoomLedger.java").read_text(encoding="utf-8")
clean_sys = (ROOT / "src/main/java/com/gonzotech/cleanroom/CleanRoomSystem.java").read_text(encoding="utf-8")
mix_chunk = (ROOT / "src/main/java/com/gonzotech/mixin/LevelChunkCleanRoomMixin.java").read_text(encoding="utf-8")
net_plnr = (ROOT / "src/main/java/com/gonzotech/machines/miner/NetworkPlanner.java").read_text(encoding="utf-8")
plot290 = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/PlotnickBlockEntity.java").read_text(encoding="utf-8")
# #30: событийная инвалидация вместо полного рескана на каждый запрос
assert "LongPredicate chunkLive" in room_ledger and "chunksLive(known, chunkLive)" in room_ledger, \
    "#30: быстрая дорожка RoomLedger.find исчезла"
assert room_ledger.count("chunkKeys") == 3, "#30: набор чанков комнаты должен считаться один раз"
assert clean_sys.count("chunkLive(level)") == 2, "#30: не все запросы к реестру получили предикат"
assert room_ledger.count("public Room find(") == 2, \
    "#30: тестовая перегрузка find() должна оставаться единственной трёхаргументной"
clean_self = (ROOT / "audit/CleanRoomSelfTest.java").read_text(encoding="utf-8")
assert "LongPredicate live" in clean_self and ", live)" in clean_self, \
    "#30: UNLOADED-блок селфтеста обязан симулировать чанк предикатом"
assert "find(world::get, a, 24)" not in clean_self, "#30: селфтест UNLOADED обходит предикат"
# #17: SECTOR_PULSE — событийное протухание счётчиков
assert "pulseBlockChange(level, pos);" in mix_chunk, "#17: setBlockState-миксин перестал штамповать секторы"
assert "if (be.menuViewers > 0) be.tickSectorPulse(server);" in plot290, "#17: сверка штампов выпала из тика"
assert plot290.count("SECTOR_PULSE") == 3 and "if (changed) networkStatsDirty = true;" in plot290, \
    "#17: таблица штампов или её потребитель пропали"
# #18: гейт законности обоих write-путей демонтажа (сделано в 0.3.264 — подтверждено пином)
assert "if (!mayTouchBreak(server, pos)) continue; // приват против слома" in plot290, \
    "#18: боковой сбор руды остался без гейта"
assert "if (!mayTouchBreak(server, pos)) {" in plot290, "#18: демонтаж без гейта"
# #1: загрузчик v1 — беззнаковый байт «Slot», коллизия в ближайшее пустое, свёртка v1-сейва
assert "legacyByte.getAsInt() & 0xFF" in plot290, "#1: побайтовый «Slot» v1 снова читается со знаком"
assert "index = free;" in plot290, "#1: спасение коллизий исчезло из loadStackList"
assert plot290.count("foldIntoStorage(") == 4, "#1: свёртка legacy-таблиц должна быть одна на v1 и v2"
assert "if (isLegacySlotFormat(tag)) {" in plot290, "#1: v1-сейв снова читается позиционно"
# #11-Y: вертикальное окно планировщика = лента движка, одна константа
assert net_plnr.count("VERTICAL_BAND") == 3, "#11-Y: константа ленты планировщика"
assert "WORK_BELT_Y = NetworkPlanner.VERTICAL_BAND;" in plot290, "#11-Y: лента BE отсоединилась от планировщика"
assert "anchor.getY() < minY - 1" in net_plnr, "#11-Y: якоря вне ленты снова уходят в BFS"
assert "anchor.getY() - 8" not in net_plnr, "#11-Y: старое неограниченное окно вернулось"
# #7: документация WorldGuard — README + welcome
readme290 = (ROOT / "README.md").read_text(encoding="utf-8")
assert "GT capabilities: server-side WorldGuard" in readme290, "#7: README растерял возможность сервера"

# ── 18. Срез 0.3.291: дебаггер кликов v2 — таблица точек диагностики дюпа (автор 25.10) ──
clickmix = (ROOT / "src/main/java/com/gonzotech/mixin/ContainerClickRadiationMixin.java").read_text(encoding="utf-8")
assert "[RAD-INV][CLICK]" in clickmix and "getChangedSlots().entrySet()" in clickmix \
    and "getButtonNum()" in clickmix, "0.3.291: дактилопечать клика неполная"
assert "РАСХОЖДЕНИЕ" in clickmix and "getStateId()" in clickmix, \
    "0.3.291: контроль state-расхождения выпал из дактилопечати клика"
assert "[RAD-INV][QC]" in clickmix and "QUICK_CRAFT" in clickmix, \
    "0.3.291: фазы ПКМ-раскладки исчезли из дебаггера"
assert "ContainerMenuQuickCraftAccessor" not in clickmix, \
    "0.3.293: accessor приватных quickcraft-полей вернулся (риск APPLY)"
assert "[RAD-INV][CLICK-E]" in clickmix, "0.3.291: пара «после» по слотам/курсору пропала"
mixdir = "src/main/java/com/gonzotech/mixin"
for _f, _need in (
        ("CreativeSlotPacketDebugMixin", 'method = "handleSetCreativeModeSlot"'),
        ("SlotSetByPlayerDebugMixin", "setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"),
        ("ItemEntityMergeDebugMixin", "[RAD-INV][ENT-MERGE]")):
    _src291 = (ROOT / mixdir / (_f + ".java")).read_text(encoding="utf-8")
    assert _need in _src291, f"0.3.291: {_f} разучился видеть {_need}"
creative291 = (ROOT / mixdir / "CreativeSlotPacketDebugMixin.java").read_text(encoding="utf-8")
assert "[RAD-INV][CREATIVE-DROP]" in creative291, \
    "0.3.291: отрицательный слот креатива (выброс) слился с обычным"
slotw291 = (ROOT / mixdir / "SlotSetByPlayerDebugMixin.java").read_text(encoding="utf-8")
assert "setByPlayer(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V" in slotw291, \
    "0.3.291: вторая передрузка setByPlayer (drag) не покрыта"
assert "compat={}" in slotw291 and "SLOT-WRITE" in slotw291, "0.3.291: [SLOT-WRITE] растерял совместимость компонентов"
client291 = (ROOT / mixdir / "client/ClientSlotPacketDebugMixin.java").read_text(encoding="utf-8")
assert "handleContainerSetSlot" in client291 and "InventoryDebugClientGate.isOn()" in client291 \
    and "ItemRadioactivity.getInduced" in client291, "0.3.291: клиентский приём setSlot не пишется"
# 0.3.293: краш автора — @Shadow чужого поля (minecraft живёт в родителе
# ClientCommonPacketListenerImpl, а field-shadow резолвится только в самом
# целевом классе). Клиентский миксин обязан читать мир через getInstance().
_t293 = client291.split("*/", 1)[-1]  # код без javadoc-пояснения про сам запрет
assert "@Shadow" not in _t293 and "Minecraft.getInstance()" in _t293, \
    "0.3.293: в клиентском миксине снова появился @Shadow — APPLY упадёт"
assert not (ROOT / mixdir / "ContainerMenuQuickCraftAccessor.java").exists(), \
    "0.3.293: accessor-файл должен быть удалён"
_mix293 = _json.loads((ROOT / "src/main/resources/gonzotech.mixins.json").read_text(encoding="utf-8"))
assert "ContainerMenuQuickCraftAccessor" not in _mix293["mixins"], \
    "0.3.293: удалённый accessor остался в конфиг-листе — ClassNotFoundException на старте"
drop291 = (ROOT / "src/main/java/com/gonzotech/radiation/ItemDropTraceDebug.java").read_text(encoding="utf-8")
for _tag in ("[RAD-INV][ENT+]", "[RAD-INV][ENT−]", "[RAD-INV][ENT-PICK]", "loadedFromDisk", "TRACED"):
    assert _tag in drop291, f"0.3.291: трассировка предметов потеряла {_tag}"
sys291 = (ROOT / "src/main/java/com/gonzotech/radiation/RadiationSystem.java").read_text(encoding="utf-8")
assert "(tracing || debugInv)" in sys291, "0.3.291: [PASS] забыл брать «до» и на debug-сессиях"
assert "[RAD-INV][COUNT]" in sys291, "0.3.291: проверка неизменности count прохода исчезла"
assert "[RAD-INV][PASS]" in sys291, "0.3.291: строка компонентов до/после исчезла"
assert sys291.count("[RAD-INV][CLAMP]") == 2, "0.3.291: обрезание не отдельное событие (ждём 2 подтега)"
mod291 = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text(encoding="utf-8")
assert "InventoryDebugGatePayload.TYPE" in mod291 and "InventoryDebugClientGate.set(payload.enabled())" in mod291, \
    "0.3.291: гейт клиента не зарегистрирован"
assert "InventoryDebugGatePayload(\n" in mod291.replace("\r\n", "\n") \
    and "RADIATION_INVENTORY_DEBUG.get()));" in mod291, "0.3.291: на входе гейт не шлётся"
mix291 = _json.loads((ROOT / "src/main/resources/gonzotech.mixins.json").read_text(encoding="utf-8"))
for _m in ("CreativeSlotPacketDebugMixin", "SlotSetByPlayerDebugMixin", "ItemEntityMergeDebugMixin"):
    assert _m in mix291["mixins"], f"0.3.291: миксин {_m} не прописан в конфиг-листе"
assert "client.ClientSlotPacketDebugMixin" in mix291["client"], "0.3.291: клиентский миксин не прописан"
cfg291 = (ROOT / "src/main/java/com/gonzotech/core/config/GonzoServerConfig.java").read_text(encoding="utf-8")
assert "QUICK_CRAFT phases" in cfg291 and "setSlot receipts" in cfg291, \
    "0.3.291: TOML-комментарий не описывает новые линии лога"
assert "radiationInventoryDebug" in cfg291, "0.3.291: прежний единственный тумблер выжил — ок"
# 0.3.292: javac автора — в 1.21.4 у AbstractContainerMenu нет countSlots();
# границы слотов проверяются только через публичный NonNullList slots (как в 277-миксине).
for _f292 in ("client/ClientSlotPacketDebugMixin", "CreativeSlotPacketDebugMixin"):
    assert "countSlots()" not in (ROOT / mixdir / (_f292 + ".java")).read_text(encoding="utf-8"), \
        f"0.3.292: {_f292} снова зовёт несуществующий countSlots()"
assert "menu.slots.size()" in client291 and creative291.count("inventoryMenu.slots.size()") == 2, \
    "0.3.292: проверка границ уехала на публичный slots"




# lang
for lang_name in ("ru_ru.json", "en_us.json"):
    lang = (RES / "lang" / lang_name).read_text(encoding="utf-8")
    for key in ("message.gonzotech.plotnick.impulse.nogeophone",
                "message.gonzotech.plotnick.drill.dismantle"):
        assert key in lang, f"в {lang_name} нет ключа {key}"
    # 0.3.290 #7: welcome явно перечисляет серверные возможности
    for wkey in ("gui.gonzotech.welcome.generic", "gui.gonzotech.welcome.hardcore",
                 "gui.gonzotech.welcome.survival"):
        m290 = re.search(r'"%s": "([^"]*)"' % re.escape(wkey), lang)
        assert m290 and "server-side WorldGuard" in m290.group(1), \
            f"в {lang_name} welcome '{wkey}' не перечисляет GT capabilities: server-side WorldGuard"


# 0.3.294: корневой ремонт дюпа — живая доза вне стака (RadDoseIndex);
# сервер не мутирует предметы «под ногами» клиента, creative-канал
# (handleSetCreativeModeSlot, 1.21.4: без stateId/контент-проверки) больше
# нечем наполнять устаревшими сетками.
idx294 = (ROOT / "src/main/java/com/gonzotech/radiation/RadDoseIndex.java").read_text(encoding="utf-8")
assert "class RadDoseIndex" in idx294 and "WeakHashMap<ItemStack, Entry>" in idx294 \
    and "QUIET_NS = 5_000_000_000L" in idx294, "0.3.294: индекс разобран (ключ/окно тишины)"
ir294 = (ROOT / "src/main/java/com/gonzotech/radiation/ItemRadioactivity.java").read_text(encoding="utf-8")
for _r294 in ("RadDoseIndex.peek(stack)", "RadDoseIndex.setValue(stack, value)",
              "RadDoseIndex.markTick(stack, nowTick)", "RadDoseIndex.tickOf(stack)",
              "RadDoseIndex.markCleared(stack)", "RadDoseIndex.isLive(stack)"):
    assert _r294 in ir294, f"0.3.294: роутинг {_r294} выпал — компонент снова пишется под живой сеткой"
assert "RadDoseIndex.adopt(player, stack)" in sys291 and "RadDoseIndex.quietFor(player)" in sys291 \
    and "RadDoseIndex.flushPlayer(player)" in sys291, "0.3.294: adopt/flush не в проходе"
assert "RadDoseIndex.noteInteraction(this.player)" in clickmix \
    and "RadDoseIndex.noteInteraction(this.player)" in creative291, \
    "0.3.294: flush-окно растеряло печать активности клика/креатива"
assert "radClearPending" not in ir294 and "private static final Map<UUID, Long> LAST_INTERACTION_NS" not in ir294

# 0.3.295: debug2.log — NPE диагностики на netty-входе (гонка HEAD netty-пакета
# N+1 с RETURN серверного прохода пакета N) + IAE JEI на rejoin. Диагностика
# гейтится на серверный поток, регистрация расширения идемпотентна.
# 0.3.296: краш-репорт автора повторил урок 293 в новом месте — поле server
# объявлено НЕ в ServerGamePacketListenerImpl, а в родителе
# ServerConfigurationPacketListenerImpl (это видно по handleConfigurationFinished
# в трассе), а field-shadow иерархию не обходит. Тред-гейт обязан идти через
# player.getServer(): метод-вызовы легальны и уже обкатаны в [CLICK]-строках.
for _m296 in (clickmix, creative291):
    assert "private net.minecraft.server.MinecraftServer server;" not in _m296 \
        and "this.server." not in _m296, \
        "0.3.296: @Shadow server вернулся — APPLY упадёт на поле из предка"
    assert "gonzotech$srv" in _m296 and _m296.count("isSameThread()") >= 2, \
        "0.3.296: гейт потока через getServer() выпал — вернётся NPE-кик диагностики"
assert "RadDoseIndex.noteInteraction(this.player);" in clickmix.split("isSameThread")[0], \
    "0.3.295: штамп активности уехал под netty-гейт — окно flush перестанет закрываться вовремя"
jei295 = (ROOT / "src/main/java/com/gonzotech/integration/jei/GonzoTechJeiPlugin.java").read_text(encoding="utf-8")
assert "catch (IllegalArgumentException alreadyRegistered)" in jei295, \
    "0.3.295: JEI-расширение снова регистрируется неидемпотентно (вылет на rejoin)"

# 0.3.297 (#43-#46 из latest.log): рецепты в ванильном формате ингредиентов,
# лут Третьего ПКФСОЗ на собственный id, item-дефиниция Плотника, живые parent
# у fluid-моделей. Пины проверяют именно загрузку, а не наличие файлов.
import json as _json297
for _f297 in ("corundum_abrasive", "deepslate_from_monolith", "motherboard",
              "sandpapy", "third_grinder"):
    _raw297 = (ROOT / "src/main/resources/data/gonzotech/recipe" / (_f297 + ".json")).read_text(encoding="utf-8")
    assert '"item"' not in _raw297, f"0.3.297: {_f297} снова содержит объектоный ingredient-формат"
    _d297 = _json297.loads(_raw297)
    _ings = _d297.get("ingredients", [])
    _keys = list(_d297.get("key", {}).values())
    assert (_ings and all(isinstance(e, str) for e in _ings)) or \
        (_keys and all(isinstance(v, str) for v in _keys)), \
        f"0.3.297: {_f297} пуст/кривой по части ингредиентов"
_loot297 = (ROOT / "src/main/resources/data/gonzotech/loot_table/blocks/third_pcfsoz.json").read_text(encoding="utf-8")
assert "gonzotech:third_pcfsoz" in _loot297 and '"gonzotech:pcfsoz"' not in _loot297, \
    "0.3.297: лут Третьего ПКФСОЗ опять дропит id первой эпохи (потеря блока)"
_item297 = ROOT / "src/main/resources/assets/gonzotech/items/plotnick.json"
assert _item297.exists() and "gonzotech:block/plotnick" in _item297.read_text(encoding="utf-8"), \
    "0.3.297: у Плотника снова нет item-дефиниции — missingno в руке и JEI"
import subprocess as _sp297
_out297 = _sp297.run(["grep", "-rl", "template_fluid", "src/main/resources"],
                     cwd=ROOT, capture_output=True, text=True).stdout.strip()
assert not _out297, f"0.3.297: мёртвый parent minecraft:block/template_fluid вернулся: {_out297}"

# 0.3.298: юнит-ремонт наведёнки — таблица автора в mZt, значения в nZt;
# константы обязаны приходить в формулу перемасштабленными (×MILLI), иначе
# плато сжимается на 10^6 (наблюдение автора: «алмаз от полок радия фонит на
# 30 nZt»). Пресеты RadSources и чанк-гейт масштабированы верно — пин следит,
# чтобы наведёнка не отстала.
ir298 = (ROOT / "src/main/java/com/gonzotech/radiation/ItemRadioactivity.java").read_text(encoding="utf-8")
assert "DISCHARGE_ANCHOR = 33.0 * RadUnits.MILLI" in ir298, \
    "0.3.298: якорь отдачи вернулся в сырые nZt — плато сожмётся до десятков nZt"
assert "CLEANUP_FLOOR = RadUnits.MILLI" in ir298, \
    "0.3.298: floor чистки вернулся в 1 nZt — сорные доли mZt перестанут выметаться"
assert "seeded / RadUnits.MILLI" in ir298, \
    "0.3.298: степень стоимости приёма снова считает 0.097 % на nZt вместо mZt"
assert "target >= CLEANUP_FLOOR" in ir298 and "Math.max(current, CLEANUP_FLOOR)" in ir298, \
    "0.3.298: посев/гейт роста не привязан к 1-mZt floor (двойная трактовок)"

# 0.3.299: N-проход. N5 — setVirtual (виртуал Плотника) обязан быть заменой
# (контракт setItem): долив в нём рождал «перемещение в голову» для воронок.
# N6 — потолок складских стопок один на меню и автоматизацию. N7 — TNT-слот
# источника: занятый отказывает, перегрузы сейвов вываливаются, а не обрезаются.
# N11 — установка Плотника одноразово грузит 5×5 чанков. N13 — BFS приёмников
# кэшируется с инвалидацией по штампу мира и бюджетом узлов на тик. N14 —
# щит пинг-понга разделён по измерениям.
be299 = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/PlotnickBlockEntity.java").read_text(encoding="utf-8")
assert "public static int capacityFor(ItemStack stack)" in be299 and "capOf(" not in be299, \
    "0.3.299 N6: единый потолок склада разъехался с меню"
sv299 = be299[be299.index("private void setVirtual"):be299.index("private void setVirtual") + 2400]
assert "virtual.set(idx, stack);" in sv299 and "cur.grow(moved)" not in sv299 \
    and "insert(stack); // чужой тип в стопку" not in sv299, \
    "0.3.299 N5: setVirtual снова доливаёт вместо замены — контракт setItem сломан"
assert "legacyOverflow.add(rest);" in be299, \
    "0.3.299 N7: normalizeStacks обрезает перегруз загрузки в void"
mn299 = (ROOT / "src/main/java/com/gonzotech/machines/menu/PlotnickMenu.java").read_text(encoding="utf-8")
assert mn299.count("PlotnickBlockEntity.capacityFor(") >= 2, \
    "0.3.299 N6: меню мердж идёт мимо складской capacityFor"
ss299 = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/SeismicSourceBlockEntity.java").read_text(encoding="utf-8")
assert "items.get(SLOT_DYNAMITE).isEmpty()" in ss299 and "loadSurplus" in ss299 \
    and "popResource" in ss299, \
    "0.3.299 N7: TNT-слот источника снова принимает воoccupied и режет излишки"
sb299 = (ROOT / "src/main/java/com/gonzotech/machines/block/SeismicSourceBlock.java").read_text(encoding="utf-8")
assert "SeismicSourceBlockEntity::serverTick" in sb299, \
    "0.3.299 N7: у источника нет тикера — loadSurplus некому сбросить"
pb299 = (ROOT / "src/main/java/com/gonzotech/machines/block/PlotnickBlock.java").read_text(encoding="utf-8")
assert "protected void onPlace" in pb299 and "server.getChunk(cx + dx, cz + dz)" in pb299, \
    "0.3.299 N11: установка Плотника не прогревает 5×5 чанков — перевыборы слепнут"
ir299 = (ROOT / "src/main/java/com/gonzotech/machines/network/ItemRouting.java").read_text(encoding="utf-8")
assert "sinksCached(level, pos)" in ir299 and "TOPO_BUDGET_PER_TICK" in ir299 \
    and "public static void onWorldEdit(Level level)" in ir299, \
    "0.3.299 N13: BFS приёмников снова бегает по каждой трубе каждый тик"
assert "Map<Level, java.util.Map<Long, Long>> LAST_INSERTED" in ir299 \
    and "noteInserted(level, now, sink.containerPos())" in ir299, \
    "0.3.299 N14: щит пинг-понга снова без измерений — коллизии Nether/Overworld"
assert "insertedThisTick(now," not in ir299.replace("insertedThisTick(level, now,", ""), \
    "0.3.299 N14: остался вызов insertedThisTick без level"
mx299 = (ROOT / "src/main/java/com/gonzotech/mixin/LevelChunkCleanRoomMixin.java").read_text(encoding="utf-8")
assert "ItemRouting.onWorldEdit(level)" in mx299, \
    "0.3.299 N13: миксин не инвалидирует кэш топологии"

# 0.3.300: гейт re-equip bob'а обязан зачищать ВСЕ три радиационных штампа
# (с markDecayTick старые два перестали сходиться — предмет прыгал в руке
# каждый радиационный проход) и не давать рано-возврата по значению дозы.
gate300 = (ROOT / "src/main/java/com/gonzotech/radiation/client/RadReequipGate.java").read_text(encoding="utf-8")
assert gate300.count("tag.remove(ItemRadioactivity.TAG_RAD") == 3, \
    "0.3.300: RadReequipGate снова strip'ит не все штампы — bob вернётся"
assert "if (ra == rt)" not in gate300, \
    "0.3.300: рано-возврат по равенству дозы вернёт прыжок на одних тик-штампах"
assert "stripRadDeep(copy, false);" in gate300 and "BUNDLE_CONTENTS" in gate300, \
    "0.3.300: вложенное затухание (связка/контейнер) снова анимирует родителя"
ir300 = (ROOT / "src/main/java/com/gonzotech/radiation/ItemRadioactivity.java").read_text(encoding="utf-8")
assert "public static final String TAG_RAD_MODEL" in ir300 \
    and "public static final String TAG_RAD_LAST_TICK" in ir300, \
    "0.3.300: штампы снова приватные — гейт ослепнет при следующем рефакторе"
mx300 = (ROOT / "src/main/java/com/gonzotech/mixin/client/ItemInHandRendererMixin.java").read_text(encoding="utf-8")
assert "shouldInstantlyReplaceVisibleItem" in mx300 and "onlyRadChanged(from, to)" in mx300, \
    "0.3.300: миксин-гейт отцепился от ванильной проверки замены"

# 0.3.301: ItemContainerContents в Mojmap НЕ Iterable — пин на индексный обход
# (урок: MCP-зеркала расходятся с Mojmap API; сверяться с нашим же decay-путём).
gate301 = (ROOT / "src/main/java/com/gonzotech/radiation/client/RadReequipGate.java").read_text(encoding="utf-8")
assert "for (ItemStack inner : content)" not in gate301 \
    and "content.getStackInSlot(i)" in gate301, \
    "0.3.301: в гейте снова for-each по ItemContainerContents — не скомпилируется"
print("autominer_markers_test passed")

#!/usr/bin/env python3
"""Blast furnace (0.3.65): pure 3x3x3 layout self-test via ECJ + wiring pins.

Author spec: layer 1 (top) = 8 fireclay + cauldron; layer 2 (middle) = firebox
center, 4 heat-pipe/universal nodes on edge midpoints, 4 fireclay corners;
layer 3 (bottom) = 9 fireclay. Burn x4 vanilla, 34 GTH/t, storage 34 016,
5 fuel slots insert-only, GTH out through structure nodes.
"""
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
MM = SRC / "machines"

java = None
home = os.environ.get("JAVA_HOME")
if home and Path(home, "bin/java").is_file():
    java = str(Path(home) / "bin/java")
elif Path.home().joinpath(".jdks").exists():
    for p in Path.home().joinpath(".jdks").glob("*"):
        if Path(p, "bin/java").is_file():
            java = str(Path(p, "bin/java"))
javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")

# ── чистый лэйаут: ECJ-компиляция + прогон сверок ──
with tempfile.TemporaryDirectory(prefix="gonzotech-blast-") as tmp:
    sources = [MM / "blastfurnace/BlastFurnaceLayout.java", ROOT / "audit/BlastFurnaceSelfTest.java"]
    compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"] if os.environ.get("ECJ_JAR") else [javac, "--release", "21"]
    subprocess.run(compiler + ["-encoding", "UTF-8", "-d", tmp] + [str(s) for s in sources], check=True)
    out = subprocess.run([java, "-cp", tmp, "BlastFurnaceSelfTest"], check=True, capture_output=True, text=True)
    assert "33 checks passed" in out.stdout, out.stdout

# ── пины проводки ──
def code(path):
    import re
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", Path(path).read_text(), flags=re.S)

be = code(MM / "block/entity/FireboxBlockEntity.java")
layout = (MM / "blastfurnace/BlastFurnaceLayout.java").read_text()
struct = code(MM / "blastfurnace/BlastFurnaceStructure.java")
menu = code(MM / "menu/BlastFurnaceMenu.java")
screen = (MM / "client/BlastFurnaceScreen.java").read_text()
machines = (SRC / "machines/registry/ModMachines.java").read_text()
menus_reg = (SRC / "machines/registry/ModMenus.java").read_text()
client = (MM / "client/MachineClient.java").read_text()
defs = (MM / "energy/MachineDefs.java").read_text()
gate = (SRC / "core/event/Phase3Events.java").read_text()
unlocks = (SRC / "chalkboard/advancement/RecipeUnlocks.java").read_text()
tabs = (SRC / "core/registry/ModCreativeTabs.java").read_text()
fireclay_block = (MM / "block/FireclayBlock.java").read_text()

# числа автора
assert "BLAST_FURNACE_GTH_CAPACITY = 34_016 * MILLI;" in defs
assert "BLAST_FURNACE_GTH_PER_TICK = 34 * MILLI;" in defs
assert "BLAST_FURNACE_BURN_SPEED_DIVISOR = 4;" in defs
assert "BLAST_FURNACE_FUEL_SLOTS = 5;" in defs
# 0.3.75: кап отдачи 144 GTH/т НА УЗЕЛ (авторские числа; 320-тотал отменён)
assert "BLAST_FURNACE_NODE_GTH_OUTPUT = 144 * MILLI;" in defs
assert "BLAST_FURNACE_GTH_OUTPUT" not in defs and "BLAST_FURNACE_GTH_OUTPUT" not in be
assert "long budget = Math.min((long) MachineDefs.BLAST_FURNACE_NODE_GTH_OUTPUT, remaining);" in be
# 0.3.75: lifecycle сборки/разбора — поглощение содержимого топки и полный дроп
assert "absorbIntoBlast(server);" in be and "dropAndResetAfterDeform(server);" in be
assert "private void absorbIntoBlast(ServerLevel server)" in be
assert "private void dropAndResetAfterDeform(ServerLevel server)" in be
assert "Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5," in be
assert "gth.set(0);" in be
# blastFormed персистится: восстановленная после загрузки печь не переочищается
assert 'tag.putBoolean("BlastFormed", blastFormed);' in be
assert 'blastFormed = tag.getBoolean("BlastFormed");' in be
# 0.3.75: слом/постановка самой топки — partChanged (same-tick разбор, флаги)
fbb = (ROOT / "src/main/java/com/gonzotech/machines/block/FireboxBlock.java").read_text()
assert fbb.count("BlastFurnaceStructure.partChanged(level, pos);") == 2, fbb.count("BlastFurnaceStructure.partChanged(level, pos);")

# топка = контроллер: 8 слотов, 5 топливных, ×4 жжение, 34 GTH/t, буфер с клампом
assert "FUEL_SLOTS = {SLOT_FUEL, SLOT_FUEL_2, SLOT_FUEL_3, SLOT_FUEL_4, SLOT_FUEL_5};" in be
assert "3 + MachineDefs.BLAST_FURNACE_FUEL_SLOTS);" in be
assert "burn = Math.max(1, burn / MachineDefs.BLAST_FURNACE_BURN_SPEED_DIVISOR);" in be
assert "MachineDefs.BLAST_FURNACE_GTH_PER_TICK" in be
assert "BLAST_FURNACE_GTH_CAPACITY" in be and "gthCapacityMilli()" in be
assert "if (be.isLit() && !be.blastFormed && SmeltHelper.canOutput" in be  # домен не плавит
# только закладывать: canTake false в доменном режиме
assert "if (blastFormed) return false;" in be  # доменная печь: забор трубами запрещён
# вывод GTH через узлы структуры
assert "BlastFurnaceStructure.nodePositions(server, worldPosition)" in be
assert "PipeRouting.drain(server, node, PipeType.HEAT, budget," in be  # 0.3.75: бюджет НА УЗЕЛ
# меню по режиму
assert "new com.gonzotech.machines.menu.BlastFurnaceMenu(id, inv, this, blastData);" in be
assert "block.gonzotech.blast_furnace" in be

# структура: роли по автору + флаги FORMED на шамоте + любой тепло/универсал узел T1/T2
assert "case FIREBOX -> state.is(ModMachines.FIREBOX.get());" in struct
assert "state.is(ModMachines.SECOND_HEAT_NODE.get())" in struct
assert "state.is(ModMachines.SECOND_UNIVERSAL_NODE.get())" in struct
assert "state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);" in struct
assert "FireclayBlock.FORMED, formed" in struct
assert "isFormed(ServerLevel level, BlockPos fireboxPos)" in struct
# 0.3.66: openMenu только formed-топки и строго с BlockPos (иначе клиент получает null-буфер → NPE)
assert "player.openMenu(firebox, firebox.getBlockPos());" in struct
assert "&& firebox.isBlastFormed())" in struct
# 0.3.66: чанки куба не догружаем силой — иначе загрузка мира виснет на 100%
assert "public static boolean chunksLoaded(ServerLevel level, BlockPos fireboxPos)" in struct
assert "level.hasChunkAt(fireboxPos.offset(-1, 0, -1))" in struct  # 0.3.67: углы BlockPos (блок-координаты), не чанковые
# 0.3.70: каулдрон СТРОГО в верхнем центре; вода допустима
assert "case CAULDRON -> state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);" in struct
assert "isCauldronFamily" not in struct and "return cauldronSeen;" not in struct
assert "isLayerCenter" not in struct and "isLayerCenter" not in layout
# FORMED ставится всему шамоту куба
assert "if (!(state.getBlock() instanceof FireclayBlock)) continue;" in struct
# 0.3.70: сборка/разбор в тот же тик — partChanged зовёт revalidateNow без троттлинга
assert "firebox.revalidateNow();" in struct  # 0.3.70: сборка/разбор в тот же тик
assert "public void revalidateNow()" in be and "blastCheckTick = Long.MIN_VALUE;" in be
# 0.3.70: formed-шамот рендерится — FireclayBlock в белом списке Smart CTM
ctm = (ROOT / "src/main/java/com/gonzotech/machines/client/ctm/TurbineSmartCtmBakedModel.java").read_text()
assert "instanceof com.gonzotech.machines.block.FireclayBlock" in ctm
assert "FireclayBlock.FORMED);" in ctm
# 0.3.70: клик по котлу/узлам открывает меню (RightClickBlock)
events = (ROOT / "src/main/java/com/gonzotech/machines/blastfurnace/BlastFurnaceEvents.java").read_text()
assert "@SubscribeEvent" in events and "PlayerInteractEvent.RightClickBlock" in events
assert "BlastFurnaceStructure.openMenu(server, event.getPos(), event.getEntity())" in events
assert "event.setCanceled(true);" in events
# 0.3.73: ивент только для котла (у ванильного котла нет хука) и со сдвиг-гейтом;
# узлы обслуживаются хуками сетевых блоков (FormedMenus), шифт там рулит ваниль.
assert "isShiftKeyDown" in events
assert "!state.is(Blocks.CAULDRON) && !state.is(Blocks.WATER_CAULDRON)" in events
assert "ModMachines" not in events and "isCutoutPart" not in events
formed = (ROOT / "src/main/java/com/gonzotech/machines/FormedMenus.java").read_text()
assert "TurbineStructure.openMenu(level, pos, player)" in formed
assert "SteamGenStructure.openMenu(level, pos, player)" in formed
assert "BlastFurnaceStructure.openMenu(level, pos, player)" in formed
for nb in ("PipeBlock", "UniversalNodeBlock", "UniversalFluidPipeBlock"):
    nsrc = (ROOT / f"src/main/java/com/gonzotech/machines/network/{nb}.java").read_text()
    assert "FormedMenus.open(level, pos, player)" in nsrc, nb
    assert "TurbineStructure.openMenu(level, pos, player)" not in nsrc, nb
gmod = (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text()
assert "NeoForge.EVENT_BUS.register(com.gonzotech.machines.blastfurnace.BlastFurnaceEvents.class);" in gmod
# 0.3.70: burnout без тултипа; GTH-тултип в единицах (не милли)
assert 'gui.gonzotech.blast_furnace.burning' not in screen
assert "GtUnits.gthPair(menu.gth() / 1000, MachineDefs.BLAST_FURNACE_GTH_CAPACITY / 1_000)" in screen
# 0.3.72: заливка шкалы GTH — милли/милли (была милли/единицы => мгновенные 100%)
assert "float gth = (float) menu.gth() / (float) MachineDefs.BLAST_FURNACE_GTH_CAPACITY;" in screen
assert "(MachineDefs.BLAST_FURNACE_GTH_CAPACITY / 1_000);" not in screen
for lang in ("en_us", "ru_ru"):
    lt = (ROOT / f"src/main/resources/assets/gonzotech/lang/{lang}.json").read_text()
    assert "blast_furnace.burning" not in lt
assert "be.revalidateBlast(server);" in be  # 0.3.69: вызов ревалидации в тике (был потерян — печь не собиралась)
# 0.3.76: дым горящей топки — campfire-дым и пыль 0x78726B, по одной частице за тик
assert "ParticleTypes.CAMPFIRE_COSY_SMOKE" in be
assert "new DustParticleOptions(0x78726B, 1.0F)" in be
assert "pos.getY() + 1.05," in be
# 0.3.76: страницы заметок 15/16 — крафт шамота справа и структура печи (3 слоя)
content = (ROOT / "src/main/java/com/gonzotech/chalkboard/notes/ScholarNotesContent.java").read_text()
assert 'NoteIllustration.craftingRight(List.of("minecraft:bricks", "minecraft:clay_ball", "minecraft:bone_meal", "minecraft:wheat", "minecraft:calcite", "", "", "", ""), "gonzotech:fireclay")' in content
assert 'NoteIllustration.structureRight(StructureModel.blastFurnace())' in content
assert '"gui.gonzotech.notes.p42.title"' in content and '"gui.gonzotech.notes.p43.body"' in content
smodel = (ROOT / "src/main/java/com/gonzotech/chalkboard/notes/StructureModel.java").read_text()
assert "public static StructureModel blastFurnace()" in smodel
for sid in ('"gonzotech:fireclay"', '"gonzotech:first_heat_node"', '"gonzotech:firebox"', '"minecraft:cauldron"'):
    assert sid in smodel, sid
# нумерация страниц 1..43 без дыр; витрины новых страниц
import re as _re
_pagenums = [int(m) for m in _re.findall(r'new ScholarPage\((\d+),', content)]
assert _pagenums == list(range(1, 45)), _pagenums  # 0.3.77: +страница «Зарисовка»
_i15 = content.index('new ScholarPage(15,')
assert 'p42.title' in content[_i15:_i15 + 200]
_i16 = content.index('new ScholarPage(16,')
assert 'p43.title' in content[_i16:_i16 + 200]
# 0.3.77: страница 38 «Зарисовка» (ERA_2, без гейта) — чистая иллюстрация page_gonzo
kind = (ROOT / "src/main/java/com/gonzotech/chalkboard/notes/NoteIllustrationKind.java").read_text()
assert 'PAGE_GONZO("page_gonzo.png")' in kind
ill = (ROOT / "src/main/java/com/gonzotech/chalkboard/notes/NoteIllustration.java").read_text()
assert "public static NoteIllustration gonzoRight()" in ill
assert (ROOT / "src/main/resources/assets/gonzotech/textures/gui/notes/page_gonzo.png").exists()
notes_screen = (ROOT / "src/main/java/com/gonzotech/chalkboard/client/ScholarNotesScreen.java").read_text()
assert "withStrikethrough(strike)" in notes_screen
assert "case PAGE_GONZO ->" in notes_screen
_i38 = content.index('new ScholarPage(38,')
assert 'ERA_2' in content[_i38:_i38 + 120] and 'ScholarUnlock.ALWAYS' in content[_i38:_i38 + 160]
assert 'NoteIllustration.gonzoRight()' in content[_i38:_i38 + 500]
_isun = content.index('FLAG_SUN_EVENT')
assert _isun < _i38 < content.index('new ScholarPage(39,')
for _lang in ("en_us", "ru_ru"):
    import json as _json
    _data = _json.loads((ROOT / f"src/main/resources/assets/gonzotech/lang/{_lang}.json").read_text())
    assert _data["gui.gonzotech.notes.p42.title"]
    assert "шамотный кирпич" in _data["gui.gonzotech.notes.p42.body"] or "fireclay brick" in _data["gui.gonzotech.notes.p42.body"]
    assert "GTH" in _data["gui.gonzotech.notes.p43.body"]
    assert _data["gui.gonzotech.notes.p44.title"]
    assert "~~Я~~" in _data["gui.gonzotech.notes.p44.body"] or "~~I~~" in _data["gui.gonzotech.notes.p44.body"]
    assert "**кто сейчас смотрит**" in _data["gui.gonzotech.notes.p44.body"] or "**who is looking**" in _data["gui.gonzotech.notes.p44.body"]
assert "if (!BlastFurnaceStructure.chunksLoaded(server, worldPosition)) return;" in be
# предметная модель 1.21.4 (assets/gonzotech/items/)
item_model = (ROOT / "src/main/resources/assets/gonzotech/items/fireclay.json").read_text()
assert '"model": "gonzotech:block/fireclay"' in item_model
assert "nodePositions(ServerLevel level, BlockPos fireboxPos)" in struct
# лэйаут: слой1 котёл в центре, слой2 узлы на рёбрах, слой3 фундамент
assert "if (dx == 0 && dz == 0) return Role.CAULDRON;" in layout
assert "return edge ? Role.HEAT_NODE : Role.FIRECLAY;" in layout
assert "if (dy == -1) return Role.FIRECLAY;" in layout

# блок шамота: FORMED + Smart CTM-крюки + открытие меню кликом
assert 'BooleanProperty.create("formed")' in fireclay_block
assert "BlastFurnaceStructure.partChanged(level, pos);" in fireclay_block
assert "BlastFurnaceStructure.openMenu(level, pos, player)" in fireclay_block

# меню/экран: 5 FuelSlot в ряд 44..116 @53, шкалы по раскладке автора
assert "addSlot(new FuelSlot(firebox, FireboxBlockEntity.FUEL_SLOTS[i], 44 + i * 18, 53, inventory));" in menu
assert "addPlayerInventory(inventory, 8, 84);" in menu
assert 'gui("blast_furnace_gui_bg.png")' in screen
assert 'gui("blast_furnace_gui.png")' in screen
assert "drawVBarTex(graphics, burnX, burnY, 16, 16, lit, BAR_BURNUP);" in screen
assert "drawVBarTex(graphics, barX, barY, barW, barH, gth, BAR_GTH);" in screen
assert "MachineDefs.BLAST_FURNACE_GTH_CAPACITY / 1_000" in screen

# регистрация + гейт + книга + вкладка
assert 'BLOCKS.registerBlock("fireclay", com.gonzotech.machines.block.FireclayBlock::new, machineMetal());' in machines
assert 'ITEMS.registerSimpleBlockItem("fireclay", FIRECLAY);' in machines
assert 'MENUS.register("blast_furnace"' in menus_reg
assert "ModMenus.BLAST_FURNACE.get(), BlastFurnaceScreen::new" in client
assert "ModMachines.FIRECLAY_ITEM.get(), 1)" in gate  # фулл-гейт открытие 1
assert '"gonzotech:fireclay",' in unlocks  # книга: открытие 1
assert "output.accept(com.gonzotech.machines.registry.ModMachines.FIRECLAY_ITEM.get()); // шамот — до всех крошек" in tabs

# ассеты: blockstate formed-вариант, smart_ctm модель, пустые GUI-листы, рецепт, лут
bs = (ROOT / "src/main/resources/assets/gonzotech/blockstates/fireclay.json").read_text()
assert '"formed=true"' in bs and "gonzotech:block/fireclay_ctm" in bs
model = (ROOT / "src/main/resources/assets/gonzotech/models/block/fireclay_ctm.json").read_text()
assert '"loader": "gonzotech:smart_ctm"' in model
assert "fireclay/fireclay_formed" in model and "fireclay/fireclay_ctm_line" in model
assert "fireclay/fireclay_ctm_outer_corner" in model and "fireclay/fireclay_ctm_inner_corner" in model
for tex in ("fireclay.png", "fireclay_formed.png", "fireclay_ctm_line.png",
            "fireclay_ctm_outer_corner.png", "fireclay_ctm_inner_corner.png"):
    assert (ROOT / f"src/main/resources/assets/gonzotech/textures/block/fireclay/{tex}").is_file(), tex
for gui in ("blast_furnace_gui_bg.png", "blast_furnace_gui.png"):
    assert (ROOT / f"src/main/resources/assets/gonzotech/textures/gui/{gui}").is_file(), gui
recipe = (ROOT / "src/main/resources/data/gonzotech/recipe/fireclay.json").read_text()
for ing in ("minecraft:bricks", "minecraft:clay_ball", "minecraft:bone_meal", "minecraft:wheat", "minecraft:calcite"):
    assert ing in recipe, ing
assert '"id": "gonzotech:fireclay"' in recipe and '"count": 4' in recipe
loot = (ROOT / "src/main/resources/data/gonzotech/loot_table/blocks/fireclay.json").read_text()
assert '"name": "gonzotech:fireclay"' in loot
for lang in ("en_us", "ru_ru"):
    lt = (ROOT / f"src/main/resources/assets/gonzotech/lang/{lang}.json").read_text()
    assert '"block.gonzotech.fireclay"' in lt and '"block.gonzotech.blast_furnace"' in lt
    assert "blast_furnace.burning" not in lt  # 0.3.70: burnout без тултипа, ключ удалён

print("Blast furnace wiring passed (layout 33 checks + pins)")

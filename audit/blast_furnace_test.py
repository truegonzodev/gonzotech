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
assert "PipeRouting.drain(server, node, PipeType.HEAT, remaining," in be
# меню по режиму
assert "new com.gonzotech.machines.menu.BlastFurnaceMenu(id, inv, this, blastData);" in be
assert "block.gonzotech.blast_furnace" in be

# структура: роли по автору + флаги FORMED на шамоте + любой тепло/универсал узел T1/T2
assert "case FIREBOX -> state.is(ModMachines.FIREBOX.get());" in struct
assert "state.is(ModMachines.SECOND_HEAT_NODE.get())" in struct
assert "state.is(ModMachines.SECOND_UNIVERSAL_NODE.get())" in struct
assert "state.is(Blocks.CAULDRON);" in struct
assert "FireclayBlock.FORMED, formed" in struct
assert "isFormed(ServerLevel level, BlockPos fireboxPos)" in struct
# 0.3.66: openMenu только formed-топки и строго с BlockPos (иначе клиент получает null-буфер → NPE)
assert "player.openMenu(firebox, firebox.getBlockPos());" in struct
assert "&& firebox.isBlastFormed())" in struct
# 0.3.66: чанки куба не догружаем силой — иначе загрузка мира виснет на 100%
assert "public static boolean chunksLoaded(ServerLevel level, BlockPos fireboxPos)" in struct
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
    assert '"gui.gonzotech.blast_furnace.burning"' in lt

print("Blast furnace wiring passed (layout 33 checks + pins)")

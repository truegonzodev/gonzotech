#!/usr/bin/env python3
"""Tongs & ladle (0.3.79, EPOCH3-BASE 2.8): pure storage logic compiled and
smoke-run (ECJ), plus wiring pins. Author numbers: -40% radioactivity,
-80% toxicity for carried content (replace the old -60/-70/-90 notes).
Needs JDK 21 in JAVA_HOME/PATH or a Java 21 runtime + ECJ_JAR.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
CORE_ITEM = ROOT / "src/main/java/com/gonzotech/core/item"
JAVA_HOME = os.environ.get("JAVA_HOME")
java = str(Path(JAVA_HOME) / "bin/java") if JAVA_HOME else shutil.which("java")
javac = str(Path(JAVA_HOME) / "bin/javac") if JAVA_HOME else shutil.which("javac")
if not java or not Path(java).is_file():
    raise SystemExit("Java 21 required: set JAVA_HOME or PATH")

HARNESS = '''public class TongsLadleSelfTest {
    public static void main(String[] args) {
        // Щипцы: ртуть/цезий "чистого" вида НЕ берут; руды/поллуцит/киноварь берут.
        check(!com.gonzotech.core.item.TongsLogic.canPick("mercury_ingot"), "tongs mercury_ingot");
        check(!com.gonzotech.core.item.TongsLogic.canPick("cesium_dust"), "tongs cesium_dust");
        check(!com.gonzotech.core.item.TongsLogic.canPick("mercury_nugget"), "tongs mercury_nugget");
        check(com.gonzotech.core.item.TongsLogic.canPick("raw_mercury"), "tongs raw_ore");
        check(com.gonzotech.core.item.TongsLogic.canPick("pollucite"), "tongs pollucite");
        check(com.gonzotech.core.item.TongsLogic.canPick("cinnabar"), "tongs cinnabar");
        check(com.gonzotech.core.item.TongsLogic.canPick("uranium_ingot"), "tongs uranium");
        // Полные id «namespace:path» (0.3.83): gonzo-предметы больше не «съедаются».
        check(com.gonzotech.core.item.TongsLogic.canPick("gonzotech:uranium_ingot"), "tongs ns uranium");
        check(!com.gonzotech.core.item.TongsLogic.canPick("minecraft:mercury_ingot"), "tongs ns mercury");
        check(com.gonzotech.core.item.TongsLogic.pathOf("gonzotech:uranium_ingot").equals("uranium_ingot"), "pathOf ns");
        check(com.gonzotech.core.item.TongsLogic.pathOf("pollucite").equals("pollucite"), "pathOf bare");
        check(!com.gonzotech.core.item.TongsLogic.canPick("gonzotech:tongs"), "ns tongs in tongs");
        // Носитель в носитель не кладётся.
        check(!com.gonzotech.core.item.TongsLogic.canPick("tongs"), "tongs in tongs");
        check(!com.gonzotech.core.item.TongsLogic.canPick("ladle"), "ladle in tongs");
        // Вместимость 64 одного вида.
        check(com.gonzotech.core.item.TongsLogic.roomFor("", 0, "uranium_ingot", 64) == 64, "empty 64");
        check(com.gonzotech.core.item.TongsLogic.roomFor("uranium_ingot", 53, "uranium_ingot", 40) == 11, "top-up");
        check(com.gonzotech.core.item.TongsLogic.roomFor("uranium_ingot", 64, "uranium_ingot", 1) == 0, "full");
        check(com.gonzotech.core.item.TongsLogic.roomFor("uranium_ingot", 10, "iron_ingot", 5) == 0, "mixed");
        // Ковш: ртуть/цезий + ванильные контейнеры (автор 02.10.2026).
        check(com.gonzotech.core.item.LadleLogic.canPickItem("mercury_ingot"), "ladle mercury");
        check(com.gonzotech.core.item.LadleLogic.canPickItem("cesium_nugget"), "ladle cesium");
        check(com.gonzotech.core.item.LadleLogic.canPickItem("white_shulker_box"), "ladle shulker");
        check(com.gonzotech.core.item.LadleLogic.canPickItem("bundle"), "ladle bundle");
        check(com.gonzotech.core.item.LadleLogic.canPickItem("minecraft:white_shulker_box"), "ladle ns shulker");
        check(!com.gonzotech.core.item.LadleLogic.canPickItem("gonzotech:ladle"), "ns ladle in ladle");
        check(!com.gonzotech.core.item.LadleLogic.canPickItem("uranium_ingot"), "ladle not uranium");
        // Носители НИКОГДА не гнездятся (щипцы↔щипцы, ковш↔ковш, щипцы↔ковш).
        check(!com.gonzotech.core.item.LadleLogic.canPickItem("tongs"), "tongs in ladle");
        check(!com.gonzotech.core.item.LadleLogic.canPickItem("ladle"), "ladle in ladle");
        check(!com.gonzotech.core.item.TongsLogic.canPick("white_shulker_box") == false, "shulker in tongs ok");
        check(com.gonzotech.core.item.LadleLogic.roomForItem("", 0, "cesium_dust", 64) == 64, "ladle item cap");
        check(com.gonzotech.core.item.LadleLogic.roomForItem("cesium_dust", 64, "cesium_dust", 3) == 0, "ladle full");
        // Жидкость: порция 1000 mB, один вид.
        check(com.gonzotech.core.item.LadleLogic.roomForFluid("", 0, "minecraft:water", 1000) == 1000, "scoop");
        check(com.gonzotech.core.item.LadleLogic.roomForFluid("minecraft:water", 400, "minecraft:water", 900) == 600, "top-up fluid");
        check(com.gonzotech.core.item.LadleLogic.roomForFluid("minecraft:water", 400, "gonzotech:mash", 100) == 0, "mixed fluid");
        check(com.gonzotech.core.item.LadleLogic.roomForFluid("minecraft:water", 1000, "minecraft:water", 1) == 0, "fluid full");
        System.out.println("TongsLadleSelfTest passed");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
'''

with tempfile.TemporaryDirectory(prefix="gonzotech-tongs-ladle-") as output:
    src = Path(output) / "TongsLadleSelfTest.java"
    src.write_text(HARNESS)
    sources = [CORE_ITEM / "TongsLogic.java", CORE_ITEM / "LadleLogic.java", src]
    if os.environ.get("ECJ_JAR"):
        compiler = [java, "-jar", os.environ["ECJ_JAR"], "-21", "-proc:none"]
    elif javac and Path(javac).is_file():
        compiler = [javac, "--release", "21"]
    else:
        raise SystemExit("javac required (or supply ECJ_JAR with a Java 21 runtime)")
    subprocess.run(compiler + ["-d", output] + [str(p) for p in sources], check=True)
    subprocess.run([java, "-cp", output, "TongsLadleSelfTest"], check=True)

# ── Пины проводки ──
carrier = (ROOT / "src/main/java/com/gonzotech/radiation/CarrierItem.java").read_text()
assert "double RADIOACTIVITY_FACTOR = 0.60;" in carrier   # −40 % (автор 01.10.2026)
assert "double TOXICITY_FACTOR = 0.20;" in carrier        # −80 %
assert "RadSources.emissionOfStack(content) * RADIOACTIVITY_FACTOR;" in carrier
assert "ItemToxicity.toxicityOfStack(content) * TOXICITY_FACTOR;" in carrier

rads = (ROOT / "src/main/java/com/gonzotech/radiation/RadSources.java").read_text()
assert "if (stack.getItem() instanceof CarrierItem carrier) return carrier.carriedEmission(stack);" in rads
tox = (ROOT / "src/main/java/com/gonzotech/radiation/ItemToxicity.java").read_text()
assert "if (stack.getItem() instanceof CarrierItem carrier) return carrier.carriedToxicity(stack);" in tox

base = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/BaseMachineBlockEntity.java").read_text()
assert "stack.getItem() instanceof CarrierItem carrier && carrier.hasCarried(stack)" in base
assert "carrier.takeCarried(stack);" in base
assert "Containers.dropItemStack(level," in base

for slot in ("FuelSlot", "NuclearFuelSlot", "PumpInputSlot", "AlloyFoundryMenu", "SecondGrinderMenu"):
    src = (ROOT / f"src/main/java/com/gonzotech/machines/menu/{slot}.java").read_text()
    assert "mayPlace(carrier.previewCarried(stack));" in src, slot

moditems = (ROOT / "src/main/java/com/gonzotech/core/registry/ModItems.java").read_text()
assert 'ITEMS.registerItem("tongs", props -> new com.gonzotech.core.item.TongsItem(' in moditems
assert 'ITEMS.registerItem("ladle", props -> new com.gonzotech.core.item.LadleItem(' in moditems
tabs = (ROOT / "src/main/java/com/gonzotech/core/registry/ModCreativeTabs.java").read_text()
assert "ModItems.TONGS.get())" in tabs and "ModItems.LADLE.get())" in tabs

for lang in ("en_us", "ru_ru"):
    import json
    data = json.loads((ROOT / f"src/main/resources/assets/gonzotech/lang/{lang}.json").read_text())
    assert data["item.gonzotech.tongs"] and data["item.gonzotech.ladle"]
    assert "%s" in data["tooltip.gonzotech.carrier.contains"]
for name in ("tongs", "ladle"):
    assert (ROOT / f"src/main/resources/assets/gonzotech/items/{name}.json").is_file()
    assert (ROOT / f"src/main/resources/assets/gonzotech/models/item/{name}.json").is_file()
    assert (ROOT / f"src/main/resources/assets/gonzotech/textures/item/{name}.png").is_file()

# ── Пины фиксов компиляции автора (0.3.80): точные сигнатуры 1.21.4 ──
for item in ("TongsItem", "LadleItem"):
    src = (ROOT / f"src/main/java/com/gonzotech/core/item/{item}.java").read_text()
    assert "public boolean overrideStackedOnOther(" in src, item          # boolean, не ClickAction
    assert "public boolean overrideOtherStackedOnMe(" in src, item
    assert "net.minecraft.world.entity.SlotAccess cursor) {" in src, item  # 6-й параметр SlotAccess
    assert "public InteractionResult use(" in src, item                   # 1.21.4: без InteractionResultHolder
    assert "InteractionResultHolder" not in src, item
    assert "import net.minecraft.world.item.component.CustomData;" in src, item
    assert "player.getInventory().add(content)" in src, item              # мешочек: содержимое в инвентарь
    assert "ClickAction.PASS" not in src and "ClickAction.SUCCESS" not in src, item

firebox = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/FireboxBlockEntity.java").read_text()
# дым 0.3.76: параметр serverTick pos, а не статически недоступное поле worldPosition
assert "pos.getX() + 0.5 + (server.random.nextDouble() - 0.5) * 0.4," in firebox
assert "worldPosition.getX() + 0.5 + (server.random" not in firebox

logic = (ROOT / "src/main/java/com/gonzotech/core/item/LadleLogic.java").read_text()
assert 'itemIdPath.endsWith("shulker_box")' in logic
assert "!TongsLogic.isCarrier(itemIdPath)" in logic

# ── Пины 0.3.83: полный id + deep-обход хранилищ + наведёнка носителей ──
for item in ("TongsItem", "LadleItem"):
    src = (ROOT / f"src/main/java/com/gonzotech/core/item/{item}.java").read_text()
    assert "BuiltInRegistries.ITEM.getKey(other.getItem()).toString()" in src, item   # полный id
    assert "BuiltInRegistries.ITEM.getKey(incoming.getItem()).toString()" in src, item
    assert 'tooltip.gonzotech.shielding' in src and 'tooltip.gonzotech.carrier.full' in src, item
    assert 'tooltip.gonzotech.carrier.hint' in src, item

rads = (ROOT / "src/main/java/com/gonzotech/radiation/RadSources.java").read_text()
assert "public static double emissionDeep(ItemStack stack)" in rads
assert "container.nonEmptyItems()" in rads        # ItemContainerContents (шалкеры/моды)
assert "bundle.items()" in rads                   # BundleContents (связки)
assert "DataComponents.BUNDLE_CONTENTS" in rads
assert "depth < 4" in rads                        # защита от циклов

radsys = (ROOT / "src/main/java/com/gonzotech/radiation/RadiationSystem.java").read_text()
assert radsys.count("RadSources.emissionDeep(stack)") >= 2      # intrinsic + per-stack
assert "secondStackEmission" in radsys and "stackSource" in radsys
assert "RadSources.emissionDeep(container.getItem(i))" in radsys

chunk = (ROOT / "src/main/java/com/gonzotech/radiation/ChunkRadiationData.java").read_text()
assert "RadSources.emissionDeep(container.getItem(i))" in chunk  # дозиметр видит вложенное

ir = (ROOT / "src/main/java/com/gonzotech/radiation/ItemRadioactivity.java").read_text()
assert "double intrinsic = stack.getItem() instanceof CarrierItem" in ir  # фундамент=0

import json
for lang in ("en_us", "ru_ru"):
    data = json.loads((ROOT / f"src/main/resources/assets/gonzotech/lang/{lang}.json").read_text())
    assert data["tooltip.gonzotech.carrier.hint"] and data["tooltip.gonzotech.carrier.full"]

# ── Пины 0.3.84: переименование path→id доведено до конца + deep-тултип ──
tongs = (ROOT / "src/main/java/com/gonzotech/core/item/TongsItem.java").read_text()
assert "store(magazine, id, itemCount(magazine) + moved);" in tongs
assert "store(magazine, path," not in tongs
ladle = (ROOT / "src/main/java/com/gonzotech/core/item/LadleItem.java").read_text()
assert "id.equals(BUCKET_ITEM)" in ladle
assert "path.equals(BUCKET_ITEM)" not in ladle
radtip = (ROOT / "src/main/java/com/gonzotech/radiation/client/RadTooltip.java").read_text()
assert "RadSources.emissionDeep(event.getItemStack())" in radtip   # X+Y в тултипе
assert "ItemRadioactivity.totalEmission(event.getItemStack())" not in radtip

# ── Пины 0.3.85: ЛКМ-вставка / ПКМ-извлечение, update-merge, отступы лора ──
for item in ("TongsItem", "LadleItem"):
    src = (ROOT / f"src/main/java/com/gonzotech/core/item/{item}.java").read_text()
    assert "stack.update(DataComponents.CUSTOM_DATA" in src, item   # merge-запись (фикс таба)
    assert "stack.set(DataComponents.CUSTOM_DATA" not in src, item  # грубый set запрещён
    assert "SoundEvents.BUNDLE_REMOVE_ONE" in src, item             # звук извлечения
    assert "cursor.set(" in src, item                               # извлечение в курсор (SlotAccess)
    assert "action != ClickAction.PRIMARY" in src or "action == ClickAction.PRIMARY" in src, item  # ЛКМ/ПКМ развилка
    # отступы лора: пустая строка сразу после строки креатив-таба и перед экранированием
    body = src[src.index("appendHoverText"):]
    assert "Component.empty()" in body and body.count("Component.empty()") >= 2, item

# ── Пины 0.3.86: ГОСТ лора (трубы/хазмат) + строка таба для носителей ──
hax = (ROOT / "src/main/java/com/gonzotech/radiation/HazmatArmorItem.java").read_text()
assert "extends ArmorItem" in hax and "tooltip.gonzotech.hazmat.set" in hax
assert "HazmatTooltips.java" not in str(list((ROOT / "src/main/java/com/gonzotech/radiation/client").glob("*.java")))
moditems = (ROOT / "src/main/java/com/gonzotech/core/registry/ModItems.java").read_text()
assert moditems.count("new com.gonzotech.radiation.HazmatArmorItem(") == 4
assert "new net.minecraft.world.item.ArmorItem(com.gonzotech.radiation.Hazmat" not in moditems
ut = (ROOT / "src/main/java/com/gonzotech/core/client/UniversalTooltip.java").read_text()
assert "HazmatTooltips" not in ut   # лор хазмата теперь в предмете (до «когда надето»)
pipe = (ROOT / "src/main/java/com/gonzotech/core/client/PipeLossTooltip.java").read_text()
assert "add(Component.empty())" not in pipe   # лор труб — прямо под именем
layout = (ROOT / "src/main/java/com/gonzotech/core/client/TooltipLayout.java").read_text()
assert "instanceof com.gonzotech.core.item.CarrierItem" in layout  # фикс таба и для носителей

print("Tongs & ladle wiring passed (-40% rad / -80% tox, 64x1 + 1000 mB, 1.21.4 signatures, deep scan)")

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
        // Носитель в носитель не кладётся.
        check(!com.gonzotech.core.item.TongsLogic.canPick("tongs"), "tongs in tongs");
        check(!com.gonzotech.core.item.TongsLogic.canPick("ladle"), "ladle in tongs");
        // Вместимость 64 одного вида.
        check(com.gonzotech.core.item.TongsLogic.roomFor("", 0, "uranium_ingot", 64) == 64, "empty 64");
        check(com.gonzotech.core.item.TongsLogic.roomFor("uranium_ingot", 53, "uranium_ingot", 40) == 11, "top-up");
        check(com.gonzotech.core.item.TongsLogic.roomFor("uranium_ingot", 64, "uranium_ingot", 1) == 0, "full");
        check(com.gonzotech.core.item.TongsLogic.roomFor("uranium_ingot", 10, "iron_ingot", 5) == 0, "mixed");
        // Ковш: ТОЛЬКО ртуть/цезий предметами.
        check(com.gonzotech.core.item.LadleLogic.canPickItem("mercury_ingot"), "ladle mercury");
        check(com.gonzotech.core.item.LadleLogic.canPickItem("cesium_nugget"), "ladle cesium");
        check(!com.gonzotech.core.item.LadleLogic.canPickItem("uranium_ingot"), "ladle not uranium");
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
    assert data["tooltip.gonzotech.carrier.contains"] == "Содержит: %s × %s" or "Contains" in data["tooltip.gonzotech.carrier.contains"]
for name in ("tongs", "ladle"):
    assert (ROOT / f"src/main/resources/assets/gonzotech/items/{name}.json").is_file()
    assert (ROOT / f"src/main/resources/assets/gonzotech/models/item/{name}.json").is_file()
    assert (ROOT / f"src/main/resources/assets/gonzotech/textures/item/{name}.png").is_file()

print("Tongs & ladle wiring passed (-40% rad / -80% tox, 64x1 + 1000 mB)")

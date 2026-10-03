#!/usr/bin/env python3
"""Пины раунда 10 (0.3.110): экранированная семья эпохи 3 + свинцовые поршни.

Экранирующее свойство: 89 % на семье (фактор 0.88 на потоки и потери узла),
81 % на свинцовых поршнях (фактор 0.19). Семья third_* повторяет поведение
тира II; клампы «3» сшиваются только внутри «3».
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"
DATA = RES / "data/gonzotech"
ASSETS = RES / "assets/gonzotech"

ok = 0

def pin(cond, label):
    global ok
    assert cond, f"PIN FAIL: {label}"
    ok += 1

# ── 1. Маркер тира и фактор 0.88 ──
tp = (SRC / "machines/network/ThirdTierPipe.java").read_text()
pin("extends SecondTierPipe" in tp, "ThirdTierPipe extends SecondTierPipe")
pin("STAT_FACTOR = 0.88" in tp, "STAT_FACTOR 0.88")

# ── 2. Потери: ветка perCell ×0.88 ──
rt = (SRC / "machines/network/PipeRouting.java").read_text()
pin("instanceof ThirdTierPipe" in rt, "perCell: ветка ThirdTierPipe")
pin("Math.round(PipeLoss.perCell(true, heatType) * ThirdTierPipe.STAT_FACTOR)" in rt, "perCell ×0.88")

# ── 3. Клампы: род «3» отдельно, потери узла ×0.88 ──
idx = (SRC / "machines/network/NodeClumpIndex.java").read_text()
pin('b instanceof ThirdTierPipe ? "3" : b instanceof SecondTierPipe ? "2" : "1"' in idx, "род U3")
pin('"N" + tier + ":" + node.pipeType().name()' in idx, "род N3:")
pin("Math.round(per * ThirdTierPipe.STAT_FACTOR)" in idx, "lossMilliFor ×0.88")

# ── 4. Рад: 12×0.11 + 2×0.19 в обоих реестрах ──
rad = (SRC / "radiation/RadMaterials.java").read_text()
family = [
    "third_wire", "third_heat_pipe", "third_universal_fluid_pipe", "third_item_pipe",
    "third_wire_node", "third_heat_node",
    "third_universal_fluid_node", "third_item_node", "third_universal_node",
    "third_lead_piston", "third_sticky_lead_piston",
]
# 0.3.114: универсальная труба удалена по автору («моя оговорка — удалить»);
# связка тир-3 — внутренний блок без предмета
assert (ROOT / "src/main/resources/data/gonzotech/recipe/third_universal_pipe.json").is_file() is False
assert "THIRD_UNIVERSAL_PIPE_ITEM" not in (ROOT / "src/main/java/com/gonzotech/core/registry/ModCreativeTabs.java").read_text()
for ident in family:
    factor = "0.19" if "piston" in ident else "0.11"
    pin(rad.count(f'Map.entry("{ident}", {factor})') >= 2, f"{ident}: {factor} в ITEM_EXACT и BLOCK_EXACT")

# ── 5. Крафты: 10 бесформенных (хост + свинец + Zr-пластина + резина), 2 форменных ──
for ident in family:
    r = (DATA / f"recipe/{ident}.json").read_text()
    if ident in ("third_lead_piston", "third_sticky_lead_piston"):
        pin('"minecraft:crafting_shaped"' in r, f"{ident}: shaped")
        pin('"key"' in r, f"{ident}: сетка автора")
    else:
        pin('"minecraft:crafting_shapeless"' in r, f"{ident}: shapeless")
        pin('"gonzotech:zirconium_plate"' in r and '"gonzotech:rubber"' in r, f"{ident}: Zr + резина")
        host = ident.replace("third_", "second_")
        pin(f'"gonzotech:{host}"' in r, f"{ident}: хост {host}")
        lead = "lead_block" if ident.endswith("node") else "lead_ingot"
        pin(f'"gonzotech:{lead}"' in r, f"{ident}: {lead}")
    pin(f'"gonzotech:{ident}"' in r.split('"result"')[1], f"{ident}: результат")

# форменные сетки автора — дословно
piston_r = (DATA / "recipe/third_lead_piston.json").read_text()
pin('"LBL"' in piston_r and '"MPM"' in piston_r and '"CRC"' in piston_r, "поршень: сетка 3×3 автора")
pin('"minecraft:piston"' in piston_r, "поршень: ванильный катализатор")
sticky_r = (DATA / "recipe/third_sticky_lead_piston.json").read_text()
# сетка автора RRR/_A_/___: пустой нижний ряд нормализован пробелами
pin('"RRR"' in sticky_r and '" A "' in sticky_r, "липкий: сетка автора")
pin('"gonzotech:third_lead_piston"' in sticky_r, "липкий: хост — свинцовый поршень")

# ── 6. Поршни: подклассы PistonBaseBlock, материал ──
pb = (SRC / "machines/block/ThirdPistonBlock.java").read_text()
pin("extends PistonBaseBlock" in pb, "ThirdPistonBlock наследник")
pin("super(false, properties)" in pb, "обычный: sticky=false")
sb = (SRC / "machines/block/ThirdStickyPistonBlock.java").read_text()
pin("super(true, properties)" in sb, "липкий: sticky=true")
mm_t = (SRC / "machines/registry/ModMachines.java").read_text()  # материал — в props-фабрике ModMachines
pin(mm_t.count("material(net.minecraft.world.level.block.SoundType.METAL, 1.5F, 6.0F)") == 2,
    "поршни: material(METAL,1.5,6.0) ×2")

# 0.3.112: ванильный codec() поршня объявлен ТОЧНЫМ MapCodec<PistonBaseBlock> —
# возврат «? extends» не компилируется у автора (лов сборки 0.3.110)
stub_pb = Path("audit/stubs/net/minecraft/world/level/block/piston/PistonBaseBlock.java").read_text()
pin("public MapCodec<PistonBaseBlock> codec()" in stub_pb, "стаб: точный тип codec()")
for f, nm in ((SRC / "machines/block/ThirdPistonBlock.java", "обычный"),
              (SRC / "machines/block/ThirdStickyPistonBlock.java", "липкий")):
    pin("public MapCodec<PistonBaseBlock> codec()" in f.read_text(), f"codec() точный тип: {nm}")

# ── 7. Узел 0.88 (0.91 тир-II не тронут) ──
un = (SRC / "machines/network/ThirdUniversalNodeBlock.java").read_text()
pin("0.88" in un, "уни-узел ×0.88")
s2 = (SRC / "machines/network/SecondUniversalNodeBlock.java").read_text()
pin("0.91" in s2, "тир-II 0.91 не тронут")

# ── 7b. Связка тир-3 (0.3.114): пучки только внутри одного тира ──
comp = (SRC / "machines/network/ThirdCompositePipeBlock.java").read_text()
pin("extends CompositePipeBlock implements ThirdTierPipe" in comp, "связка тир-3: класс")
pin("public double throughputFactor" in comp and "STAT_FACTOR" in comp, "связка тир-3: ×0.88")
mm_t = (SRC / "machines/registry/ModMachines.java").read_text()
pin('"third_composite_pipe"' in mm_t, "связка тир-3: зарегистрирована")
gt = (SRC / "GonzoTechMod.java").read_text()
pin("setThird(" in gt, "связка тир-3: commonSetup")
acc = (SRC / "machines/network/ModCompositeAccess.java").read_text()
pin("block instanceof ThirdTierPipe) return 3;" in acc, "лестница тиров: 3 раньше 2")
pin("case 3 -> thirdComposite;" in acc, "getFor: тир-3 → своя связка")
pin("return tier > 0 && tierOf(existing) == tier;" in acc, "sameTier: только один тир")
cp = (SRC / "machines/network/CompositePipeBlock.java").read_text()
pin("ModCompositeAccess.sameTier(this, stack)" in cp, "useItemOn: sameTier вместо булева тира")
pin("static boolean isSecondTierPipeItem" not in cp, "мёртвого булева хелпера нет")
import json as _json
cl = _json.loads((ROOT / "src/main/resources/data/gonzotech/loot_table/blocks/third_composite_pipe.json").read_text())
drops = {e["name"] for pool in cl["pools"] for e in pool["entries"]}
pin("gonzotech:third_universal_fluid_pipe" in drops, "связка тир-3: угол жидкостей")
pin(not any("third_water_pipe" in d or "third_steam_pipe" in d for d in drops), "связка тир-3: без несуществующих труб")

# ── 7c. Поршневой миксин: голова выживает над модифицированным основанием ──
mix = (SRC / "mixin/PistonHeadBlockMixin.java").read_text()
pin('@Inject(method = "isFittingBase"' in mix, "миксин: перехват isFittingBase")
pin("PistonBaseBlock.class.isAssignableFrom" in mix, "миксин: модифицированные основания")
mixcfg = (ROOT / "src/main/resources/gonzotech.mixins.json").read_text()
pin('"PistonHeadBlockMixin"' in mixcfg, "миксин: в конфиге")
# ротации blockstate поршней — ТОЧНО ванильные (лов сборки: «смотрит вверх»)
bs = _json.loads((ASSETS / "blockstates/third_lead_piston.json").read_text())["variants"]
pin(bs["extended=false,facing=east"].get("y") == 90 and "x" not in bs["extended=false,facing=east"], "ротация east y=90")
pin(bs["extended=false,facing=north"] == {"model": "gonzotech:block/third_lead_piston"}, "ротация north без поворота")
pin(bs["extended=false,facing=south"].get("y") == 180, "ротация south y=180")
pin(bs["extended=false,facing=up"].get("x") == 270, "ротация up x=270")
pin(bs["extended=false,facing=west"].get("y") == 270, "ротация west y=270")
bs2 = _json.loads((ASSETS / "blockstates/third_sticky_lead_piston.json").read_text())["variants"]
pin(bs2["extended=true,facing=down"] == {"model": "gonzotech:block/third_sticky_lead_piston_extended", "x": 90}, "липкий extended down x=90")
# фронт: непродвинутый = платформа-пластина, продвинутый = открытая морда (inside)
m1 = _json.loads((ASSETS / "models/block/third_lead_piston.json").read_text())
pin(m1["textures"]["platform"].endswith("piston_top"), "поршень: платформа = пластина")
m2 = _json.loads((ASSETS / "models/block/third_sticky_lead_piston.json").read_text())
pin(m2["textures"]["platform"].endswith("piston_top_sticky"), "липкий: платформа = липкая пластина")
m3 = _json.loads((ASSETS / "models/block/third_lead_piston_extended.json").read_text())
pin(m3["textures"]["inside"].endswith("piston_inner"), "extended: морда = inside")

# ── 8. Регистрация: блоки + предметы + креатив-таб ──
mm = (SRC / "machines/registry/ModMachines.java").read_text()
for ident in family:
    pin(f'"{ident}"' in mm, f"ModMachines: {ident}")
tabs = (SRC / "core/registry/ModCreativeTabs.java").read_text()
i_piston = tabs.find("THIRD_LEAD_PISTON_ITEM")
i_chem = tabs.find("THIRD_CHEMICAL_PLANT_ITEM")
pin(0 < i_chem < i_piston, "таб: семья после химзавода эпохи 3")

# ── 9. Замыкание контура: 12 блоков в tag, состояние-independent ──
tag = (DATA / "tags/block/contour_seal.json").read_text()
for ident in family:
    pin(f'gonzotech:{ident}' in tag, f"contour_seal: {ident}")

# ── 10. Локализация ──
for lang in ("ru_ru", "en_us"):
    L = (ASSETS.parent.parent / f"assets/gonzotech/lang/{lang}.json").read_text()
    for ident in family:
        pin(f'"block.gonzotech.{ident}"' in L, f"lang/{lang}: {ident}")

# ── 11. Ассеты: blockstate+model+items+loot на всю семью ──
for ident in family:
    pin((ASSETS / f"blockstates/{ident}.json").is_file(), f"blockstate {ident}")
    pin((ASSETS / f"models/block/{ident}.json").is_file(), f"model {ident}")
    pin((ASSETS / f"items/{ident}.json").is_file(), f"items {ident}")
    pin((DATA / f"loot_table/blocks/{ident}.json").is_file(), f"loot {ident}")
    if "piston" in ident:
        t = (ASSETS / "textures/block/third/piston_top_sticky.png").is_file() if "sticky" in ident \
            else (ASSETS / "textures/block/third/piston_top.png").is_file()
    else:
        t = (ASSETS / f"textures/block/third/{ident}.png").is_file()
    pin(t, f"текстура {ident}")


# ── 0.3.115: сжатое основание + своя головка (репорт автора: «блок остаётся полным»,
# «головка — ванильная текстура», головка разрушалась — миксин 0.3.114) ──
ext = _json.loads((ASSETS / "models/block/third_lead_piston_extended.json").read_text())
pin(ext["elements"][0]["from"] == [0, 0, 4] and ext["elements"][0]["to"] == [16, 16, 16],
    "extended-модель основания: коробка 16×16×12 (глубина 12 по оси Z)")
pin(ext["elements"][0]["faces"]["north"]["texture"] == "#inside", "extended: открытая морда = inside")
full = _json.loads((ASSETS / "models/block/third_lead_piston.json").read_text())
pin(full["elements"][0]["from"] == [0, 0, 0], "непродвинутая модель = полный куб")
hm = _json.loads((ASSETS / "models/block/third_lead_piston_head.json").read_text())
pin(hm["elements"][0]["to"] == [16, 16, 4] and hm["elements"][1]["to"] == [10, 10, 20],
    "головка: плита 4px + шток (long)")
hs = _json.loads((ASSETS / "models/block/third_lead_piston_head_short.json").read_text())
pin(hs["elements"][1]["to"] == [10, 10, 16], "головка short: шток без вылета")
hbs = _json.loads((ASSETS / "blockstates/third_lead_piston_head.json").read_text())["variants"]
pin(len(hbs) == 24, "головка: 6 facing × 2 short × 2 type")
pin(hbs["facing=up,short=false,type=sticky"]["x"] == 270, "головка: ротации ванильные")
head_src = (SRC / "machines/block/ThirdPistonHeadBlock.java").read_text()
pin("extends PistonHeadBlock" in head_src, "головка: подкласс")
pin("builder.add(FACING, TYPE, SHORT)" in head_src, "головка: свойства ванили")
mm_t = (SRC / "machines/registry/ModMachines.java").read_text()
pin('"third_lead_piston_head"' in mm_t and "PUSHREACTION" not in mm_t, "головка: зарегистрирована")
pin(".pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)" in mm_t, "головка: DESTROY (не толкается)")
bm = (SRC / "mixin/PistonBaseBlockMixin.java").read_text()
pin('@Redirect(method = "moveBlocks"' in bm, "миксин: редирект создания головки в moveBlocks")
pin("Lnet/minecraft/world/level/block/Blocks;PISTON_HEAD" in bm, "миксин: поле PISTON_HEAD в target")
pin("ordinal = 1" in bm, "миксин: редирект только создания (ordinal 1)")
# GETSTATIC Blocks.PISTON_HEAD has no operands; the redirect must not take an owner arg.
pin("private Block gonzotech$customHead()" in bm, "миксин: GETSTATIC handler без аргументов")
pin("gonzotech$customHead(PistonBaseBlock" not in bm, "миксин: нет лишнего receiver-параметра")
pin("gonzotech$anyModdedHead" in bm and "instanceof net.minecraft.world.level.block.piston.PistonHeadBlock" in bm,
    "миксин: своя головка = головка и в проверке is()")
mixcfg = (ROOT / "src/main/resources/gonzotech.mixins.json").read_text()
pin('"PistonBaseBlockMixin"' in mixcfg, "миксин: в конфиге")
print(f"OK: {ok} пинов раунда 10")

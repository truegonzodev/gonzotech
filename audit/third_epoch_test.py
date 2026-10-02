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
    "third_universal_pipe", "third_wire_node", "third_heat_node",
    "third_universal_fluid_node", "third_item_node", "third_universal_node",
    "third_lead_piston", "third_sticky_lead_piston",
]
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
        host = "second_universal_node" if ident == "third_universal_pipe" else ident.replace("third_", "second_")
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

# ── 7. Универсальная труба: переносит все среды; узел 0.88 (0.91 тир-II не тронут) ──
up = (SRC / "machines/network/ThirdUniversalPipeBlock.java").read_text()
pin("return true" in up, "уни-труба carries() = true")
un = (SRC / "machines/network/ThirdUniversalNodeBlock.java").read_text()
pin("0.88" in un, "уни-узел ×0.88")
s2 = (SRC / "machines/network/SecondUniversalNodeBlock.java").read_text()
pin("0.91" in s2, "тир-II 0.91 не тронут")

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

print(f"OK: {ok} пинов раунда 10")

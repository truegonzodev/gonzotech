#!/usr/bin/env python3
"""Пины раунда 11 (0.3.111): свинцовый ящик + 14-связный контур.

Ящик = копия ванильной бочки: 9 слотов, без двойных сцепок, третий эпоха,
фулл-гейт «Открытие 3». Сам НЕ замыкает контур и без экранирующего свойства;
содержимое действует на чанк (заражение + визуальные источники) на 80% слабее.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"
DATA = RES / "data/gonzotech"
ASSETS = RES / "assets/gonzotech"
ID = "third_lead_chest"

ok = 0

def pin(cond, label):
    global ok
    assert cond, f"PIN FAIL: {label}"
    ok += 1

# ── 1. Блок: копия бочки, полная ориентация, контур/экран НЕ тронуты ──
b = (SRC / "machines/block/ThirdLeadChestBlock.java").read_text()
pin("extends Block implements EntityBlock" in b, "самостоятельный блок-контейнер")
pin("BlockStateProperties.FACING" in b, "полная ориентация (вверх/вниз), как бочка")
pin("BlockStateProperties.OPEN" in b, "свойство открытой крышки")
pin("CONTENT_CHUNK_FACTOR = 0.2" in b, "содержимое −80%")
pin("getRedstoneSignalFromBlockEntity" in b, "компаратор как у контейнера")
pin("Containers.dropContents" in b, "высыпание содержимого при сломе")
pin("tooltip.gonzotech.lead_chest" in b, "лор по ГОСТ — в классе, не event")

rad = (SRC / "radiation/RadMaterials.java").read_text()
tag = (DATA / "tags/block/contour_seal.json").read_text()
pin(f'"{ID}"' not in rad, "нет экранирующего свойства (не в RadMaterials)")
pin(f'"{ID}"' not in tag, "не замыкает контур (не в contour_seal)")

# ── 2. BE: ровно 9 слотов, автоматика бочки, крышка со звуком ──
be = (SRC / "machines/block/entity/ThirdLeadChestBlockEntity.java").read_text()
pin("SLOT_COUNT = 9" in be, "9 слотов, не 27")
pin("implements WorldlyContainer, MenuProvider" in be, "WorldlyContainer + MenuProvider")
pin("startOpen" in be and "stopOpen" in be and "BARREL_OPEN" in be, "крышка/звуки как у бочки")
pin("ContainerHelper.saveAllItems" in be and "ContainerHelper.loadAllItems" in be, "NBT содержимого")
pin("new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}" in be, "автоматизация: все 9 слотов в обе стороны")

# ── 3. Меню/экран/регистрации ──
m = (SRC / "machines/menu/ThirdLeadChestMenu.java").read_text()
pin("CHEST_SLOTS = 9" in m, "меню: один ряд")
s = (SRC / "machines/client/LeadChestScreen.java").read_text()
pin("lead_chest_gui_bg.png" in s and "lead_chest_gui.png" in s, "свои GUI-текстуры")
mc = (SRC / "machines/client/MachineClient.java").read_text()
pin("ModMenus.THIRD_LEAD_CHEST.get(), LeadChestScreen::new" in mc, "экран зарегистрирован (root «нет GUI» закрыт)")
mm = (SRC / "machines/registry/ModMachines.java").read_text()
pin(f'BLOCKS.registerBlock("{ID}"' in mm, "блок зарегистрирован")
pin(f'ITEMS.registerSimpleBlockItem("{ID}"' in mm, "BlockItem зарегистрирован")
mbe = (SRC / "machines/registry/ModBlockEntities.java").read_text()
pin('BLOCK_ENTITIES.register("third_lead_chest"' in mbe, "BE зарегистрирован")
men = (SRC / "machines/registry/ModMenus.java").read_text()
pin('MENUS.register("third_lead_chest"' in men, "MenuType зарегистрирован")
tabs = (SRC / "core/registry/ModCreativeTabs.java").read_text()
i_chest = tabs.find("THIRD_LEAD_CHEST_ITEM")
i_sticky = tabs.find("THIRD_STICKY_LEAD_PISTON_ITEM")
pin(0 < i_sticky < i_chest, "таб: после поршней, третья эпоха")

# ── 4. Крафт: фулл-гейт Открытие 3, кольцо из 8 слитков (сетка не была задана — флаг) ──
gate = (SRC / "machines/crafting/TierThreeCrafting.java").read_text()
pin(f'"gonzotech:{ID}"' in gate, "фулл-гейт Открытие 3")
r = (DATA / f"recipe/{ID}.json").read_text()
pin('"minecraft:crafting_shaped"' in r, "форменный")
pin('"LLL"' in r and '"L L"' in r, "кольцо бочки 3×3")
pin('"gonzotech:lead_ingot"' in r, "свинцовые слитки")
pin(f'"gonzotech:{ID}"' in r.split('"result"')[1], "результат")

# ── 5. Рад-хуки: заражение чанка и визуальные источники ×0.2 ──
sys_ = (SRC / "radiation/RadiationSystem.java").read_text()
pin("ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR" in sys_, "скан контейнеров: ×0.2")
chunk = (SRC / "radiation/ChunkRadiationData.java").read_text()
i_vis = chunk.find("ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR")
i_srcs = chunk.find("visualSources")
pin(i_srcs != -1 and i_vis > i_srcs, "визуальные источники: ×0.2 (меньше партиклов)")

# ── 6. Ассеты ──
pin((ASSETS / f"blockstates/{ID}.json").is_file(), "blockstate")
for model in (f"models/block/{ID}.json", f"models/block/{ID}_open.json"):
    pin((ASSETS / model).is_file(), model)
pin((ASSETS / f"items/{ID}.json").is_file(), "items")
pin((DATA / f"loot_table/blocks/{ID}.json").is_file(), "loot")
for tex in ("lead_chest_side", "lead_chest_top", "lead_chest_bottom", "lead_chest_open"):
    pin((ASSETS / f"textures/block/third/{tex}.png").is_file(), f"текстура {tex}")
pin((ASSETS / "textures/gui/lead_chest_gui_bg.png").is_file(), "GUI bg 512×512")
pin((ASSETS / "textures/gui/lead_chest_gui.png").is_file(), "GUI fg")
for lang in ("ru_ru", "en_us"):
    L = (ASSETS / f"lang/{lang}.json").read_text()
    pin(f'"block.gonzotech.{ID}"' in L, f"lang/{lang}: имя")
    pin('"tooltip.gonzotech.lead_chest"' in L, f"lang/{lang}: лор")
pick = (RES / "data/minecraft/tags/block/mineable/pickaxe.json").read_text()
pin(f'"gonzotech:{ID}"' in pick, "mineable/pickaxe")
# семья раунда 10 тоже возвращена в кирку
for ident in ("third_wire", "third_lead_piston", "third_universal_node"):
    pin(f'"gonzotech:{ident}"' in pick, f"pickaxe: {ident}")

print(f"OK: {ok} пинов раунда 11")

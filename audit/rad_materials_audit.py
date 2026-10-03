#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Аудит экранирования (раунд 14, автор 03.10.2026).

Принцип автора: «уран хоть блок хоть слиток хоть что — имеет экранирование»,
обобщённый: все МАТЕРИАЛЬНЫЕ формы одного метал-семейства (слиток/кусочек/
пыль/блок/сырая руда) согласованы по фактору прохождения. Пластины/проволока —
конструкционные формы со своими точечными ставками, из правила исключены.

Зеркало логики RadMaterials.itemFactor/blockFactor: java-карты парсятся
из исходника, поэтому гейт не может протухнуть мимо правки. Для raw и изотопов
30% умножается на ПРОЦЕНТ ЭКРАНИРОВАНИЯ слитка: transmission = 1 -
(1 - ingot_transmission) × 0.3. Это не то же самое, что ingot_transmission × 0.3.

Запуск: python3 audit/rad_materials_audit.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech/radiation/RadMaterials.java"
LANG = ROOT / "src/main/resources/assets/gonzotech/lang/ru_ru.json"

# ── 1. Парсинг java ──────────────────────────────────────────────────────────
src = SRC.read_text(encoding="utf-8")


def parse_map(name: str) -> dict:
    m = re.search(name + r"\s*=\s*Map\.ofEntries\((.*?)\n    \);", src, re.S)
    assert m, f"карта {name} не найдена"
    out = {}
    for key, val in re.findall(r'Map\.entry\("([^"]+)",\s*([0-9.]+)\)', m.group(1)):
        out[key] = float(val)
    return out


FACTORS = parse_map("FACTORS")
ITEM_EXACT = parse_map("ITEM_EXACT")
BLOCK_EXACT = parse_map("BLOCK_EXACT")

m = re.search(r"GENERIC_METALS\s*=\s*List\.of\((.*?)\);", src, re.S)
assert m, "GENERIC_METALS не найден"
GENERIC = set(re.findall(r'"([a-z0-9_]+)"', m.group(1)))
PREFIXES = sorted(FACTORS, key=len, reverse=True)

# ── 2. Зеркало логики ────────────────────────────────────────────────────────


def is_ore(path: str) -> bool:
    return path == "ore" or path.startswith("ore_") or path.endswith("_ore") or "_ore_" in path


def is_equipment(path: str) -> bool:
    return path.endswith(("_pickaxe", "_axe", "_shovel", "_hoe", "_sword",
                          "_helmet", "_chestplate", "_leggings", "_boots",
                          "_horse_armor", "_armor", "_tools", "_bars"))


def metal_word(path: str) -> bool:
    return any(path == w or path.startswith(w + "_") for w in GENERIC)


def factor(path: str, item: bool) -> float:
    exact = (ITEM_EXACT if item else BLOCK_EXACT).get(path)
    if exact is not None:
        return exact
    for p in PREFIXES:
        if path == p or path.startswith(p + "_"):
            return FACTORS[p]
    if item:
        if path.startswith("raw_"):
            # Сырьё получает 30% shield-процента реального sibling-ingot,
            # а карта хранит долю прохождения. Не умножать transmission на 0.3.
            base = path[4:]
            ingot_path = base + "_ingot"
            if ingot_path not in item_ids:
                return 1.0
            ingot = factor(ingot_path, True)
            return 1.0 - (1.0 - ingot) * 0.3
        if path.endswith("_ingot") or path.endswith("_nugget"):
            return 0.72
        if path.endswith("_dust") and metal_word(path):
            return 0.72
        if path.endswith("_block") and metal_word(path):
            return 0.72
        return 1.0
    return 0.72 if path.endswith(("_block", "_ingot_form")) and metal_word(path) else 1.0


# ── 3. Все id из lang ────────────────────────────────────────────────────────
import json  # noqa: E402

lang = json.loads(LANG.read_text(encoding="utf-8"))
items = sorted(k[len("item.gonzotech."):] for k in lang if k.startswith("item.gonzotech."))
item_ids = set(items)
blocks = sorted(k[len("block.gonzotech."):] for k in lang if k.startswith("block.gonzotech."))

# ── 4. Пины раунда 14 ────────────────────────────────────────────────────────
failures = []


def pin(cond: bool, msg: str) -> None:
    if not cond:
        failures.append(msg)


for w in ("zirconium", "calcium", "cesium", "tellurium", "telluride", "palladium",
          "neodymium", "rhenium", "alnico", "cantor", "nitinol", "vitreloy",
          "stellite", "cast_iron", "corten_steel", "stainless_steel",
          "ferromagnetic", "semiconductor"):
    pin(w in GENERIC, f"GENERIC_METALS без слова {w!r}")

# Пример автора: циркониевая пластина — блок/пыль согласованы; raw получает
# 30% от shielding sibling-ingot, а не 30% его transmission.
pin(factor("zirconium_plate", True) == factor("zirconium_block", True) ==
    factor("zirconium_ingot", True) == factor("zirconium_dust", True) == 0.72,
    "семейство zirconium (плотные формы) не согласовано")
pin(abs(factor("raw_zirconium", True) - 0.916) < 1e-9,
    "raw_zirconium: ожидается .916 прохода / 8.4% защиты")
# Проверочные числа автора: raw tungsten = 29.91% защиты, raw lead = 29.4%.
for raw_example in ("raw_tungsten", "raw_calcite"):
    pin(raw_example in item_ids, f"нет raw-формы в lang: {raw_example}")
pin(abs(factor("raw_tungsten", True) - 0.7009) < 1e-9,
    "raw_tungsten: ожидается .7009 прохода / 29.91% защиты")
pin(abs(factor("raw_lead", True) - 0.706) < 1e-9,
    "raw_lead: ожидается .706 прохода / 29.4% защиты")
pin(factor("raw_calcite", True) == 1.0, "raw_calcite без экранирующего слитка должен остаться 1.0")
raw_ids = sorted(path for path in item_ids if path.startswith("raw_"))
for raw_path in raw_ids:
    base = raw_path[4:]
    sibling = base + "_ingot"
    expected = 1.0 - (1.0 - factor(sibling, True)) * 0.3 if sibling in item_ids else 1.0
    pin(abs(factor(raw_path, True) - expected) < 1e-9,
        f"{raw_path}: ожидался 30% shield sibling-ingot; если его нет — 1.0")
    if sibling not in item_ids:
        pin(factor(raw_path, True) == 1.0,
            f"{raw_path} без sibling-ingot внезапно получил shielding")
# Текущие изотопы проверяются формулой именно от sibling-ingot, не только
# отдельной числовой фиксацией .916.
for parent, isotopes in {
    "uranium": ("uranium_233", "uranium_235", "uranium_238"),
    "thorium": ("thorium_229",),
}.items():
    ingot_transmission = factor(parent + "_ingot", True)
    expected = 1.0 - (1.0 - ingot_transmission) * 0.3
    for isotope in isotopes:
        pin(abs(factor(isotope, True) - expected) < 1e-9,
            f"{isotope}: transmission не соответствует 30% shield sibling-ingot")

# Уран: плотные формы проходят 0.72 (28% защиты); сырьё и текущие компонентные
# изотопы получают только 30% от этих 28% = 8.4% защиты, проход = .916.
uran_dense = ["uranium_ingot", "uranium_nugget", "uranium_dust", "uranium_block"]
uran_reduced = ["raw_uranium", "uranium_233", "uranium_235", "uranium_238"]
for pth in uran_dense + uran_reduced:
    pin(pth in items or pth in blocks, f"нет уран-формы в lang: {pth}")
    pin(factor(pth, pth in items) < 1.0, f"уран без экранирования: {pth}")
for pth in uran_dense:
    pin(factor(pth, pth in items) == 0.72, f"{pth} != 0.72")
for pth in uran_reduced:
    pin(abs(factor(pth, pth in items) - 0.916) < 1e-9,
        f"{pth} != .916 (8.4% защиты = 30% от 28%)")
# Th-229: та же формула от thorium_ingot (слиток фактора .72).
pin(abs(factor("thorium_229", True) - 0.916) < 1e-9,
    "thorium_229 != .916 (8.4% защиты)")
# сера 12% / йод 18% (автор, раунд 14): всё что связано
pin(FACTORS.get("sulfur") == 0.12, "сера != 0.12")
pin(FACTORS.get("iodine") == 0.18, "йод != 0.18")
pin(factor("sulfur_ingot", True) == 0.12, "sulfur_ingot != 0.12")
pin(factor("iodine_block", True) == 0.18, "iodine_block != 0.18")
pin(abs(factor("raw_sulfur", True) - 0.736) < 1e-9,
    "raw_sulfur != .736 (26.4% защиты = 30% от 88%)")
# головка поршня = ставка поршня
pin(ITEM_EXACT.get("third_lead_piston_head") == 0.19, "ITEM third_lead_piston_head != 0.19")
pin(BLOCK_EXACT.get("third_lead_piston_head") == 0.19, "BLOCK third_lead_piston_head != 0.19")

# ── 5. Семейная согласованность материальных форм ────────────────────────────
MATERIAL_ROLE = re.compile(r"^(?P<base>.+?)_(ingot|nugget|dust|block|raw|raw_ore)$")
MATERIAL_BASE = re.compile(r"^raw_(?P<base>.+)$")

# Разрешённый разнобой (осознанные решения прошлых раундов):
#  - руды: фактор 1.0 по правилу «руда не стройматериал»;
#  - third_*: точечные ставки контура (трубы .11, поршни .19, двери);
#  - готовое оборудование/двери/инструменты;
#  - уран-топлива и следы переработки (yellow_cake, *_fuel, mox_*, snup_*,
#    ut_*, uranium_2*, depleted_*) — вопрос автору, не молчаливый разнобой;
#  - sulfur/iodine: неметаллы со «слитками» по базовому правилу.
WHITELIST_FAMILIES = {
    "third", "third_lead", "lead_chest",
    "redstone", "glowstone", "quartz",
    "barium_concrete", "bore", "boron_concrete", "vr_20", "vr20",
}
SKIP_BASES = re.compile(
    r"(_fuel$|^mox_|^tmox_|^snup_|^ut_|^depleted_|^yellow_cake|_233$|_235$|_238$)")

fams: dict = {}
raws: dict = {}
for path in set(items) | set(blocks):
    if is_ore(path) or is_equipment(path) or path.startswith("third_"):
        continue
    if path.startswith("raw_"):
        base = path[4:]
        if SKIP_BASES.search(base) or base in WHITELIST_FAMILIES:
            continue
        raws.setdefault(base, []).append(factor(path, item=path in items))
        continue
    mm = MATERIAL_ROLE.match(path)
    if not mm:
        continue
    base = mm.group("base")
    if SKIP_BASES.search(base) or base in WHITELIST_FAMILIES:
        continue
    fams.setdefault(base, {})[path] = factor(path, item=path in items)

for base, members in sorted(fams.items()):
    vals = sorted(set(members.values()))
    if len(vals) > 1:
        failures.append(
            f"семейство {base}: разнобой {vals} — {members}")
    if base in raws:
        ingot_path = base + "_ingot"
        ingot = factor(ingot_path, True) if ingot_path in item_ids else None
        expected = 1.0 - (1.0 - ingot) * 0.3 if ingot is not None else 1.0
        for rv in raws[base]:
            if abs(rv - expected) > 1e-9:
                failures.append(
                    f"сырьё {base}: {rv} != 30% защиты sibling-ingot ({expected} transmission from {ingot})")
                break

if failures:
    print("FAIL — экранирование не согласовано:")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print(f"OK: пины раунда 14 + {len(fams)} семей материальных форм согласованы "
      f"({len(items)} items / {len(blocks)} blocks)")

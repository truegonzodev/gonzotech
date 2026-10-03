#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Аудит экранирования (раунд 14, автор 03.10.2026).

Принцип автора: «уран хоть блок хоть слиток хоть что — имеет экранирование»,
обобщённый: все МАТЕРИАЛЬНЫЕ формы одного метал-семейства (слиток/кусочек/
пыль/блок/сырая руда) согласованы по фактору прохождения. Пластины/проволока —
конструкционные формы со своими точечными ставками, из правила исключены.

Зеркало логики RadMaterials.itemFactor/blockFactor: java-карты парсятся
из исходника, поэтому гейт не может протухнуть мимо правки.

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
            base = path[4:]
            for pr in PREFIXES:
                if base == pr or base.startswith(pr + "_"):
                    return FACTORS[pr]
            return 0.72 if metal_word(base) else 1.0
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

# пример автора: циркониевая пластина — блок/пыль больше не отстают
pin(factor("zirconium_plate", True) == factor("zirconium_block", True) ==
    factor("zirconium_ingot", True) == factor("zirconium_dust", True) ==
    factor("raw_zirconium", True),
    "семейство zirconium не согласовано")
# уран: ВСЁ урансодержащее-материал — одинаковый экран, и он есть (<1.0)
uranium_forms = ["uranium_ingot", "uranium_nugget", "uranium_dust", "uranium_block",
                 "raw_uranium", "uranium_233", "uranium_235", "uranium_238"]
uran_factors = {p: factor(p, p in items) for p in uranium_forms if p in items or p in blocks}
pin(len(uran_factors) == len(uranium_forms), f"нет уран-форм в lang: {uran_factors}")
pin(len(set(uran_factors.values())) == 1, f"уран-формы разошлись: {uran_factors}")
pin(next(iter(uran_factors.values())) < 1.0, "уран без экранирования")
# торий-229 (голый изотоп) — ставка семьи тория
pin(factor("thorium_229", True) == factor("thorium_ingot", True),
    "thorium_229 не согласован с семьёй тория")
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
    "sulfur", "iodine", "redstone", "glowstone", "quartz",
    "barium_concrete", "bore", "boron_concrete", "vr_20", "vr20",
}
SKIP_BASES = re.compile(
    r"(_fuel$|^mox_|^tmox_|^snup_|^ut_|^depleted_|^yellow_cake|_233$|_235$|_238$)")

fams: dict = {}
for path in set(items) | set(blocks):
    if is_ore(path) or is_equipment(path) or path.startswith("third_"):
        continue
    mm = MATERIAL_ROLE.match(path) or MATERIAL_BASE.match(path)
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

if failures:
    print("FAIL — экранирование не согласовано:")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print(f"OK: пины раунда 14 + {len(fams)} семей материальных форм согласованы "
      f"({len(items)} items / {len(blocks)} blocks)")

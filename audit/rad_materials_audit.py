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
            # Автор (раунд 14): сырьё рыхлое — ×0.3 от ставки семьи;
            # неметаллы без экрана (кальцит) остаются 1.0.
            base = path[4:]
            fam = None
            for pr in PREFIXES:
                if base == pr or base.startswith(pr + "_"):
                    fam = FACTORS[pr]
                    break
            if fam is None and metal_word(base):
                fam = 0.72
            return fam * 0.3 if fam is not None and fam < 1.0 else 1.0
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

# пример автора: циркониевая пластина — блок/пыль больше не отстают;
# сырьё = ставка семьи × 0.3 (автор, раунд 14)
pin(factor("zirconium_plate", True) == factor("zirconium_block", True) ==
    factor("zirconium_ingot", True) == factor("zirconium_dust", True) == 0.72,
    "семейство zirconium (плотные формы) не согласовано")
pin(factor("raw_zirconium", True) == 0.72 * 0.3, "raw_zirconium != 0.72×0.3")
# уран: ВСЁ урансодержащее экранирует (<1.0); плотные формы 0.72, сырьё/изотопы 0.216
uran_dense = ["uranium_ingot", "uranium_nugget", "uranium_dust", "uranium_block"]
uran_raw = ["raw_uranium", "uranium_233", "uranium_235", "uranium_238"]
for pth in uran_dense + uran_raw:
    pin(pth in items or pth in blocks, f"нет уран-формы в lang: {pth}")
    pin(factor(pth, pth in items) < 1.0, f"уран без экранирования: {pth}")
for pth in uran_dense:
    pin(factor(pth, pth in items) == 0.72, f"{pth} != 0.72")
for pth in uran_raw:
    pin(factor(pth, pth in items) == 0.216, f"{pth} != 0.216 (0.72×0.3)")
# торий-229 (голый изотоп) — ставка семьи тория ×0.3
pin(factor("thorium_229", True) == factor("thorium_ingot", True) * 0.3,
    "thorium_229 != семья тория ×0.3")
# сера 12% / йод 18% (автор, раунд 14): всё что связано
pin(FACTORS.get("sulfur") == 0.12, "сера != 0.12")
pin(FACTORS.get("iodine") == 0.18, "йод != 0.18")
pin(factor("sulfur_ingot", True) == 0.12, "sulfur_ingot != 0.12")
pin(factor("iodine_block", True) == 0.18, "iodine_block != 0.18")
pin(factor("raw_sulfur", True) == 0.036, "raw_sulfur != 0.036 (0.12×0.3)")
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
        dense = vals[0] if len(vals) == 1 else None
        for rv in raws[base]:
            if dense is None or abs(rv - dense * 0.3) > 1e-9:
                failures.append(
                    f"сырьё {base}: {rv} != плотная ставка {dense} × 0.3")
                break

if failures:
    print("FAIL — экранирование не согласовано:")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print(f"OK: пины раунда 14 + {len(fams)} семей материальных форм согласованы "
      f"({len(items)} items / {len(blocks)} blocks)")

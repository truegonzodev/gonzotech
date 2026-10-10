#!/usr/bin/env python3
"""Пин name-hook'а custom_alloy (инструмент агента, 08.10.2026).

Один премдет custom_alloy называется по составу:
«<преобладающая характеристика> <класс энтропии> сплав». Проверяем, что:
* CustomAlloyItem переопределяет getName и собирает переводимый ключ
  item.gonzotech.custom_alloy.named из adjective+entropy;
* все 7 прилагательных и 5 классов энтропии есть в обоих lang-файлах;
* формат имени содержит два %s;
* пороги энтропии (85% / 50% / 14 / 5) на месте.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech/core/item/CustomAlloyItem.java"
LANG = ROOT / "src/main/resources/assets/gonzotech/lang"

code = SRC.read_text(encoding="utf-8")
ADJECTIVES = ["strength", "brittleness", "inertness", "conductivity", "heat", "plasticity", "weight"]
ENTROPIES = ["legated", "low", "medium", "high", "super"]


def check(ok, msg):
    assert ok, msg


check("public Component getName(ItemStack stack)" in code, "getName(ItemStack) is overridden")
check('Component.translatable("item.gonzotech.custom_alloy.named"' in code,
      "getName builds the named translation key")
check('"alloy.gonzotech.adjective." + dominantTrait(properties)' in code
      and '"alloy.gonzotech.entropy." + entropyClass(composition)' in code,
      "name is composed from a dominant-trait adjective and an entropy class")
check("if (maxFraction > 0.85D) return \"legated\";" in code
      and "if (maxFraction > 0.50D) return \"low\";" in code
      and "if (count >= 14) return \"super\";" in code
      and "if (count >= 5) return \"high\";" in code,
      "entropy thresholds: >85% legated, >50% low, 14+ super, 5+ high, else medium")

for lang in ("ru_ru", "en_us"):
    data = json.loads((LANG / f"{lang}.json").read_text(encoding="utf-8"))
    named = data.get("item.gonzotech.custom_alloy.named", "")
    check(named.count("%s") == 2, f"{lang}: named format has two %s placeholders")
    for a in ADJECTIVES:
        check(f"alloy.gonzotech.adjective.{a}" in data, f"{lang}: adjective.{a} present")
    for e in ENTROPIES:
        check(f"alloy.gonzotech.entropy.{e}" in data, f"{lang}: entropy.{e} present")

print("OK: custom_alloy name-hook contract holds")

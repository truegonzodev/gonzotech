#!/usr/bin/env python3
"""Пин инжекта биома desolation в оверворлд (инструмент агента, 12.10.2026).

Фиксирует контракт: desolation появляется в оверворлде через mixin-инжект в
OverworldBiomeBuilder.addBiomes (normal/large-biomes/amplified), БЕЗ оверрайда
data/minecraft/dimension/overworld.json (который ломал суперплоский/большие/
расширенный). Только статические проверки (без JDK).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
JAVA = ROOT / "src/main/java/com/gonzotech"


def check(ok, msg):
    assert ok, msg


mixin_path = JAVA / "mixin/OverworldBiomeBuilderDesolationMixin.java"
check(mixin_path.is_file(), "desolation injection mixin exists")
mixin = mixin_path.read_text(encoding="utf-8")
check("@Mixin(OverworldBiomeBuilder.class)" in mixin, "mixin targets OverworldBiomeBuilder")
check('method = "addBiomes"' in mixin, "mixin injects into addBiomes")
check('@At("TAIL")' in mixin, "mixin appends at TAIL (after vanilla biomes)")
check(mixin.count("DESOLATION)") == 4, "exactly 4 desolation climate points injected")
check("Climate.Parameter.span" in mixin and "new Climate.ParameterPoint" in mixin,
      "uses Climate.Parameter.span + ParameterPoint record")

cfg = json.loads((RES / "gonzotech.mixins.json").read_text(encoding="utf-8"))
check("OverworldBiomeBuilderDesolationMixin" in cfg.get("mixins", []),
      "mixin registered in gonzotech.mixins.json")

# Суперплоский/большие/расширенный: оверворлд НЕ должен быть переопределён датапаком.
check(not (RES / "data/minecraft/dimension/overworld.json").exists(),
      "no dimension/overworld.json override (presets preserved)")

# Сам биом и его климат-точки на месте.
biome = RES / "data/gonzotech/worldgen/biome/desolation.json"
check(biome.is_file(), "desolation biome json exists")

print("OK: desolation biome injection contract holds")

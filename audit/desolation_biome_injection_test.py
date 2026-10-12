#!/usr/bin/env python3
"""Пин инжекта desolation через оверрайд пресета биомов (инструмент агента, 12.10.2026).

desolation возвращается в оверворлд датапак-оверрайдом
data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
(полный список биомов + 4 точки desolation). Генератор НЕ переопределяется,
поэтому суперплоский/большие/расширенный остаются собой.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"


def check(ok, msg):
    assert ok, msg


preset = RES / "data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json"
check(preset.is_file(), "overworld biome preset override exists")
d = json.loads(preset.read_text(encoding="utf-8"))
check("biomes" in d and isinstance(d["biomes"], list), "preset has biomes list")
check(len(d["biomes"]) > 7000, f"preset keeps full vanilla list ({len(d['biomes'])})")
des = [b for b in d["biomes"] if b.get("biome") == "gonzotech:desolation"]
check(len(des) == 4, f"exactly 4 desolation points ({len(des)})")
for b in des:
    p = b["parameters"]
    for k in ("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness"):
        check(k in p, f"desolation point has {k}")

# Генератор не переопределён → суперплоский цел.
check(not (RES / "data/minecraft/dimension/overworld.json").exists(),
      "no dimension/overworld.json override (presets preserved)")

# Биом и его фичи на месте.
check((RES / "data/gonzotech/worldgen/biome/desolation.json").is_file(), "desolation biome json exists")

print("OK: desolation preset-override contract holds")

#!/usr/bin/env python3
"""Пин: «Пустошь» без биома — климат-гейт через noise-router (вариант 4).

Инжект биома в оверворлд невозможен ни одним датапак-путём (подтверждено
крашами/логами): dimension-оверрайд ломает суперплоский, пресет-реестр
принимает только {"preset":...}, mixin в BiomeSource не попадает в биомную
сетку чанка. Поэтому «Пустошь» — климатическая зона + фичи на весь оверворлд.

Контракт:
  1. DesolationClimate: 4 гиперкуба, вычисление из density-функций router
     (temperature/vegetation/continents/erosion/ridges), fail-safe;
  2. DesolationConversionFeature: гейт по климату (НЕ по биому), convertChunk
     принимает RandomState, радиация привязана к конверсии (markDesolation);
  3. DeadTrunkFeature: стволы только в климат-зоне;
  4. ChunkRadiationData: есть markDesolation, НЕТ биом-проверки;
  5. biome_modifier add_desolation_features: is_overworld, vegetal_decoration,
     4 фичи в порядке conversion→trunks→scorched→saplings;
  6. placed-фичи дезоляции БЕЗ minecraft:biome-фильтра;
  7. НЕТ mixin-инжекта биома и НЕТ DesolationBiomeInject;
  8. НЕТ dimension/overworld.json и preset-оверрайда.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main")
RES = os.path.join(SRC, "resources")
JAVA = os.path.join(SRC, "java", "com", "gonzotech")

failures = []


def check(cond, msg):
    if not cond:
        failures.append(msg)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


# 1. климат-гейт
climate = os.path.join(JAVA, "core", "worldgen", "DesolationClimate.java")
check(os.path.isfile(climate), "нет DesolationClimate.java")
if os.path.isfile(climate):
    c = read(climate)
    check(c.count("0.15F, 0.50F") == 4, "ожидалось 4 гиперкуба")
    for acc in ("temperature()", "vegetation()", "continents()", "erosion()", "ridges()"):
        check(acc in c, f"нет обращения к router.{acc}")
    check("SinglePointContext" in c, "нет SinglePointContext")
    check("catch (Throwable" in c, "нет fail-safe в климат-гейте")

# 2. конверсия
conv = os.path.join(JAVA, "core", "worldgen", "DesolationConversionFeature.java")
check(os.path.isfile(conv), "нет DesolationConversionFeature.java")
if os.path.isfile(conv):
    v = read(conv)
    check(".is(DESOLATION)" not in v, "конверсия всё ещё гейтится по биому")
    check("DesolationClimate.isDesolation" in v, "конверсия не гейтится по климату")
    check("RandomState randomState" in v, "convertChunk не принимает RandomState")
    check("markDesolation" in v, "радиация не привязана к конверсии")

# 3. стволы
trunk = os.path.join(JAVA, "core", "worldgen", "DeadTrunkFeature.java")
check(os.path.isfile(trunk), "нет DeadTrunkFeature.java")
if os.path.isfile(trunk):
    t = read(trunk)
    check("DesolationClimate.isDesolation" in t, "стволы не гейтятся по климату")

# 4. радиация
rad = os.path.join(JAVA, "radiation", "ChunkRadiationData.java")
check(os.path.isfile(rad), "нет ChunkRadiationData.java")
if os.path.isfile(rad):
    r = read(rad)
    check("markDesolation" in r, "нет markDesolation в радиации")
    check("DESOLATION" not in r, "радиация всё ещё ссылается на биом DESOLATION")

# 5. biome_modifier
mod = os.path.join(RES, "data", "gonzotech", "neoforge", "biome_modifier",
                   "add_desolation_features.json")
check(os.path.isfile(mod), "нет biome_modifier add_desolation_features.json")
if os.path.isfile(mod):
    m = json.load(open(mod, encoding="utf-8"))
    check(m.get("biomes") == "#minecraft:is_overworld", "modifier не на is_overworld")
    check(m.get("step") == "vegetal_decoration", "modifier не в vegetal_decoration")
    feats = m.get("features", [])
    check(feats[:1] == ["gonzotech:desolation_conversion_placed"],
          "конверсия должна идти первой")
    check(len(feats) == 4, "ожидалось 4 фичи в modifier")

# 6. placed-фичи без biome-фильтра
for f in ("desolation_conversion_placed", "desolation_trunks_placed",
          "desolation_scorched_placed", "desolation_saplings_placed"):
    p = os.path.join(RES, "data", "gonzotech", "worldgen", "placed_feature", f + ".json")
    check(os.path.isfile(p), f"нет placed_feature {f}")
    if os.path.isfile(p):
        d = json.load(open(p, encoding="utf-8"))
        check(all(pl.get("type") != "minecraft:biome" for pl in d["placement"]),
              f"{f} всё ещё имеет minecraft:biome-фильтр")

# 7. mixin-инжект биома убран
check(not os.path.isfile(os.path.join(JAVA, "mixin", "MultiNoiseBiomeSourceDesolationMixin.java")),
      "mixin-инжект биома не удалён")
check(not os.path.isfile(os.path.join(JAVA, "core", "worldgen", "DesolationBiomeInject.java")),
      "DesolationBiomeInject не удалён")
mixcfg = json.load(open(os.path.join(RES, "gonzotech.mixins.json"), encoding="utf-8"))
check("MultiNoiseBiomeSourceDesolationMixin" not in mixcfg.get("mixins", []),
      "mixin всё ещё зарегистрирован")

# 8. нет dimension/preset-оверрайдов
check(not os.path.exists(os.path.join(RES, "data", "minecraft", "dimension", "overworld.json")),
      "dimension/overworld.json снова появился")
check(not os.path.exists(os.path.join(
    RES, "data", "minecraft", "worldgen",
    "multi_noise_biome_source_parameter_list", "overworld.json")),
      "preset-оверрайд снова появился")

if failures:
    print("FAIL: desolation climate-feature contract broken:")
    for m in failures:
        print("  -", m)
    sys.exit(1)
print("OK: desolation climate-feature contract holds")

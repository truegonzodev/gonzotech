#!/usr/bin/env python3
"""Пин: desolation в оверворлде через дописывание климат-точек в пресет.

История (все подходы проверены запуском):
  - dimension/overworld.json        -> ломает суперплоский;
  - preset-реестр multi_noise_...   -> принимает только {"preset":...};
  - mixin в OverworldBiomeBuilder   -> краш bootstrap'а (висячие ссылки);
  - mixin в getNoiseBiome           -> инъекция была, но сетка чанка/цвета
                                       мимо (0.3.188);
  - климат-фичи без биома (0.3.189) -> контент есть, но НЕТ цветов биома
                                       (небо/трава/вода) — вернул автор.

Итог: дописываем 4 климат-точки desolation в Climate.ParameterList пресета
оверворлда в конструкторе MultiNoiseBiomeSourceParameterList (мягкий
holderGetter.get — bootstrap не крашится). Ванильный findValue сам даёт
частоту/границы; биом в сетке чанка -> цвета/F3//locate/конверсия работают.

Контракт:
  1. mixin MultiNoiseParameterListDesolationMixin зарегистрирован; инъекция в
     конструктор (Preset, HolderGetter), мягкий get(DESOLATION), отбор
     оверворлда по plains, fail-safe, 4 бокса;
  2. конверсия и стволы снова гейтятся по биому (цвета и контент согласованы);
  3. радиация baseline снова по биому desolation;
  4. НЕТ климат-фич: DesolationClimate, add_desolation_features;
  5. НЕТ мёртвых подходов: getNoiseBiome-mixin, DesolationBiomeInject,
     dimension-оверрайд, preset-оверрайд;
  6. биом desolation.json с effects (цвета) и фичами на месте.
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


# 1. mixin пресета
mx_path = os.path.join(JAVA, "mixin", "MultiNoiseParameterListDesolationMixin.java")
check(os.path.isfile(mx_path), "нет MultiNoiseParameterListDesolationMixin.java")
mixcfg = json.load(open(os.path.join(RES, "gonzotech.mixins.json"), encoding="utf-8"))
check("MultiNoiseParameterListDesolationMixin" in mixcfg.get("mixins", []),
      "mixin пресета не зарегистрирован")
if os.path.isfile(mx_path):
    mx = read(mx_path)
    check("<init>(Lnet/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList$Preset;"
          "Lnet/minecraft/core/HolderGetter;)V" in mx, "инъекция не в конструктор пресета")
    check("biomes.get(DESOLATION)" in mx, "нет мягкого holderGetter.get(DESOLATION)")
    check("isBound()" in mx, "нет проверки isBound (крах 0.3.190: unbound-плейсхолдер)")
    check("Biomes.PLAINS" in mx, "нет отбора оверворлда по plains")
    check("catch (Throwable" in mx, "нет fail-safe")
    check(mx.count("box(0.15F") == 4, "ожидалось 4 климат-бокса")

# 2. контент по биому
conv = read(os.path.join(JAVA, "core", "worldgen", "DesolationConversionFeature.java"))
check(".is(DESOLATION)" in conv, "конверсия не гейтится по биому")
check("DesolationClimate" not in conv, "конверсия ссылается на удалённый климат-гейт")
trunk = read(os.path.join(JAVA, "core", "worldgen", "DeadTrunkFeature.java"))
check("DesolationClimate" not in trunk, "стволы ссылаются на удалённый климат-гейт")

# 3. радиация по биому
rad = read(os.path.join(JAVA, "radiation", "ChunkRadiationData.java"))
check("DESOLATION" in rad and "getBiome" in rad, "радиация не по биому desolation")

# 4. климат-фич убраны
check(not os.path.isfile(os.path.join(JAVA, "core", "worldgen", "DesolationClimate.java")),
      "DesolationClimate.java не удалён")
check(not os.path.exists(os.path.join(
    RES, "data", "gonzotech", "neoforge", "biome_modifier", "add_desolation_features.json")),
    "biome_modifier климат-фич не удалён")

# 5. мёртвые подходы отсутствуют
check(not os.path.isfile(os.path.join(JAVA, "mixin", "MultiNoiseBiomeSourceDesolationMixin.java")),
      "getNoiseBiome-mixin не удалён")
check(not os.path.isfile(os.path.join(JAVA, "core", "worldgen", "DesolationBiomeInject.java")),
      "DesolationBiomeInject не удалён")
check("MultiNoiseBiomeSourceDesolationMixin" not in mixcfg.get("mixins", []),
      "getNoiseBiome-mixin всё ещё зарегистрирован")
check(not os.path.exists(os.path.join(RES, "data", "minecraft", "dimension", "overworld.json")),
      "dimension/overworld.json снова появился")
check(not os.path.exists(os.path.join(
    RES, "data", "minecraft", "worldgen",
    "multi_noise_biome_source_parameter_list", "overworld.json")),
    "preset-оверрайд снова появился")

# 6. биом с цветами и фичами
biome_p = os.path.join(RES, "data", "gonzotech", "worldgen", "biome", "desolation.json")
check(os.path.isfile(biome_p), "нет desolation.json")
if os.path.isfile(biome_p):
    b = json.load(open(biome_p, encoding="utf-8"))
    eff = b.get("effects", {})
    for k in ("sky_color", "water_color", "grass_color", "foliage_color"):
        check(k in eff, f"в биоме нет цвета {k}")
    feats = json.dumps(b.get("features", []))
    check("desolation_conversion" in feats and "desolation_trunks" in feats,
          "в биоме нет фич конверсии/стволов")

if failures:
    print("FAIL: desolation preset-injection contract broken:")
    for m in failures:
        print("  -", m)
    sys.exit(1)
print("OK: desolation preset-injection contract holds")

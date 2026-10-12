#!/usr/bin/env python3
"""Пин: десоляция в оверворлде через runtime-mixin (fail-safe).

Контракт (подход после двух крашей датапак-путей):
  1. mixin MultiNoiseBiomeSourceDesolationMixin существует и зарегистрирован;
  2. целевой метод — getNoiseBiome(Climate.TargetPoint), инъекция RETURN+cancellable;
  3. класс-держатель DesolationBiomeInject: 4 климат-бокса + захват холдера на
     ServerAboutToStartEvent + fail-safe (null-холдер);
  4. НЕТ dimension/overworld.json (ломал суперплоский);
  5. НЕТ multi_noise_biome_source_parameter_list/overworld.json (не парсится);
  6. биом desolation.json на месте.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main")
RES = os.path.join(SRC, "resources")
JAVA = os.path.join(SRC, "java", "com", "gonzotech")

failures = []


def check(cond, msg):
    if not cond:
        failures.append(msg)


# 1. mixin существует и зарегистрирован
mixin_path = os.path.join(JAVA, "mixin", "MultiNoiseBiomeSourceDesolationMixin.java")
check(os.path.isfile(mixin_path), "нет MultiNoiseBiomeSourceDesolationMixin.java")
mixins_cfg = os.path.join(RES, "gonzotech.mixins.json")
check(os.path.isfile(mixins_cfg), "нет gonzotech.mixins.json")
if os.path.isfile(mixins_cfg):
    with open(mixins_cfg, encoding="utf-8") as f:
        cfg = json.load(f)
    check("MultiNoiseBiomeSourceDesolationMixin" in cfg.get("mixins", []),
          "MultiNoiseBiomeSourceDesolationMixin не зарегистрирован в mixins[]")

# 2. целевой метод + RETURN + cancellable
if os.path.isfile(mixin_path):
    with open(mixin_path, encoding="utf-8") as f:
        mx = f.read()
    check("getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)"
          "Lnet/minecraft/core/Holder;" in mx,
          "mixin celit не в тот метод (getNoiseBiome(TargetPoint))")
    check('@At("RETURN")' in mx, "инъекция не на RETURN")
    check("cancellable = true" in mx, "инъекция не cancellable")
    check("catch (Throwable" in mx, "нет fail-safe try/catch")
    check("@Mixin(MultiNoiseBiomeSource.class)" in mx, "нет @Mixin(MultiNoiseBiomeSource.class)")

# 3. класс-держатель
inject_path = os.path.join(JAVA, "core", "worldgen", "DesolationBiomeInject.java")
check(os.path.isfile(inject_path), "нет DesolationBiomeInject.java")
if os.path.isfile(inject_path):
    with open(inject_path, encoding="utf-8") as f:
        inj = f.read()
    check("ServerAboutToStartEvent" in inj, "нет захвата на ServerAboutToStartEvent")
    check("lookupOrThrow(Registries.BIOME)" in inj, "нет lookupOrThrow(Registries.BIOME)")
    check("getOrThrow(DESOLATION)" in inj, "нет getOrThrow(DESOLATION)")
    check("catch (Throwable" in inj, "нет fail-safe в захвате холдера")
    boxes = re.findall(r"box\(0\.15F", inj)
    check(len(boxes) == 4, f"ожидалось 4 климат-бокса, найдено {len(boxes)}")
    check("Climate.Parameter.span" in inj, "нет Climate.Parameter.span")
    check("volatile Holder<Biome> desolation" in inj, "нет volatile-холдера")

# 4. нет dimension-оверрайда
check(not os.path.exists(os.path.join(RES, "data", "minecraft", "dimension", "overworld.json")),
      "dimension/overworld.json снова появился (ломает суперплоский)")

# 5. нет preset-оверрайда
check(not os.path.exists(os.path.join(
        RES, "data", "minecraft", "worldgen",
        "multi_noise_biome_source_parameter_list", "overworld.json")),
      "multi_noise_biome_source_parameter_list/overworld.json снова появился (не парсится)")

# 6. биом на месте
check(os.path.isfile(os.path.join(RES, "data", "gonzotech", "worldgen", "biome", "desolation.json")),
      "нет data/gonzotech/worldgen/biome/desolation.json")

if failures:
    print("FAIL: desolation runtime-mixin contract broken:")
    for m in failures:
        print("  -", m)
    sys.exit(1)
print("OK: desolation runtime-mixin contract holds")

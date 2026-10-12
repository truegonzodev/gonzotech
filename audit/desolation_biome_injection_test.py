#!/usr/bin/env python3
"""Пин: desolation в оверворлде — мутация пресета ПОСЛЕ сборки реестров.

История (все подходы проверены запуском):
  - dimension/overworld.json            -> ломает суперплоский;
  - preset-реестр multi_noise_...       -> принимает только {"preset":...};
  - mixin в OverworldBiomeBuilder       -> краш bootstrap'а (висячие ссылки);
  - mixin в getNoiseBiome               -> инъекция была, но сетка/цвета мимо;
  - климат-фичи без биома (0.3.189)     -> контент без цветов биома;
  - mixin в конструктор пресета (0.3.190/191) -> крах bootstrap'а: lookup
    RegistrySetBuilder выдаёт unbound-плейсхолдеры и сам вызов get()
    регистрирует висячую ссылку («Unreferenced key»).

Итог: точки дописываются в УЖЕ ПОСТРОЕННЫЙ пресет minecraft:overworld на
ServerAboutToStartEvent (реестры собраны, холдеры bound, builder не участвует).
Ванильный findValue сам даёт частоту/границы; биом в сетке -> цвета/F3//locate/
конверсия/радиация согласованы.

Контракт:
  1. DesolationPresetExt (интерфейс-мост) + mixin его реализует, метод
     gonzotech$addDesolationPoints, 4 бокса, НЕТ @Inject в конструктор;
  2. DesolationPresetInject: ServerAboutToStartEvent, lookupOrThrow(BIOME).get,
     lookupOrThrow(MULTI_NOISE...).get(overworld), instanceof ext;
  3. mixin зарегистрирован в gonzotech.mixins.json;
  4. контент по биому (конверсия/стволы/радиация), НЕТ климат-фич;
  5. НЕТ мёртвых подходов (dimension, preset-оверрайд, getNoiseBiome-mixin,
     DesolationBiomeInject, конструктор-инжект);
  6. биом desolation.json с effects и фичами.
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


# 1. мост + mixin
ext = os.path.join(JAVA, "core", "worldgen", "DesolationPresetExt.java")
check(os.path.isfile(ext), "нет DesolationPresetExt.java")
mx_path = os.path.join(JAVA, "mixin", "MultiNoiseParameterListDesolationMixin.java")
check(os.path.isfile(mx_path), "нет MultiNoiseParameterListDesolationMixin.java")
if os.path.isfile(mx_path):
    mx = read(mx_path)
    check("implements DesolationPresetExt" in mx, "mixin не реализует DesolationPresetExt")
    check("gonzotech$addDesolationPoints" in mx, "нет gonzotech$addDesolationPoints")
    check("@Inject" not in mx, "mixin всё ещё использует @Inject (конструктор-инжект мёртв)")
    check("@Mutable" in mx and "parameters" in mx, "нет @Mutable-поля parameters")
    ext_src = read(ext)
    check(ext_src.count("box(0.05F") + ext_src.count("box(-0.45F") == 6,
          "ожидалось 6 климат-боксов в DesolationPresetExt (расширены в 0.3.194)")

# 2. обработчик события
inj = os.path.join(JAVA, "core", "worldgen", "DesolationPresetInject.java")
check(os.path.isfile(inj), "нет DesolationPresetInject.java")
if os.path.isfile(inj):
    i = read(inj)
    check("ServerAboutToStartEvent" in i, "нет ServerAboutToStartEvent")
    check("lookupOrThrow(Registries.BIOME)" in i, "нет lookupOrThrow(BIOME)")
    check("MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST" in i, "нет реестра параметр-листов")
    check("instanceof DesolationPresetExt" in i, "нет вызова через DesolationPresetExt")
    check("catch (Throwable" in i, "нет fail-safe в обработчике")

# 3. регистрация mixin
mixcfg = json.load(open(os.path.join(RES, "gonzotech.mixins.json"), encoding="utf-8"))
check("MultiNoiseParameterListDesolationMixin" in mixcfg.get("mixins", []),
      "mixin пресета не зарегистрирован")

# 0.3.197: живой источник правится перехватом ВОЗВРАТА parameters()
# (в 1.21.4 у MultiNoiseBiomeSource нет теневого поля — хранение на Either,
# список отдаёт метод parameters(); @Shadow field крашил bootstrap).
src_mx = os.path.join(JAVA, "mixin", "LiveBiomeSourceDesolationMixin.java")
check(os.path.isfile(src_mx), "нет LiveBiomeSourceDesolationMixin.java")
if os.path.isfile(src_mx):
    sm = read(src_mx)
    check("method = \"parameters\"" in sm and "@At(\"RETURN\")" in sm,
          "source-mixin не перехватывает возврат parameters()")
    check("setReturnValue" in sm and "DesolationLiveCache" in sm,
          "source-mixin: нет подмены возврата/кэша холдера")
    check("public static" not in sm and "private static" not in sm,
          "в source-mixin остались static-члены (запрещены mixin'ом)")
    check("@Shadow" not in sm, "source-mixin снова тянет @Shadow (поле недоступно в 1.21.4)")
check("LiveBiomeSourceDesolationMixin" in mixcfg.get("mixins", []),
      "LiveBiomeSourceDesolationMixin не зарегистрирован")
inj_src = read(inj)
check("LevelEvent.Load" in inj_src, "нет дозаправки на LevelEvent.Load")
check("Level.OVERWORLD" in inj_src, "дозаправка не ограничена оверворлдом")
check("DesolationLiveCache.cacheDesolation" in inj_src, "обработчик не кэширует холдер для source-mixin")

# 4. контент по биому
conv = read(os.path.join(JAVA, "core", "worldgen", "DesolationConversionFeature.java"))
check(".is(DESOLATION)" in conv, "конверсия не гейтится по биому")
check("DesolationClimate" not in conv, "конверсия ссылается на удалённый климат-гейт")
trunk = read(os.path.join(JAVA, "core", "worldgen", "DeadTrunkFeature.java"))
check("DesolationClimate" not in trunk, "стволы ссылаются на удалённый климат-гейт")
rad = read(os.path.join(JAVA, "radiation", "ChunkRadiationData.java"))
check("DESOLATION" in rad and "getBiome" in rad, "радиация не по биому desolation")
check(not os.path.isfile(os.path.join(JAVA, "core", "worldgen", "DesolationClimate.java")),
      "DesolationClimate.java не удалён")
check(not os.path.exists(os.path.join(
    RES, "data", "gonzotech", "neoforge", "biome_modifier", "add_desolation_features.json")),
    "biome_modifier климат-фич не удалён")

# 5. мёртвые подходы отсутствуют
for dead in ("MultiNoiseBiomeSourceDesolationMixin.java",):
    check(not os.path.isfile(os.path.join(JAVA, "mixin", dead)), f"{dead} не удалён")
check(not os.path.isfile(os.path.join(JAVA, "core", "worldgen", "DesolationBiomeInject.java")),
      "DesolationBiomeInject не удалён")
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
    check("desolation_trunks" in feats, "в биоме нет фичи стволов")
    check("desolation_conversion" not in feats,
          "конверсия должна идти через mixin applyBiomeDecoration, не из списка биома")

# 7. конверсия — через mixin на каждый чанк; стволы не триггерят её
cdx = os.path.join(JAVA, "mixin", "ChunkDecorationDesolationMixin.java")
check(os.path.isfile(cdx), "нет ChunkDecorationDesolationMixin.java")
if os.path.isfile(cdx):
    c = read(cdx)
    check("applyBiomeDecoration" in c and "@At(\"HEAD\")" in c,
          "mixin не инжектится в HEAD applyBiomeDecoration")
    check("convertChunk" in c and "catch (Throwable" in c,
          "mixin не вызывает convertChunk fail-safe")
check("ChunkDecorationDesolationMixin" in mixcfg.get("mixins", []),
      "ChunkDecorationDesolationMixin не зарегистрирован")
check("convertChunk" not in trunk, "DeadTrunkFeature всё ещё триггерит конверсию")
check("doneChunks" not in conv, "в конверсии остался дедуп doneChunks")
check(not os.path.exists(os.path.join(
    RES, "data", "gonzotech", "worldgen", "placed_feature",
    "desolation_conversion_placed.json")),
      "мёртвый placed_feature конверсии не удалён")

if failures:
    print("FAIL: desolation post-build preset-injection contract broken:")
    for m in failures:
        print("  -", m)
    sys.exit(1)
print("OK: desolation post-build preset-injection contract holds")

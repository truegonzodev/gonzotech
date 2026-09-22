#!/usr/bin/env python3
"""Статическая валидация ресурсов Gonzo Tech (без Java/Minecraft).

Проверяет:
  * все JSON парсятся;
  * у каждого items/<id>.json цель модели существует (item/… или block/…);
  * все текстуры, на которые ссылаются модели, существуют и путь в НИЖНЕМ регистре
    (в MC ResourceLocation с заглавными буквами невалиден → «missing model»);
  * у зарегистрированных предметов/блоков есть lang-ключ в en_us и ru_ru, состав ключей совпадает;
  * ссылки моделей/рецептов/тегов на gonzotech-id не битые;
  * у каждого блока есть лут-таблица (кроме известных исключений);
  * у каждого зарегистрированного эффекта мода есть иконка textures/mob_effect/<id>.png
    (эффект без иконки видно в игре пустой рамкой — так проскочил «Зуд», 22.09.2026).
"""
import json, glob, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # корень репозитория
RES = os.path.join(ROOT, "src/main/resources")
NS = "gonzotech"
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)

errors, warnings = [], []


def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


# ── 1. JSON ────────────────────────────────────────────────────────────────
jsons = glob.glob(os.path.join(RES, "**/*.json"), recursive=True)
for p in jsons:
    try:
        load(p)
    except Exception as e:                                    # noqa: BLE001
        errors.append(f"битый JSON: {os.path.relpath(p, ROOT)} — {e}")

# ── 2. items-дефиниции → модели ────────────────────────────────────────────
models_item = {os.path.basename(p)[:-5] for p in glob.glob(f"{ASSETS}/models/item/*.json")}
models_block = {os.path.relpath(p, f"{ASSETS}/models/block")[:-5]
                for p in glob.glob(f"{ASSETS}/models/block/**/*.json", recursive=True)}
item_defs = sorted(os.path.basename(p)[:-5] for p in glob.glob(f"{ASSETS}/items/*.json"))

for p in glob.glob(f"{ASSETS}/items/*.json"):
    d = load(p)
    targets = []
    m = d.get("model")
    if isinstance(m, dict):
        targets = [v for k, v in m.items() if k == "model" and isinstance(v, str)]
    for t in targets:
        if ":" not in t:
            t = f"minecraft:{t}"
        nsp, path = t.split(":", 1)
        if nsp != NS:
            continue
        if path.startswith("item/") and path[5:] not in models_item:
            errors.append(f"items/{os.path.basename(p)}: нет модели {t}")
        elif path.startswith("block/") and path[6:] not in models_block:
            errors.append(f"items/{os.path.basename(p)}: нет модели {t}")

# ── 3. текстуры моделей: существование + регистр ───────────────────────────
def tex_path(ref):
    if ":" in ref:
        nsp, path = ref.split(":", 1)
    else:
        nsp, path = "minecraft", ref
    if nsp != NS:
        return None
    return path


for p in glob.glob(f"{ASSETS}/models/**/*.json", recursive=True):
    rel = os.path.relpath(p, ASSETS)
    try:
        d = load(p)
    except Exception:                                          # noqa: BLE001
        continue
    for _, ref in (d.get("textures") or {}).items():
        if not isinstance(ref, str):
            continue
        path = tex_path(ref)
        if path is None or path.endswith("/") or "#" in ref:
            continue
        if not os.path.exists(f"{ASSETS}/textures/{path}.png"):
            errors.append(f"{rel}: нет текстуры {ref}")
    for txt in json.dumps(d).split('"'):
        if txt.startswith(f"{NS}:") and re.search(r"[A-Z]", txt):
            errors.append(f"{rel}: заглавные буквы в ссылке {txt} (невалидный ResourceLocation)")

for r, _, fs in os.walk(ASSETS):
    for f in fs:
        rel = os.path.relpath(os.path.join(r, f), ASSETS)
        if re.search(r"[A-Z]", rel):
            errors.append(f"файл с заглавными буквами в пути: assets/{rel}")

# ── 4. lang ────────────────────────────────────────────────────────────────
lang_files = {}
for loc in ("en_us", "ru_ru"):
    lang_files[loc] = load(f"{ASSETS}/lang/{loc}.json")
en, ru = lang_files["en_us"], lang_files["ru_ru"]
if set(en) != set(ru):
    errors.append(f"состав ключей lang расходится: только EN {len(set(en)-set(ru))}, "
                  f"только RU {len(set(ru)-set(en))}")
for loc, d in lang_files.items():
    for k, v in d.items():
        if v is None or v == "":
            errors.append(f"{loc}: пустое значение {k}")
        if "\\n" in str(v):
            warnings.append(f"{loc}: литеральный \\n (двойное экранирование?) в {k}")

# blockstate -> модель должна существовать
for p in glob.glob(f"{ASSETS}/blockstates/*.json"):
    rel = os.path.relpath(p, ASSETS)
    d = load(p)
    for variant_key in ("variants", "multipart"):
        node = d.get(variant_key)
        if node is None:
            continue
        stack = [node]
        while stack:
            cur = stack.pop()
            if isinstance(cur, dict):
                if isinstance(cur.get("model"), str):
                    ref = cur["model"]
                    if ref.startswith(f"{NS}:"):
                        target = ref.split(":", 1)[1]
                        if target.startswith("block/") and target[6:] not in models_block:
                            errors.append(f"{rel}: нет модели {ref}")
                stack.extend(cur.values())
            elif isinstance(cur, list):
                stack.extend(cur)

blockstates = {os.path.basename(p)[:-5] for p in glob.glob(f"{ASSETS}/blockstates/*.json")}
for b in sorted(blockstates):
    if f"block.{NS}.{b}" not in en:
        errors.append(f"нет lang-ключа block.{NS}.{b}")
for i in item_defs:
    if i in blockstates:
        continue
    if f"item.{NS}.{i}" not in en:
        errors.append(f"нет lang-ключа item.{NS}.{i}")

# ── 5. ссылки recipe/tags/model на gonzotech-id ────────────────────────────
known = set(item_defs) | blockstates
# плюс всё, что встречается как id в реестрах java — грубая аппроксимация: items+blockstates+advancements
for p in glob.glob(f"{DATA}/recipe/**/*.json", recursive=True) + \
         glob.glob(f"{DATA}/tags/**/*.json", recursive=True):
    txt = open(p, encoding="utf-8").read()
    for ref in re.finditer(r'"gonzotech:([a-z0-9_/]+)"', txt):
        target = ref.group(1)
        if target.startswith(("block/", "item/", "category/", "tag/")):
            continue
        if target not in known and not os.path.exists(f"{ASSETS}/blockstates/{target}.json"):
            warnings.append(f"{os.path.relpath(p, DATA)}: ссылка на неизвестный id {target}")

# ── 6. лут-таблицы блоков ──────────────────────────────────────────────────
LOOT_EXCEPTIONS = {"molten_corium"}   # флюидный блок, лут не положен
loot = {os.path.basename(p)[:-5] for p in glob.glob(f"{DATA}/loot_table/blocks/*.json")}
for b in sorted(blockstates - loot - LOOT_EXCEPTIONS):
    errors.append(f"у блока {b} нет лут-таблицы")

# ── 7. иконки эффектов: у каждого id из ModEffects есть textures/mob_effect/<id>.png ──
mod_effects_src = os.path.join(ROOT, "src/main/java/com/gonzotech/core/registry/ModEffects.java")
if os.path.exists(mod_effects_src):
    effect_ids = re.findall(r'MOB_EFFECTS\.register\(\s*"([a-z0-9_]+)"',
                            open(mod_effects_src, encoding="utf-8").read())
    for effect_id in effect_ids:
        if not os.path.exists(f"{ASSETS}/textures/mob_effect/{effect_id}.png"):
            errors.append(
                f"у эффекта {effect_id} нет иконки: assets/gonzotech/textures/mob_effect/{effect_id}.png")

# ── отчёт ──────────────────────────────────────────────────────────────────
print(f"JSON: {len(jsons)} | items-дефиниций: {len(item_defs)} | models/item: {len(models_item)} | "
      f"models/block: {len(models_block)} | блокстейтов: {len(blockstates)} | "
      f"лут-таблиц: {len(loot)}")
print(f"lang: en={len(en)} ru={len(ru)}")
print(f"ошибок: {len(errors)}, предупреждений: {len(warnings)}")
for e in errors:
    print("  ✗", e)
for w in warnings:
    print("  ⚠", w)
sys.exit(1 if errors else 0)

#!/usr/bin/env python3
"""Сверка правил именования Gonzo Tech (автор, 21.09.2026).

Правило из двух независимых половин:

  1) ПРЕФИКС id (`first_` / `second_` / `third_`) — к какому «Открытию» относится
     механизм, то есть какое открытие гейтит его крафт.
     Источники истины: `Phase3Events.gate()` (Открытие 1),
     `TierTwoCrafting.RECIPE_IDS` (Открытие 2) и
     `TierThreeCrafting.RECIPE_IDS` (Открытие 3).

  2) ЦИФРА в ИМЕНИ (— / II / III) — версия механизма: была ли до неё менее
     эффективная. Значит: цифра есть ⟺ существует «первая» версия
     (`X` или `first_X`), иначе цифры быть не должно.

Механизмы, крафт которых не гейтится ни одним открытием (стартовые топка/котёл/
стирлинг/конденсатор, админские `singular_*`, `solar_panel`), под правило 1 не
попадают — им префикс не положен.

Запуск: python3 audit/naming_scan.py
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # корень репозитория
SRC = f"{ROOT}/src/main/java/com/gonzotech"
MM, P3, T2 = f"{SRC}/machines/registry/ModMachines.java", f"{SRC}/core/event/Phase3Events.java", f"{SRC}/machines/crafting/TierTwoCrafting.java"
MB = f"{SRC}/core/registry/ModBlocks.java"          # блоки вне «машинного» реестра (дверь Эпохи III)
T3 = f"{SRC}/machines/crafting/TierThreeCrafting.java"   # гейт «Открытия 3»
LANG = f"{ROOT}/src/main/resources/assets/gonzotech/lang/ru_ru.json"

# ── реестр блоков: константа -> id (+ какие id вообще существуют) ──────────
REGISTER = r'DeferredBlock<[^>]+>\s+(\w+)\s*=\s*\n?\s*BLOCKS\.register(?:Block|SimpleBlock)\(\s*"([a-z0-9_]+)"'
mm = open(MM, encoding="utf-8").read()
const2id = {m.group(1): m.group(2) for m in re.finditer(REGISTER, mm)}
ids = set(const2id.values())            # «машины»: печатаются в таблице
mb = open(MB, encoding="utf-8").read()
ids_blocks = {m.group(2) for m in re.finditer(REGISTER, mb)}   # прочие блоки: только проверки
ids |= ids_blocks

# ── гейты ─────────────────────────────────────────────────────────────────
p3 = open(P3, encoding="utf-8").read()
gate_src = p3[p3.index("craftGate = Map.ofEntries("):p3.index(");", p3.index("craftGate = Map.ofEntries("))]
tier = {}
for m in re.finditer(r'ModMachines\.(\w+)\.get\(\),\s*(\d+)', gate_src):     # тир 1
    const, t = m.group(1), int(m.group(2))
    bare = const[:-5] if const.endswith("_ITEM") else const
    if bare in const2id:
        tier[const2id[bare]] = t

for src, t in ((T2, 2), (T3, 3)):                                            # тир 2 и тир 3
    body = open(src, encoding="utf-8").read()
    body = body[body.index("RECIPE_IDS"):body.index(");", body.index("RECIPE_IDS"))]
    for r in re.findall(r'"gonzotech:([a-z0-9_]+)"', body):
        hits = [b for b in ids if r == b or r.startswith(b + "_")]
        if hits:
            tier.setdefault(max(hits, key=len), t)

# пакеты труб (composite_pipe / second_composite_pipe) не крафтятся вовсе —
# они формируются в мире гаечным ключом из труб своего поколения
WORLD_FORMED = {"composite_pipe", "second_composite_pipe"}

lang = json.load(open(LANG, encoding="utf-8"))
name = lambda i: lang.get(f"block.gonzotech.{i}") or lang.get(f"item.gonzotech.{i}") or "(нет ключа)"

NUMERAL = re.compile(r"\b(II|III)\b")
base = lambda i: re.sub(r"^(first|second|third)_", "", i)


def generation(i):
    """Порядок поколений внутри семейства: голый id (стартовое/тир-1) < first_ < second_ < third_."""
    if not i.startswith(("first_", "second_", "third_")):
        return 0
    for rank, pre in enumerate(("first_", "second_", "third_"), start=1):
        if i.startswith(pre):
            return rank
    return 9


pair_of = {}
for i in ids:
    pair_of.setdefault(base(i), []).append(i)

problems = []
print(f"{'id':32} {'гейт':9} {'имя':33} {'цифра':6} {'версия-до':9}")
machine_ids = set(const2id.values())          # «машины» печатаются в таблице, прочие блоки — только проверяются
for i in sorted(ids):
    b, nm, t = base(i), name(i), tier.get(i)
    others = [x for x in pair_of[b] if x != i]
    has_num = bool(NUMERAL.search(nm))
    # «цифра = версия»: цифра нужна НЕ всем в семействе, а только тем, у кого есть
    # более ранняя версия. Порядок поколений: голый id (стартовое/тир-1) < first_ < second_ < third_.
    is_later = i != min([i] + others, key=generation)
    if i in machine_ids:
        gate = f"Открытие {t}" if t else ("—" if i in WORLD_FORMED else "не гейтится")
        print(f"{i:32} {gate:9} {nm[:32]:33} {'да' if has_num else '—':6} {'да' if is_later else '—':9}")

    # правило 1: префикс ↔ гейт
    if t == 1 and not i.startswith("first_"):
        problems.append(f"ID: {i} гейтится Открытием 1, но префикса first_ нет")
    if t == 2 and not i.startswith("second_"):
        problems.append(f"ID: {i} гейтится Открытием 2, но префикса second_ нет")
    if i.startswith("first_") and t != 1:
        problems.append(f"ID: {i} с префиксом first_, но гейт = {t or 'нет'}")
    if i.startswith("second_") and t != 2 and i not in WORLD_FORMED:
        problems.append(f"ID: {i} с префиксом second_, но гейт = {t or 'нет'}")
    if t == 3 and not i.startswith("third_"):
        problems.append(f"ID: {i} гейтится Открытием 3, но префикса third_ нет")
    if i.startswith("third_") and t != 3 and i not in WORLD_FORMED:
        problems.append(f"ID: {i} с префиксом third_, но гейт = {t or 'нет'}")
    # правило 2: цифра ↔ наличие первой версии
    if has_num and not is_later:
        problems.append(f"ИМЯ: {i} («{nm}») с цифрой версии, но это первая версия механизма")
    if is_later and not has_num:
        problems.append(f"ИМЯ: {i} («{nm}») вторая версия, но в имени нет цифры")

third = sorted(i for i, t in tier.items() if t == 3)
if third:
    print(f"\nтретий тир (Открытие 3): {', '.join(third)}")

print("\n── расхождения ──")
for p in problems:
    print("  ⚠", p)
print(f"\nблоков сверх «машинного» реестра проверено: {len(ids_blocks)}"
      f" | без префикса и без гейта (правило не применяется): "
      f"{', '.join(sorted(i for i in ids if not i.startswith(('first_', 'second_')) and i not in tier))}")
print(f"расхождений: {len(problems)}")
sys.exit(1 if problems else 0)

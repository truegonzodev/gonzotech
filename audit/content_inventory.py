#!/usr/bin/env python3
"""Reproducible resource inventory, NOT a survival reachability solver.

Item-definition IDs are the census boundary. Machine code, tags, components and
world access require human review. A self-drop is not a primary acquisition path.
Usage: python3 audit/content_inventory.py [--check]
"""
from pathlib import Path
from collections import defaultdict
import argparse
import csv
import io
import json
import re

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
SRC = ROOT / 'src/main/java'
OUTPUT = ROOT / 'docs/CONTENT-INVENTORY-2026-09-27.csv'
SPECIAL = {'alloy_' + part for part in ('boots','chestplate','helmet','leggings','pickaxe','sword')}


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for child in value.values():
            yield from strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from strings(child)


def nodes(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from nodes(child)
    elif isinstance(value, list):
        for child in value:
            yield from nodes(child)


def generate():
    ids = {p.stem for p in (RES / 'assets/gonzotech/items').glob('*.json')}
    recipes, special, inputs, loot, java = (defaultdict(set) for _ in range(5))
    recipe_files = sorted((RES / 'data/gonzotech/recipe').glob('*.json'))
    for path in recipe_files:
        data = json.loads(path.read_text(encoding='utf-8'))
        result = data.get('result', {})
        result = result.get('id', '') if isinstance(result, dict) else result
        if isinstance(result, str) and result.startswith('gonzotech:'):
            recipes[result.split(':',1)[1]].add(path.stem)
        elif data.get('type','').removeprefix('gonzotech:') in SPECIAL:
            special[data['type'].split(':',1)[1]].add(path.stem)
        for token in strings({k:v for k,v in data.items() if k in ('key','ingredient','ingredients','base','addition','template')}):
            if token.startswith('gonzotech:'):
                inputs[token.split(':',1)[1]].add(path.stem)
    for path in sorted((RES / 'data').glob('**/loot_table/**/*.json')):
        for node in nodes(json.loads(path.read_text(encoding='utf-8'))):
            if node.get('type') == 'minecraft:item' and node.get('name','').startswith('gonzotech:'):
                loot[node['name'].split(':',1)[1]].add(str(path.relative_to(RES)))
    # Literal IDs and conventional ModItems.CONSTANT references: evidence only.
    # Dynamic registry lookups are deliberately not labelled as producers/consumers.
    for path in sorted(SRC.rglob('*.java')):
        text = path.read_text(encoding='utf-8')
        tokens = set(re.findall(r'"(?:gonzotech:)?([a-z][a-z0-9_]*)"', text))
        tokens.update(s.lower() for s in re.findall(r'ModItems\.([A-Z][A-Z0-9_]*)', text))
        for item in ids & tokens:
            java[item].add(str(path.relative_to(SRC)))
    buf = io.StringIO(newline='')
    writer = csv.writer(buf, lineterminator='\n')
    writer.writerow(['item_definition_id','json_output_recipes','special_serializer_recipes',
                     'json_ingredient_recipes','loot_item_entries_NOT_primary_source_proof',
                     'java_literal_or_ModItems_refs_NOT_usage_proof'])
    for item in sorted(ids):
        writer.writerow([item] + [';'.join(sorted(table[item])) for table in (recipes,special,inputs,loot,java)])
    # defaultdict reads above inserted every ID: count non-empty values, not keys.
    n = sum(bool(recipes[item]) for item in ids)
    dynamic = sum(bool(special[item]) for item in ids)
    print(f'{len(ids)} item definitions; {len(recipe_files)} recipe files; {n} explicit-output IDs; '
          f'{dynamic} special-serializer IDs; {sum(not recipes[i] and not special[i] for i in ids)} without a JSON production recipe')
    return buf.getvalue()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    text = generate()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding='utf-8') != text:
            raise SystemExit('Inventory changed: regenerate and review audit classifications')
        print('Inventory snapshot is current')
    else:
        OUTPUT.write_text(text, encoding='utf-8')
        print(OUTPUT.relative_to(ROOT))

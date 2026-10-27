#!/usr/bin/env python3
"""Docs migration 0.3.311: immutable originals, current ownership and task lifecycle."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
SNAP = DOCS / 'archive/legacy-0.3.311'
manifest = json.loads((SNAP / 'manifest.json').read_text())
files = manifest['files']
EXPECTED = {
    'RULES.md', 'DOCS_RULES.md', 'CURRENT-STATE.md', 'HISTORY.md', 'DEFFERED.md',
    'PROGRESSION.md', 'LORE.md', 'AUTHORS-NOTES.md', 'AUDIT.md', 'RAD_WORLD.md',
    'RAD_ITEMS.md', 'MACHINES.md', 'REACTORS.md', 'AUTOMINER.md', 'CAPABILITIES.md',
    'CHALKBOARD.md', 'SCHOLAR_NOTES.md', 'INTEGRATIONS.md', 'MULTIBLOCK.md', 'JEI.md', 'SUPER_CRUCIAL_FILE.md', 'WORLDGEN.md', 'MATERIALS.md',
    'EQUIPMENT.md', 'PSYCHE.md', 'CLEANROOM.md', 'AUDIO_VISUAL.md',
    'meditation_concept.md', 'reactor_extensions_concept.md',
}
assert {p.name for p in DOCS.iterdir() if p.is_file()} == EXPECTED
assert {p.name for p in DOCS.iterdir() if p.is_dir()} == {'archive'}
navigation = re.findall(r'^\| \[[^]]+\]\(([^)]+)\)', (DOCS / 'DOCS_RULES.md').read_text(), re.M)
assert set(navigation) == EXPECTED, 'navigation must target all live documents, not historical copies'
assert len(files) == 75 and len({r['original'] for r in files}) == 75
assert len({r['archived'] for r in files}) == 75

for record in files:
    archive = ROOT / record['archived']
    assert SNAP in archive.parents, archive
    data = archive.read_bytes()
    assert len(data) == record['bytes'], archive
    assert hashlib.sha256(data).hexdigest() == record['sha256'], archive
    assert record['destinations'], record
    for destination in record['destinations']:
        assert (ROOT / destination).is_file(), destination

# If this historical Git object is available, prove the manifest inventories the
# complete old docs tree rather than merely validating a self-selected subset.
base = manifest['source_commit']
exists = subprocess.run(['git', 'cat-file', '-e', f'{base}^{{commit}}'], cwd=ROOT,
                        capture_output=True).returncode == 0
if exists:
    old = set(subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', base, '--', 'docs'],
                                     cwd=ROOT, text=True).splitlines())
    assert old == {r['original'] for r in files}
    for record in files:
        data = subprocess.check_output(['git', 'show', f"{base}:{record['original']}"], cwd=ROOT)
        assert hashlib.sha256(data).hexdigest() == record['sha256']
else:
    print('INFO: historical Git object not present (e.g. shallow clone); all archived hashes verified')

sections = json.loads((SNAP / 'sections.json').read_text())
assert len(sections) == 4173
expected_sections = []
archived_lines = {}
for record in files:
    if not record['original'].endswith('.md'):
        continue
    lines = (ROOT / record['archived']).read_text().splitlines()
    archived_lines[record['archived']] = lines
    for index, line in enumerate(lines, 1):
        if re.match(r'^#{1,6}\s|^\s*(?:[-*]|\d+[.)])\s', line):
            expected_sections.append((record['original'], index, line))
assert set(expected_sections) == {(r['source'], r['line'], r['text']) for r in sections}
assert len(expected_sections) == len(sections)
for section in sections:
    assert archived_lines[section['archived']][section['line'] - 1] == section['text']

current = (DOCS / 'CURRENT-STATE.md').read_text()
deferred = (DOCS / 'DEFFERED.md').read_text()
def tasks(text):
    return re.findall(r'^### ([\w-]+) — ', text, re.M)
assert len(tasks(current)) == len(set(tasks(current)))
assert len(tasks(deferred)) == len(set(tasks(deferred)))
for text in (current, deferred):
    assert set(re.findall(r'^#{1,6} ([A-Z][\w-]+) — ', text, re.M)) == set(tasks(text)), 'task headings must remain level 3'
assert set(tasks(current)).isdisjoint(tasks(deferred))
assert 'четыре последующих' in (DOCS / 'DOCS_RULES.md').read_text()
for block in re.split(r'(?=^### [\w-]+ — )', current, flags=re.M)[1:]:
    assert '**Статус:' in block
    if '**Статус: закрыто.**' in block:
        closed = re.search(r'Закрыто в 0\.3\.(\d+)', block)
        remove = re.search(r'Удалить при 0\.3\.(\d+)', block)
        assert closed and remove and int(remove.group(1)) == int(closed.group(1)) + 4

# Every one of the 69 previous numbered roadmap entries has an explicit disposition.
plan = (SNAP / 'PIATILETOKA-2026-10-26.md').read_text()
old_ids = {f'{f}{n}' for f, n in re.findall(r'^- \*\*([РЕЗП])(\d+)\s', plan, re.M)}
coverage = (SNAP / 'MIGRATION-MAP.md').read_text()
assert len(old_ids) == 69
for identifier in old_ids:
    assert f'| {identifier} |' in coverage, identifier
assert 'Закатной станок' in coverage and 'роль передана прессу' in deferred

# Check current projections against actual code, not historical count fixtures.
java = ROOT / 'src/main/java/com/gonzotech'
pages = re.findall(r'new ScholarPage\((\d+), ScholarChapter\.(\w+), ScholarUnlock\.(\w+),',
                   (java / 'chalkboard/notes/ScholarNotesContent.java').read_text())
assert len(pages) == 51 and len({p[0] for p in pages}) == 51
chalk = (DOCS / 'SCHOLAR_NOTES.md').read_text()
for number, chapter, gate in pages:
    assert re.search(rf'^\| {number} \| .* \| {chapter} \| `{gate}` \|$', chalk, re.M)
cat_ids = re.findall(r'public static final Definition \w+ = define(?:TextOnly)?\(\s*"([^"]+)"',
                     (java / 'integration/jei/MachineJeiCategories.java').read_text())
assert len(cat_ids) == 24
jei = (DOCS / 'JEI.md').read_text()
for identifier in cat_ids:
    assert f'`{identifier}`' in jei
progress = (DOCS / 'PROGRESSION.md').read_text()
for index, title in re.findall(r'new DiscoveryDef\((\d+), "([^"]+)"',
                              (java / 'chalkboard/core/DiscoveryDef.java').read_text()):
    assert f'| {int(index) + 1} | {index} | {title} |' in progress

print(f'docs structure: {len(EXPECTED)} live docs, 75 byte-identical originals, 4173 source items, 69 roadmap dispositions; task lifecycle and code projections passed')

# Technical ownership and declared sinks must not disappear into HISTORY.
board = (DOCS / 'CHALKBOARD.md').read_text()
for symbol in ['ChalkboardWorldData', 'ChalkboardSubmissionValidator', 'validateClaim',
               'giveNatural', 'CluePayload', 'ChalkboardClueClient', 'sampleSolution']:
    assert symbol in board, symbol
assert '| 51 |' not in board, 'page inventory belongs to SCHOLAR_NOTES'
machines = (DOCS / 'MACHINES.md').read_text()
for interface in re.findall(r'public interface (\w+)', (java / 'machines/energy/Sinks.java').read_text()):
    assert f'`{interface}`' in machines, interface
for source in (java / 'machines').rglob('*BlockEntity.java'):
    text = source.read_text()
    if re.search(r'public\s+long\s+receive\w+\(', text) or re.search(r'new (?:GtBuffer|ResourceBuffer)\(', text):
        row = next((line for line in machines.splitlines() if line.startswith(f'| [{source.stem}](')), '')
        assert row, source
        for method in re.findall(r'public\s+long\s+(receive\w+)\(', text):
            assert f'`{method}`' in row, (source, method)
        for kind, name, expression in re.findall(r'(?:private|public|protected)\s+(?:final\s+)?(GtBuffer|ResourceBuffer)\s+(\w+)\s*=\s*new\s+\w+\(([^;]+)\);', text):
            assert f'`{kind} {name}: {expression}`' in row, (source, name)

multi = (DOCS / 'MULTIBLOCK.md').read_text()
for symbol in ['BlastFurnaceLayout', 'TurbineStructure', 'SteamGenStructure', 'SiliconFactoryStructure', 'ReactorStructure']:
    assert symbol in multi, symbol
rules = (DOCS / 'RULES.md').read_text()
runtime_patch = int(re.search(r'^mod_version=0\.3\.(\d+)$', (ROOT / 'gradle.properties').read_text(), re.M).group(1))
if runtime_patch <= 361:
    assert '0.3.361' in rules and '0.3.365' not in rules
assert 'D-INT1' in deferred + current + (DOCS / 'HISTORY.md').read_text()
print('docs ownership: board/notes, multiblocks, integration task and sink/BE inventory passed')

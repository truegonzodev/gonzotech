#!/usr/bin/env python3
"""Validate live Markdown links and explicit docs paths; frozen archives stay intact.

README is not technical working memory. Its existing links still must work.
Old non-link citations in source comments/history may resolve ONLY through the
explicit migration manifest; real Markdown links never get a silent alias.
"""
from pathlib import Path
import json
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'docs/archive/legacy-0.3.311/manifest.json'
migration = json.loads(MANIFEST.read_text())
ALIASES = {r['original']: r['archived'] for r in migration['files']}
FROZEN_ORIGINALS = {(ROOT / r['archived']).resolve() for r in migration['files']}
NONFILES = {r['reference'] for r in migration['historical_nonfiles']}
EXCLUDED = {'.git', '.gradle', '.cache', 'node_modules', 'build', 'run', 'audit/reports'}
# All original texts (including the former archive) are frozen by manifest.
# New migration metadata, including MIGRATION-MAP.md, is still validated.
FROZEN = ROOT / 'docs/archive/legacy-0.3.311'
LINK = re.compile(r'!?\[[^\]\n]*\]\(([^\s)]+)(?:\s+"[^"]*")?\)')
DOC_PATH = re.compile(r'(?<![\w/])docs/[\w./-]+\.(?:md|csv|xlsx)\b')
problems, citations = [], set()
checked = 0


def frozen(path):
    return path in FROZEN_ORIGINALS


def check_link(file, target, line):
    global checked
    if '\\' in target or target.startswith(('http:', 'https:', 'mailto:', 'data:')):
        return
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc:
        return
    path = (file.parent / unquote(parsed.path)).resolve() if parsed.path else file
    checked += 1
    if not path.exists():
        problems.append(f'{file.relative_to(ROOT)}:{line} → {target}')
        return
    # Historical anchors refer to the frozen edition, not current headings.
    if parsed.fragment and path.suffix == '.md' and not frozen(path):
        text = path.read_text(encoding='utf-8')
        ids = set(re.findall(r'<a\s+id=["\']([^"\']+)', text))
        counts = {}
        for heading in re.findall(r'^#{1,6}\s+(.+?)\s*#*$', text, re.M):
            slug = re.sub(r'[^\w\- ]', '', heading.lower()).replace(' ', '-')
            n = counts.get(slug, 0)
            counts[slug] = n + 1
            ids.add(slug + (f'-{n}' if n else ''))
        if unquote(parsed.fragment).lower() not in ids:
            problems.append(f'{file.relative_to(ROOT)}:{line} → unknown anchor {target}')


for file in sorted(ROOT.rglob('*')):
    rel = file.relative_to(ROOT)
    if not file.is_file() or file.suffix not in {'.md', '.java', '.py', '.gradle', '.properties', '.txt', '.mcmeta'}:
        continue
    if frozen(file) or any(p in EXCLUDED for p in rel.parts) or str(rel).startswith(('audit/reports/', 'run-radiation-')):
        continue
    try:
        text = file.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        continue
    if file.suffix == '.md':
        # Links in code fences are literal examples, not navigation.
        visible = re.sub(r'```[^\n]*\n.*?```', lambda m: '\n' * m.group().count('\n'), text, flags=re.S)
        visible = re.sub(r'`[^`\n]+`', lambda m: ' ' * len(m.group()), visible)
        for match in LINK.finditer(visible):
            check_link(file, match.group(1), visible[:match.start()].count('\n') + 1)
    for match in DOC_PATH.finditer(text):
        ref = match.group()
        if (ROOT / ref).is_file():
            continue
        if ref in ALIASES and (ROOT / ALIASES[ref]).is_file():
            citations.add(ref)
        elif ref in NONFILES:
            # Explicitly inventoried historical names, not existing source files.
            citations.add(ref)
        else:
            problems.append(f'{rel}:{text[:match.start()].count(chr(10)) + 1} → {ref}')

print(f'ссылок проверено: {checked}; замороженный архив исключён рекурсивно')
print(f'исторических текстовых цитат с явным архивным маршрутом: {len(citations)}')
for problem in problems:
    print('  ⚠', problem)
print(f'битых ссылок: {len(problems)}')
sys.exit(bool(problems))

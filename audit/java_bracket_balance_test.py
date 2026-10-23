#!/usr/bin/env python3
"""0.3.277-f2: баланс {} () [] во всех java-файлах src/main/java.

Ловит класс ошибок «забытая скобка в новом блоке» (сборка автора 23.10 падала
на `});` вместо `}));` в GonzoTechMod) там, где javac в песочнице недоступен.
Мини-лексер: пропускает блочные/строчные комментарии, строковые литералы,
char-литералы и текстовые блоки (тройные кавычки).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def strip_noncode(src: str) -> str:
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and nxt == "*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if c == '"' and src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            out.append('"')
            continue
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
            i += 1
            out.append('"')
            continue
        if c == "'":
            i += 1
            while i < n and src[i] != "'":
                i += 2 if src[i] == "\\" else 1
            i += 1
            out.append("'")
            continue
        out.append(c)
        i += 1
    return "".join(out)

bad = []
count = 0
for f in sorted((ROOT / "src/main/java").rglob("*.java")):
    count += 1
    t = strip_noncode(f.read_text(encoding="utf-8"))
    for a, b in (("{", "}"), ("(", ")"), ("[", "]")):
        if t.count(a) != t.count(b):
            bad.append(f"{f.relative_to(ROOT)}: {a}{t.count(a)} vs {b}{t.count(b)}")
            break

assert not bad, "Баланс скобок сломан:\n" + "\n".join(bad)
print(f"java_bracket_balance_test passed ({count} files)")

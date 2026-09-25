#!/usr/bin/env python3
"""Проверка ссылок на документы Gonzo Tech.

Ищем в живых файлах репозитория упоминания `*.md` (в бэктиках или как голый путь) и проверяем,
что файл существует. Правила:

  * `docs/archive/*.md` — **заморожены**: их содержимое не правим, поэтому ссылки внутри архивных
    документов не проверяются (история);
  * `docs/archive/README.md` — живой (карта архива), проверяется;
  * `info/*.md` — локальная папка автора (в `.gitignore`), в репозитории её нет → не проверяем;
  * «исторические» и «плановые» имена (старый TEMP_NOTES после переезда в UPDATED_TEMP_NOTES,
    будущий документ по радиации) — перечислены в HISTORICAL/PLANNED и печатаются как INFO,
    а не как ошибка: такие имена упоминаются только в рассказе о переезде/планах.

Запуск: python3 audit/doc_links.py
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # корень репозитория
TEXT_EXT = (".md", ".java", ".py", ".json", ".gradle", ".properties", ".txt", ".mcmeta")
FROZEN = re.compile(r"docs/archive/(?!README\.md)[^/]+\.md$")
# имена, которых в репо нет и не должно быть: говорим о них только в рассказе
HISTORICAL = {
    "docs/TEMP_NOTES.md", "TEMP_NOTES.md",
    "docs/LOGISTICS-TEMP-NOTES.md", "docs/MATERIAL_PROCESSING_CONCEPT.md",
    "docs/CHALKBOARD-SERVER-AUTHORITY-PATCH.md", "docs/CONTENT_AUDIT_2026-09-14.md",
}
PLANNED = {"docs/RADIATION.md"}
# ссылка: docs/...md либо голое имя файла (NAME + .md)
LINK = re.compile(r"(?:docs/)?[A-Za-z0-9_\-./]*[A-Za-z0-9_\-]+\.md")

problems, checked, info = [], 0, []
for root, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle", ".cache", "node_modules", "run", "src", "gradle")]
    for f in files:
        if not f.endswith(TEXT_EXT):
            continue
        path = os.path.join(root, f)
        rel = os.path.relpath(path, ROOT)
        frozen = bool(FROZEN.search(rel))
        try:
            text = open(path, encoding="utf-8").read()
        except (UnicodeDecodeError, OSError):
            continue
        for m in LINK.finditer(text):
            ref = m.group(0)
            if ref.startswith(("http", "assets/", "data/", "info/")):
                continue                                  # info/ — локальная папка автора
            checked += 1
            if ref in HISTORICAL or ref in PLANNED:
                info.append(ref)                          # старое имя / план, не битая ссылка
                continue
            if frozen:                                   # историю не трогаем
                continue
            candidates = [ref] if ref.startswith("docs/") else [ref, f"docs/{ref}", f"docs/archive/{ref}"]
            if not any(os.path.exists(os.path.join(ROOT, c)) for c in candidates):
                line = text[:m.start()].count("\n") + 1
                problems.append(f"{rel}:{line} → {ref}")

print(f"ссылок проверено: {checked} (архивные документы заморожены)")
for i in sorted(set(info)):
    print(f"  · историческое/плановое имя (ожидаемо нет в репо): {i}")
for p in problems:
    print("  ⚠", p)
print(f"битых ссылок: {len(problems)}")
sys.exit(1 if problems else 0)

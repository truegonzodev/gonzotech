#!/usr/bin/env python3
"""Единый гейт аудит-сьютов (инструмент агента, 08.10.2026).

Запускает каждый ``audit/*_test.py`` отдельным процессом и сводит результат в
три честных состояния:

* PASS — сьют прошёл;
* FAIL — сьют упал (регрессия или сломанный пин);
* SKIP — сьют не может выполниться в этой среде (нет JDK для compile-проверок
  или нет Pillow для растровых проверок). SKIP ≠ PASS.

Использование: ``python3 audit/run_all.py`` из корня репозитория.
Код возврата 0 только если нет ни одного FAIL.
"""
import subprocess
import sys
from pathlib import Path

AUDIT = Path(__file__).resolve().parent
NO_JDK = ("javac", "javap", "JAVA_HOME", "Java 21 required")
NO_PIL = ("PIL", "Pillow", "Image")


def classify(stderr: str) -> str:
    if any(m in stderr for m in NO_JDK):
        return "SKIP(no JDK)"
    if any(m in stderr for m in NO_PIL):
        return "SKIP(no Pillow)"
    return "FAIL"


def main() -> int:
    suites = sorted(p.name for p in AUDIT.glob("*_test.py"))
    fails, skips = [], []
    for name in suites:
        proc = subprocess.run([sys.executable, str(AUDIT / name)],
                              capture_output=True, text=True)
        if proc.returncode == 0:
            print(f"PASS  {name}")
        else:
            state = classify(proc.stderr)
            (skips if state.startswith("SKIP") else fails).append(name)
            print(f"{state.split('(')[0]:4}  {name}  [{state}]")
    print(f"\n{len(suites) - len(fails) - len(skips)} PASS, {len(skips)} SKIP, "
          f"{len(fails)} FAIL / {len(suites)}")
    if skips:
        print("SKIP: " + ", ".join(skips))
    if fails:
        print("FAIL: " + ", ".join(fails))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())

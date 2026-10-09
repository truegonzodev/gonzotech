"""Единая проверка версии мода для пинов (08.10.2026).

Точные пины ``mod_version=0.3.N`` гнили при каждом инкременте Z, из-за чего
гейт не мог быть зелёным уже с 0.3.166. Вместо «равно» используем «не ниже»:
фича-пьют заявляет минимальную версию, в которой патч применён, и остаётся
зелёным при любых последующих микропатчах.

Запуск сьютов идёт как ``python3 audit/<name>.py``, поэтому каталог ``audit``
лежит в ``sys.path`` и ``import _ver`` работает без установки пакета.
"""
from pathlib import Path


def version() -> str:
    root = Path(__file__).resolve().parent.parent
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    raise AssertionError("mod_version not found in gradle.properties")


def _tup(v: str):
    return tuple(int(p) for p in v.split("."))


def at_least(min_version: str, what: str) -> None:
    """Утверждает, что текущая ``mod_version`` не ниже ``min_version``."""
    cur = version()
    assert _tup(cur) >= _tup(min_version), (
        f"{what}: mod_version {cur} < {min_version}")

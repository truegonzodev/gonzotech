#!/usr/bin/env python3
"""Regression contracts for the reactor's saved-bounds/event lifecycle."""
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"

_ver.at_least("0.3.235", "reactor lifecycle uses persisted bounds and placement-only formation")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def method(source: str, signature: str) -> str:
    start = source.index(signature)
    body_start = source.index("{", start)
    depth = 0
    for index in range(body_start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


structure = read(JAVA / "machines/reactor/ReactorStructure.java")
panel_be = read(JAVA / "machines/block/entity/ReactorControlPanelBlockEntity.java")

# A formed structure indexes every position in its saved volume, rebuilt after load.
assert "MEMBER_INDEX" in structure and "Map<Long, BlockPos>" in structure
indexing = method(structure, "private static void indexStructure(")
assert "min.getX()" in indexing and "max.getX()" in indexing
assert "min.getY()" in indexing and "max.getY()" in indexing
assert "min.getZ()" in indexing and "max.getZ()" in indexing
assert "index.put(new BlockPos(x, y, z).asLong(), root)" in indexing
assert "indexStructure(level, panel.getBlockPos(), info.min(), info.max())" in method(
    structure, "private static void applyFormedVisuals(")
assert "StructureMin" in panel_be and "StructureMax" in panel_be
assert "public boolean contains(BlockPos pos)" in panel_be

# Formed checks use only the controller's saved box; neither a flood-fill nor fresh bounds can replace it.
formed_check = method(structure, "private static Validation revalidateFormedPanel(")
assert "BlockPos min = panel.min()" in formed_check
assert "BlockPos max = panel.max()" in formed_check
assert "validateBox(level, panel.getBlockPos(), new Bounds(min, max), ignoredPos)" in formed_check
assert "inspectFromPanel" not in formed_check
assert "boundsOf" not in formed_check

# A placement inside a saved member revalidates; placement outside the snapshot is ignored.
placement = method(structure, "private static void revalidateOrForm(")
assert "controllerAt(level, changed)" in placement
assert "if (panel.contains(changed))" in placement
assert "tryFormFromPlacement(level, panel, changed)" in placement
form_attempt = method(structure, "private static void tryFormFromPlacement(")
assert "inspectFromPanel(level, panel.getBlockPos(), null)" in form_attempt
assert "contains(validation.structure(), placedPos)" in form_attempt
assert "applyFormedVisuals(level, panel, validation.structure())" in form_attempt

# Breaking only invalidates an indexed formed structure; it cannot initialize an unformed one.
removed = method(structure, "public static void partRemoved(")
assert "invalidateAt(server, pos)" in removed
assert "revalidateOrForm" not in removed
invalidator = method(structure, "private static void invalidateAt(")
assert "revalidateFormedPanel(level, panel, member)" in invalidator
assert "tryFormFromPlacement" not in invalidator

# Loading and diagnostics do not silently form a valid but previously unformed structure.
restore = method(structure, "public static void restorePanel(")
assert "if (!panel.isFormedController())" in restore
assert "return;" in restore.split("if (!panel.isFormedController())", 1)[1].split("}", 1)[0]
assert "revalidateFormedPanel(level, panel, null)" in restore
assert "if (panel.isFormedController())" in method(structure, "public static void panelLoaded(")
report = method(structure, "public static List<Component> debugReportComponents(")
assert "inspectFromPanel(server, panelPos, null)" in report
assert "applyFormedVisuals" not in report
assert "tryFormFromPlacement" not in report

# Shell flood-fill cannot absorb a neighboring formed reactor's indexed members.
component = method(structure, "private static ComponentResult collectShellComponent(")
assert "controllerAt(level, next) != null" in component

print("reactor saved-bounds lifecycle static contracts passed")

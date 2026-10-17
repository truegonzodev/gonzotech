#!/usr/bin/env python3
"""Contracts for dry structural H/L diagnostics and per-sheath TVEL ownership."""
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"

_ver.at_least("0.3.238", "dry H/L matrix and fuel items owned by sheath block entities")


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


matrix = (JAVA / "machines/reactor/ReactorMatrix.java").read_text(encoding="utf-8")
engine = (JAVA / "machines/reactor/MatrixCoefficients.java").read_text(encoding="utf-8")
panel_be = (JAVA / "machines/block/entity/ReactorControlPanelBlockEntity.java").read_text(encoding="utf-8")
sheath_be = (JAVA / "machines/block/entity/ReactorFuelSheathBlockEntity.java").read_text(encoding="utf-8")
sheath_block = (JAVA / "core/block/ReactorFuelSheathBlock.java").read_text(encoding="utf-8")
storage = (JAVA / "machines/reactor/ReactorFuelStorage.java").read_text(encoding="utf-8")
inventory = (JAVA / "machines/reactor/ReactorFuelInventory.java").read_text(encoding="utf-8")
structure = (JAVA / "machines/reactor/ReactorStructure.java").read_text(encoding="utf-8")
panel_block = (JAVA / "core/block/ReactorControlPanelBlock.java").read_text(encoding="utf-8")

# Matrix reads actual saved interior blocks but deliberately never looks at the loaded fuel count.
evaluate = method(matrix, "public static Diagnostic evaluate(")
assert "panel.min()" in evaluate and "panel.max()" in evaluate
assert "max.getX() - min.getX() - 1" in evaluate
assert "level.getBlockState(worldPos)" in evaluate
assert "MatrixCoefficients.referenceLayout()" not in evaluate
assert "MatrixCoefficients.Material.GRAPHITE" in evaluate
assert "MatrixCoefficients.Material.BORON" in evaluate
assert "MatrixCoefficients.Material.BARIUM" in evaluate
assert "MatrixCoefficients.Material.WATER" in evaluate
assert "grid.set(x, y, z, MatrixCoefficients.Material.FUEL)" in evaluate
assert "loadedTvel" not in evaluate and "setFuelStrength" not in evaluate
assert "Per-cell model source weight" in engine
assert "fuelStrengths[i]=material==Material.FUEL?1.0:0.0" in engine

allowed_interior = method(structure, "private static boolean isAllowedInterior(")
assert "isLeadPiston" not in allowed_interior
assert "THIRD_LEAD_PISTON" not in structure and "THIRD_STICKY_LEAD_PISTON" not in structure
assert "THIRD_LEAD_PISTON_HEAD" not in structure
assert "THIRD_LEAD_PISTON" not in matrix and "THIRD_STICKY_LEAD_PISTON" not in matrix
assert "THIRD_LEAD_PISTON_HEAD" not in matrix

# New item storage lives in each sheath BE; the panel's Items tag is migration-only.
assert "implements Container" in sheath_be
assert "ContainerHelper.saveAllItems(tag, fuelItems, registries)" in sheath_be
assert "ContainerHelper.loadAllItems(tag, fuelItems, registries)" in sheath_be
assert "ReactorMin" in sheath_be and "ReactorMax" in sheath_be
assert "legacyFuelItems" in panel_be and "copyLegacyFuelItems()" in panel_be
assert "new ReactorFuelInventory(panel, activeFuelSlots)" in (JAVA / "machines/menu/ReactorMenu.java").read_text()
assert "int sheathIndex = slot % sheaths.size()" in inventory
assert "int localSlot = slot / sheaths.size()" in inventory
assert "collectSheaths(server, panel.min(), panel.max())" in inventory

# ceil(T/S) is debited from the group on sheath removal, then the remaining whole items are redistributed.
assert "ceilDivide(total, sheathCountBeforeBreak)" in storage
assert "remaining, peers, min, max" in storage
assert "Containers.dropItemStack" in storage
assert "canSafelyRemoveSheath" in storage
assert "beforeSheathRemoved(serverLevel, pos, sheath)" in sheath_block
break_handler = method(structure, "public static void onBlockBreak(")
assert "event.setCanceled(true)" in break_handler
assert "return;" in break_handler
assert "canOpenFuelInventory" in panel_block

# H/L are structural values; the report prints loading separately and labels both numbers dry.
report = method(structure, "public static String debugReport(")
assert "ReactorMatrix.evaluate(server, panel)" in report
assert "ReactorFuelStorage.totalTvels(server, panel)" in report
assert "сухой тепловой коэффициент" in report and "сухой пиковый поток утечки" in report
assert ' [H="' in report and ' [L="' in report
assert "Locale.ROOT" in report
styled = method(structure, "public static Component debugReportComponent(")
assert "ChatFormatting.GOLD" in styled and "ChatFormatting.AQUA" in styled
assert "ReactorStructure.debugReportComponent(level, pos)" in panel_block

print("PASS reactor matrix integration: dry H/L, live interior mapping, per-sheath fuel, ceil(T/S) break reallocation, lead pistons excluded")

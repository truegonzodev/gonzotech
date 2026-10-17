#!/usr/bin/env python3
"""Static contracts for live-interior coefficient calculation and panel diagnostics."""
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"

_ver.at_least("0.3.237", "live matrix coefficients use panel-loaded TVELs and exclude unsupported piston blocks")


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
structure = (JAVA / "machines/reactor/ReactorStructure.java").read_text(encoding="utf-8")
panel_block = (JAVA / "core/block/ReactorControlPanelBlock.java").read_text(encoding="utf-8")

# Uses the exact saved cubic bounds and reads each live block in the interior; no synthetic reference layout.
evaluate = method(matrix, "public static Diagnostic evaluate(")
assert "panel.min()" in evaluate and "panel.max()" in evaluate
assert "max.getX() - min.getX() - 1" in evaluate
assert "level.getBlockState(worldPos)" in evaluate
assert "MatrixCoefficients.referenceLayout()" not in evaluate
assert "MatrixCoefficients.Material.GRAPHITE" in evaluate
assert "MatrixCoefficients.Material.BORON" in evaluate
assert "MatrixCoefficients.Material.BARIUM" in evaluate
assert "MatrixCoefficients.Material.WATER" in evaluate
assert "THIRD_URANIUM_REACTOR_FUEL_SHEATH" in evaluate
allowed_interior = method(structure, "private static boolean isAllowedInterior(")
assert "isLeadPiston" not in allowed_interior
assert "THIRD_LEAD_PISTON" not in structure and "THIRD_STICKY_LEAD_PISTON" not in structure
assert "THIRD_LEAD_PISTON_HEAD" not in structure
assert "THIRD_LEAD_PISTON" not in matrix and "THIRD_STICKY_LEAD_PISTON" not in matrix
assert "THIRD_LEAD_PISTON_HEAD" not in matrix
assert "(double) loadedTvels / fuelSheaths" in evaluate
assert "grid.setFuelStrength(x, y, z, tvelsPerSheath)" in evaluate

# TVEL count comes from every stored panel-container slot, including temporarily hidden slots.
count = method(panel_be, "public int loadedTvelCount(")
assert "for (ItemStack stack : fuelItems)" in count
assert "stack.is(ModItems.UF_TVEL.get())" in count and "stack.getCount()" in count
assert "fuelSlotCount()" not in count

# A sheath may emit fractional/zero source units without changing the archive's positive Rules input.
assert "private final double[] fuelStrengths" in engine
assert "public Grid setFuelStrength(int x,int y,int z,double strength)" in engine
assert "r.sourcePerFuel*g.fuelStrengths[i]" in engine
assert "if(emitted==0)return new Raw" in engine
assert "sourcePerFuel" in engine and "new Rules(1" in engine

report = method(structure, "public static String debugReport(")
assert "inspectFromPanel(server, panelPos, null)" in report
assert "ReactorMatrix.evaluate(server, panel)" in report
assert ' [H="' in report and ' [L="' in report
assert "Locale.ROOT" in report
assert "Матрица коэффициентов не рассчитана" in report
styled = method(structure, "public static Component debugReportComponent(")
assert "ChatFormatting.GOLD" in styled and "ChatFormatting.AQUA" in styled
assert "ReactorStructure.debugReportComponent(level, pos)" in panel_block
assert "player.isShiftKeyDown()" in panel_block

print("PASS reactor matrix integration: saved interior, fractional source distribution, lead pistons excluded from structures, highlighted H/L diagnostics")

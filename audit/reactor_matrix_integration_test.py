#!/usr/bin/env python3
"""Contracts for cached ReaCalc 1.2 operating metrics and per-sheath TVEL ownership."""
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
operating = (JAVA / "machines/reactor/ReactorOperatingMetrics.java").read_text(encoding="utf-8")
panel_be = (JAVA / "machines/block/entity/ReactorControlPanelBlockEntity.java").read_text(encoding="utf-8")
sheath_be = (JAVA / "machines/block/entity/ReactorFuelSheathBlockEntity.java").read_text(encoding="utf-8")
sheath_block = (JAVA / "core/block/ReactorFuelSheathBlock.java").read_text(encoding="utf-8")
storage = (JAVA / "machines/reactor/ReactorFuelStorage.java").read_text(encoding="utf-8")
inventory = (JAVA / "machines/reactor/ReactorFuelInventory.java").read_text(encoding="utf-8")
structure = (JAVA / "machines/reactor/ReactorStructure.java").read_text(encoding="utf-8")
panel_block = (JAVA / "core/block/ReactorControlPanelBlock.java").read_text(encoding="utf-8")

# The dry solve reads actual interior blocks once; loaded metrics reuse its raw per-cell outputs.
evaluate = method(matrix, "public static Diagnostic evaluate(")
calculate = method(matrix, "private static Diagnostic calculate(")
assert "CACHE" in evaluate and "calculate(level, panel)" in evaluate
assert "panel.min()" in calculate and "panel.max()" in calculate
assert "max.getX() - min.getX() - 1" in calculate
assert "level.getBlockState(worldPos)" in calculate
assert "MatrixCoefficients.referenceLayout()" not in calculate
assert "MatrixCoefficients.Material.GRAPHITE" in calculate
assert "MatrixCoefficients.Material.BORON" in calculate
assert "MatrixCoefficients.Material.BARIUM" in calculate
assert "MatrixCoefficients.Material.WATER" in calculate
assert "grid.set(x, y, z, MatrixCoefficients.Material.FUEL)" in calculate
assert "loadedTvel" not in calculate and "setFuelStrength" not in calculate
assert "ENGINE.analyzeDetailed(grid)" in calculate
assert "new WeakHashMap" in matrix and "loadedTvels() == loadedTvels" in matrix
assert "ReactorOperatingMetrics.calculate" in matrix
assert "public static void invalidate(" in matrix
assert "Per-cell model source weight" in engine
assert "fuelStrengths[i]=material==Material.FUEL?1.0:0.0" in engine
assert "POWER_SCALE = 100.0" in operating
assert "TARGET_H = .99999" in operating
assert "TEMP_SCALE = 2838.43365245" in operating
assert "TEMP_EXPONENT = 1.37798040865" in operating
assert "scaledLeak(double rawL, double ratio)" in operating
assert "step < 80" in operating and "WATER_REMOVAL * units" in operating
assert "Pнагрева" in structure and "Pполезный" in structure
assert "ChatFormatting.RED" in structure and "ChatFormatting.GREEN" in structure

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
assert "sheathsCached(server, panel)" in inventory

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

# Shift+ПКМ sends the complete static operating report as separate, styled chat lines.
report = method(structure, "public static List<Component> debugReportComponents(")
assert "ReactorMatrix.operatingSnapshot(server, panel)" in report
assert "ReactorOperatingMetrics.Values" in report
assert "rawH=" in report and "rawL=" in report and "Htv=" in report and "Ltv=" in report and "S=" in report
assert "Целевой rawH*=" in report and "ΔH=" in report
assert "Средний Htv=" in report and "T горячей точки=" in report
assert "T топлива max=" in report and "T реактора средняя=" in report
assert "qDry=" in report and "Pнагрева=" in report
assert "W=" in report and "Tw=" in report and "p=" in report and "feedback=" in report
assert "Pтеплообмен=" in report and "Pполезный=" in report and "Эффективность=" in report and "Режим=" in report
assert "Статический расчёт ReaCalc 1.2" in report
plain = method(structure, "public static String debugReport(")
assert "debugReportComponents(level, panelPos)" in plain
assert "MutableComponent combined = Component.empty()" in structure
assert "MutableComponent line = Component.empty()" in structure
assert "Locale.ROOT" in structure
styled = method(structure, "private static Component stat(String label, String value,")
assert "withStyle(color)" in styled
assert "ChatFormatting.RED" in report and "ChatFormatting.GREEN" in report
assert "for (net.minecraft.network.chat.Component line : ReactorStructure.debugReportComponents(level, pos))" in panel_block
assert "player.displayClientMessage(line, false)" in panel_block
assert "ReactorMatrix.invalidate(panel)" in structure and "ReactorMatrix.clearCache()" in structure

print("PASS reactor operating metrics: ReaCalc 1.2 parity layer, cached dry solve, complete styled Shift+ПКМ report, per-sheath fuel, lead pistons excluded")

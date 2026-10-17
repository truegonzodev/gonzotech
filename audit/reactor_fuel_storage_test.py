#!/usr/bin/env python3
"""Regression contracts for per-sheath fuel ownership and lossless break rounding."""
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"

_ver.at_least("0.3.238", "TVEL inventory belongs to sheath BEs; break rounding consumes shares once")


def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


sheath_be = read("machines/block/entity/ReactorFuelSheathBlockEntity.java")
sheath_block = read("core/block/ReactorFuelSheathBlock.java")
panel_be = read("machines/block/entity/ReactorControlPanelBlockEntity.java")
panel_block = read("core/block/ReactorControlPanelBlock.java")
storage = read("machines/reactor/ReactorFuelStorage.java")
inventory = read("machines/reactor/ReactorFuelInventory.java")
structure = read("machines/reactor/ReactorStructure.java")

# Persistence is on each sheath; panel's old inventory remains solely for safe world-upgrade migration.
assert "implements Container" in sheath_be
assert "ContainerHelper.saveAllItems(tag, fuelItems, registries)" in sheath_be
assert "ContainerHelper.loadAllItems(tag, fuelItems, registries)" in sheath_be
assert "boolean stillValid(Player player)" in sheath_be
assert "Container.stillValidBlockEntity(this, player)" in sheath_be
assert "private final NonNullList<ItemStack> fuelItems" in sheath_be
assert "legacyFuelItems" in panel_be
assert "copyLegacyFuelItems()" in panel_be and "clearLegacyFuelItems()" in panel_be
assert "bindStructure" in structure and "panel.copyLegacyFuelItems()" in structure
assert "if (info.sheaths() > 0 && storedFuel >= 0) panel.clearLegacyFuelItems()" in structure

# Normal GUI slots route directly to individual sheath containers, not panel NBT.
assert "new ReactorFuelInventory(panel, activeFuelSlots)" in read("machines/menu/ReactorMenu.java")
assert "int sheathIndex = slot % sheaths.size()" in inventory
assert "int localSlot = slot / sheaths.size()" in inventory
assert "sheaths.get(address.sheathIndex()).setItem(address.localSlot(), stack)" in inventory
assert "Containers.dropContents(level, pos, panel)" in panel_block  # only legacy compatibility contents
assert "beforeSheathRemoved(serverLevel, pos, sheath)" in sheath_block

# Breaking one share rounds T/S upward, removes exactly that many real items, then evenly reassigns survivors.
assert "int dropCount = total == 0 ? 0 : Math.min(total, ceilDivide(total, sheathCountBeforeBreak))" in storage
assert "private static int ceilDivide(int numerator, int denominator)" in storage
assert "remaining, peers, min, max" in storage
assert "removed.clearContent()" in storage
assert "dropItems(level, pos, dropped)" in storage
assert "ReactorMin" in sheath_be and "ReactorMax" in sheath_be
assert "chunksLoaded(level, sheath.reactorMin(), sheath.reactorMax())" in storage
assert "event.setCanceled(true)" in structure

# Small integer simulation mirrors the implementation's ceil(T/S) bookkeeping and proves no over-drop.
def ceil_div(t: int, s: int) -> int:
    return t // s + (1 if t % s else 0)


def remove_shares(total: int, sheaths: int) -> list[int]:
    drops = []
    while sheaths > 0:
        dropped = min(total, ceil_div(total, sheaths)) if total else 0
        drops.append(dropped)
        total -= dropped
        sheaths -= 1
    assert total == 0
    return drops

assert remove_shares(1, 10) == [1] + [0] * 9
assert remove_shares(18, 10) == [2] * 8 + [1, 1]
for total in range(19):
    for sheaths in range(1, 40):
        drops = remove_shares(total, sheaths)
        assert sum(drops) == total, (total, sheaths, drops)
        assert all(0 <= dropped <= total for dropped in drops)

print("PASS reactor fuel storage: sheath-owned NBT, legacy migration, panel proxy, ceil(T/S) and no-duplication cases")

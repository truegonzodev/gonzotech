#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Nuclear firebox fuel-duration and per-item GTH-yield contract (0.3.143)."""
import ast
import operator
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def read(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


def pin(condition, message):
    if not condition:
        raise AssertionError(message)


def expression(source, name):
    match = re.search(rf"public static final int {name}\s*=\s*([^;]+);", source)
    assert match, f"missing {name}"
    return " ".join(match.group(1).split())


def int_constant(source, name):
    value = expression(source, name)
    match = re.fullmatch(r"([0-9_]+)", value)
    assert match, f"{name} is not a literal integer: {value}"
    return int(match.group(1).replace("_", ""))


def java_int_constant(source, name, known):
    """Evaluate the small arithmetic subset used by these Java int constants."""
    tree = ast.parse(expression(source, name).replace("/", "//"), mode="eval")
    operations = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.FloorDiv: operator.floordiv,
    }

    def visit(node):
        if isinstance(node, ast.Constant) and type(node.value) is int:
            return node.value
        if isinstance(node, ast.Name) and node.id in known:
            return known[node.id]
        if isinstance(node, ast.BinOp) and type(node.op) in operations:
            return operations[type(node.op)](visit(node.left), visit(node.right))
        raise AssertionError(f"unsupported Java int expression in {name}: {ast.dump(node)}")

    return visit(tree.body)


nuclear = read("src/main/java/com/gonzotech/machines/energy/NuclearDefs.java")
firebox = read("src/main/java/com/gonzotech/machines/block/entity/NuclearFireboxBlockEntity.java")
version = read("gradle.properties")
pin("mod_version=0.3.161" in version, "micropatch version should be 0.3.161")

# User-selected ingot baselines and the old per-form ratios, rounded to the
# nearest 20-Hz game tick when a ratio is not an integral tick count.
pin(expression(nuclear, "URANIUM_INGOT_BURN_TICKS") == "130 * TICKS_PER_SECOND",
    "uranium ingot baseline should be 130 seconds")
pin(expression(nuclear, "URANIUM_NUGGET_BURN_TICKS") == "(URANIUM_INGOT_BURN_TICKS + 4) / 9",
    "uranium nugget should retain the old 1/9 coefficient, rounded to a tick")
pin(expression(nuclear, "URANIUM_BLOCK_BURN_TICKS") == "9 * URANIUM_INGOT_BURN_TICKS",
    "uranium block should retain the old 9x coefficient")
pin(expression(nuclear, "URANINITE_BURN_TICKS") == "(5 * URANIUM_INGOT_BURN_TICKS + 4) / 9",
    "uraninite should retain the old 5/9 coefficient, rounded to a tick")
pin(expression(nuclear, "URANIUM_DUST_BURN_TICKS") == "URANIUM_INGOT_BURN_TICKS",
    "uranium dust should burn like its ingot")

pin(expression(nuclear, "THORIUM_INGOT_BURN_TICKS") == "40 * TICKS_PER_SECOND",
    "thorium ingot baseline should be 40 seconds")
pin(expression(nuclear, "THORIUM_NUGGET_BURN_TICKS") == "(7 * THORIUM_INGOT_BURN_TICKS + 30) / 60",
    "thorium nugget should retain the old 7/60 coefficient, rounded to a tick")
pin(expression(nuclear, "THORIUM_BLOCK_BURN_TICKS") == "9 * THORIUM_INGOT_BURN_TICKS",
    "thorium block should retain the old 9x coefficient")
pin(expression(nuclear, "THORIANITE_BURN_TICKS") == "(7 * THORIUM_INGOT_BURN_TICKS + 6) / 12",
    "thorianite should retain the old 7/12 coefficient, rounded to a tick")
pin(expression(nuclear, "THORIUM_DUST_BURN_TICKS") == "THORIUM_INGOT_BURN_TICKS",
    "thorium dust should burn like its ingot")

ticks_per_second = int_constant(nuclear, "TICKS_PER_SECOND")
# The stored constant is mGTH/t: 116 GTH/t multiplied by MachineDefs.MILLI.
pin("NUCLEAR_FIREBOX_GTH_PER_TICK = 116 * MachineDefs.MILLI;" in nuclear,
    "nuclear firebox heat rate changed; recalculate every table entry")
gth_per_tick = 116
pin("NUCLEAR_FIREBOX_GTH_LOSS = 200;" in nuclear,
    "0.2 GTH/t firebox loss changed; update net-yield caveat")

uranium_ingot = 130 * ticks_per_second
uranium = {
    "URANIUM_INGOT_BURN_TICKS": uranium_ingot,
    "URANIUM_NUGGET_BURN_TICKS": (uranium_ingot + 4) // 9,
    "URANIUM_BLOCK_BURN_TICKS": 9 * uranium_ingot,
    "URANINITE_BURN_TICKS": (5 * uranium_ingot + 4) // 9,
    "URANIUM_DUST_BURN_TICKS": uranium_ingot,
}
thorium_ingot = 40 * ticks_per_second
thorium = {
    "THORIUM_INGOT_BURN_TICKS": thorium_ingot,
    "THORIUM_NUGGET_BURN_TICKS": (7 * thorium_ingot + 30) // 60,
    "THORIUM_BLOCK_BURN_TICKS": 9 * thorium_ingot,
    "THORIANITE_BURN_TICKS": (7 * thorium_ingot + 6) // 12,
    "THORIUM_DUST_BURN_TICKS": thorium_ingot,
}
# Verify that Java's actual constant expressions evaluate to the independently
# calculated 20-Hz tick counts (using Java-style integer division).
resolved = {"TICKS_PER_SECOND": ticks_per_second}
for name, expected in {**uranium, **thorium}.items():
    actual = java_int_constant(nuclear, name, resolved)
    pin(actual == expected, f"{name} tick calculation mismatch: {actual} != {expected}")
    resolved[name] = actual

# The 116 GTH/t generation is duration-independent; high buffer shortens a
# fuel item to 75%, with integer tick truncation in adjustedBurnTicks.
reduction_permille = int_constant(nuclear, "NUCLEAR_FIREBOX_MAX_BURN_REDUCTION_PERMILLE")
pin(reduction_permille == 250, "maximum acceleration should remain 25%")
assert "be.litDuration = be.adjustedBurnTicks(baseBurn);" in firebox
assert "be.gth.receive(NuclearDefs.NUCLEAR_FIREBOX_GTH_PER_TICK, false);" in firebox
assert "(long) baseTicks * (1_000L - reduction) / 1_000L" in firebox
assert "public static int burnTicks(ItemStack stack)" in firebox
for item_key in (
    '"uranium_ingot"', '"uranium_nugget"', '"uranium_block"', '"uranium_dust"',
    '"uranium"', '"thorium_ingot"', '"thorium_nugget"', '"thorium_block"',
    '"thorium_dust"', '"thorium"',
):
    pin(item_key in firebox, f"accepted fuel form missing: {item_key}")

expected_gross = {
    "URANIUM_INGOT_BURN_TICKS": 301_600,
    "URANIUM_NUGGET_BURN_TICKS": 33_524,
    "URANIUM_BLOCK_BURN_TICKS": 2_714_400,
    "URANINITE_BURN_TICKS": 167_504,
    "URANIUM_DUST_BURN_TICKS": 301_600,
    "THORIUM_INGOT_BURN_TICKS": 92_800,
    "THORIUM_NUGGET_BURN_TICKS": 10_788,
    "THORIUM_BLOCK_BURN_TICKS": 835_200,
    "THORIANITE_BURN_TICKS": 54_172,
    "THORIUM_DUST_BURN_TICKS": 92_800,
}
expected_full_buffer = {
    "URANIUM_INGOT_BURN_TICKS": (1_950, 226_200),
    "URANIUM_NUGGET_BURN_TICKS": (216, 25_056),
    "URANIUM_BLOCK_BURN_TICKS": (17_550, 2_035_800),
    "URANINITE_BURN_TICKS": (1_083, 125_628),
    "URANIUM_DUST_BURN_TICKS": (1_950, 226_200),
    "THORIUM_INGOT_BURN_TICKS": (600, 69_600),
    "THORIUM_NUGGET_BURN_TICKS": (69, 8_004),
    "THORIUM_BLOCK_BURN_TICKS": (5_400, 626_400),
    "THORIANITE_BURN_TICKS": (350, 40_600),
    "THORIUM_DUST_BURN_TICKS": (600, 69_600),
}
for name, base_ticks in {**uranium, **thorium}.items():
    gross = base_ticks * gth_per_tick
    fast_ticks = base_ticks * (1_000 - reduction_permille) // 1_000
    fast_gross = fast_ticks * gth_per_tick
    pin(gross == expected_gross[name], f"gross baseline yield mismatch for {name}")
    pin((fast_ticks, fast_gross) == expected_full_buffer[name],
        f"gross full-buffer yield mismatch for {name}")

print("OK: nuclear fuel baseline times, retained ratios, acceleration, and GTH/item yields")

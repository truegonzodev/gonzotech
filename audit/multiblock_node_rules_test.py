#!/usr/bin/env python3
"""Static contracts: правила узлов мультиблоков (0.3.255, автор).

Правило: для парогена, турбины и доменной печи подходят ВСЕ поколения нужных
узлов (любой универсальный узел заменяет любой другой универсальный; тир-99
подойдёт автоматически). Требования к количеству ключевых узлов не меняются
(минимум 1 универсальный; либо 1 ГТХ + 1 вода + 1 пар; либо 1 ГТХ + 1
универсальная жидкость). Реактор — только экранированные (III поколение).
"""
import re
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"


def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


kinds = read("machines/network/NodeKinds.java")
blast = read("machines/blastfurnace/BlastFurnaceStructure.java")
steamgen = read("machines/steamgen/SteamGenStructure.java")
turbine = read("machines/turbine/TurbineStructure.java")
reactor = read("machines/reactor/ReactorStructure.java")
machines = read("machines/registry/ModMachines.java")

# ── классификатор: тип по суффиксу пути реестра, ЛЮБОЕ поколение ──
for suffix in ('"_heat_node"', '"_wire_node"', '"_water_node"', '"_steam_node"',
               '"_universal_fluid_node"', '"_universal_node"', '"_item_node"'):
    assert suffix in kinds, suffix
assert "GonzoTechMod.MOD_ID" in kinds  # только узлы gonzotech
assert "isUniversalFluid(block)" in kinds  # universal_fluid не считается universal
assert "public static boolean isHeat(BlockState state)" in kinds
assert "public static boolean isUniversal(BlockState state)" in kinds

# ── все зарегистрированные узлы классифицируются ровно одним типом ──
node_ids = sorted(set(re.findall(r'registerBlock\("(\w+_node)"', machines)))
assert len(node_ids) >= 12, node_ids
kinds_suffixes = ["_universal_fluid_node", "_universal_node", "_heat_node",
                  "_wire_node", "_water_node", "_steam_node", "_item_node"]
for node_id in node_ids:
    matches = [s for s in kinds_suffixes if node_id.endswith(s)]
    assert len(matches) == 1, f"{node_id}: {matches}"

# ── доменная печь: теплоузел/универсал ЛЮБОГО поколения (экраны III поколения тоже) ──
assert "case HEAT_NODE -> NodeKinds.isHeat(state) || NodeKinds.isUniversal(state);" in blast
assert "SECOND_HEAT_NODE" not in blast  # перечисления по поколениям больше нет

# ── пароген: порты любого поколения, требования и лимит узлов сохранены ──
assert steamgen.count("NodeKinds.isSteam(block)") == 1
assert steamgen.count("NodeKinds.isWater(block)") == 1
assert steamgen.count("NodeKinds.isHeat(block)") == 1
assert steamgen.count("NodeKinds.isUniversalFluid(block)") == 2  # пар+вода
assert steamgen.count("NodeKinds.isUniversal(block)") == 3       # пар+вода+тепло
assert "SECOND_STEAM_NODE.get()" not in steamgen
# ключевые требования на месте: >=1 пар, >=1 вода, >=1 тепло (уни закрывает все три)
assert "if (cores < 1 || steam.isEmpty() || water.isEmpty() || heat.isEmpty()) return null;" in steamgen
assert "MAX_NODES_PER_STRUCTURE" in steamgen  # лимит узлов в обшивке не тронут

# ── турбина: пар + провод любого поколения, >=1 пар и >=1 провод сохранены ──
assert "NodeKinds.isSteam(block)" in turbine and "NodeKinds.isUniversalFluid(block)" in turbine
assert "NodeKinds.isWire(block)" in turbine and "NodeKinds.isUniversal(block)" in turbine
assert "SECOND_WIRE_NODE.get()" not in turbine and "SECOND_STEAM_NODE.get()" not in turbine
assert "if (steam.isEmpty() || wire.isEmpty()) return null;" in turbine

# ── реактор: ТОЛЬКО экранированные (III поколение) — правило не тронуто ──
assert "THIRD_HEAT_NODE.get()" in reactor
assert "THIRD_UNIVERSAL_NODE.get()" in reactor
assert "THIRD_UNIVERSAL_FLUID_NODE.get()" in reactor
assert "NodeKinds" not in reactor
assert "нужен один экранированный универсальный узел либо одновременно узел ГТХ и узел мультижидкости" in reactor

assert _ver.version() == "0.3.259", _ver.version()
print(f"multiblock node rules OK: {len(node_ids)} node ids, 3 multiblocks open, reactor shielded-only")

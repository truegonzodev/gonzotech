"""0.3.71: узлы дороже не делать обшивкой — лимиты узлов на структуру (авторские числа).

Турбина <= 8 узлов, парогенератор <= 10. Узлы дешевле корпуса, поэтому без
лимита всю обшивку выгодно собрать из узлов; универсальный узел закрывает
все типы портов сразу, так что счёт идёт по ПОЗИЦИЯМ, а не по ролям.
Доменную печь не трогаем (узлы — неотъемлемая часть невариативной
конструкции, ровно 4), у литографа узлов нет вовсе.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

turbine = (ROOT / "src/main/java/com/gonzotech/machines/turbine/TurbineStructure.java").read_text()
steamgen = (ROOT / "src/main/java/com/gonzotech/machines/steamgen/SteamGenStructure.java").read_text()
blast = (ROOT / "src/main/java/com/gonzotech/machines/blastfurnace/BlastFurnaceStructure.java").read_text()

# Авторские числа.
assert "MAX_NODES_PER_STRUCTURE = 8" in turbine
assert "MAX_NODES_PER_STRUCTURE = 10" in steamgen

# Счёт по позициям обшивки (универсальный узел не задваивается).
assert "if (isSteamPort(state) || isWirePort(state)) nodes++;" in turbine
assert "if (isSteamPort(state) || isWaterPort(state) || isHeatPort(state)) nodes++;" in steamgen

# Гейт в валидаторе срабатывает для обоих путей сборки: validateBox зовётся
# и из tryFormFrom, и из restoreController (восстановление после загрузки).
assert "if (nodes > MAX_NODES_PER_STRUCTURE) return null;" in turbine
assert "if (nodes > MAX_NODES_PER_STRUCTURE) return null;" in steamgen

# Минимум портов не смягчён: легитимные сборки (>=1 пар + >=1 провод и т.п.) живут.
assert "if (steam.isEmpty() || wire.isEmpty()) return null;" in turbine
assert "if (cores < 1 || steam.isEmpty() || water.isEmpty() || heat.isEmpty()) return null;" in steamgen

# Скоуп автора: доменная печь — лимит не нужен (ровно 4 узла по Layout).
assert "MAX_NODES" not in blast

# ── 0.3.103: порты НЕ часть компонента, флуд сквозь них не идёт ──
# (узел/кламп, прислонённый снаружи к обшивке, раздувал компонент — сборка
# ломалась; порты в плоскости обшивки валидируются по коробке в validateBox)
assert "if (!isPortState(level.getBlockState(current))) {" in turbine
assert "if (!next.equals(seed) && isPortState(level.getBlockState(next))) continue;" in turbine
assert "private static boolean isPortState(BlockState state) {" in turbine
assert "return isSteamPort(state) || isWirePort(state);" in turbine
assert "if (!isPortState(level.getBlockState(current))) {" in steamgen
assert "if (!next.equals(seed) && isPortState(level.getBlockState(next))) continue;" in steamgen
assert "return isSteamPort(state) || isWaterPort(state) || isHeatPort(state);" in steamgen

print("node cap wiring passed (turbine<=8, steamgen<=10, ports out of flood)")

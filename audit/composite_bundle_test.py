#!/usr/bin/env python3
"""
Пучок труб (0.3.94, репорт автора: «трубы перестали собираться в composite»).

Статически все гейты сборки пучка верны (проверено чтением + историей:
PipeBlock менялся только в 0.3.73 — FormedMenus-диспетчер, чисто). Поэтому
0.3.94 добавляет ГРОМКУЮ диагностику: при пропуске ветки пучка сервер логирует
точную причину (см. CompositePipeBlock.logBundleSkip). Сьют пинчит
диагностику, мелкий визуальный фикс AXIS_LOWER и неизменность условий веток.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NET = ROOT / "src/main/java/com/gonzotech/machines/network"

pipe = (NET / "PipeBlock.java").read_text()
ufp = (NET / "UniversalFluidPipeBlock.java").read_text()
comp = (NET / "CompositePipeBlock.java").read_text()
access = (NET / "ModCompositeAccess.java").read_text()

# ── Диагностика 0.3.94 ──
assert "logBundleSkip" in comp and "logBundleDone" in comp
assert "private static final Map<UUID, Long> BUNDLE_LOG_AT = new HashMap<>();" in comp
assert "now - last < 40L" in comp                    # антиспам ~2 с
assert "BUNDLE_LOG_AT.size() > 64" in comp           # чистка в методе записи
assert "level.isClientSide() || held.isEmpty()" in comp  # только сервер
# полный набор причин — молчать нельзя ни в одном случае
for reason in ("CLICKED_IS_NODE", "SAME_TYPE", "FLUID_CORNER_CLASH",
               "NO_COMPOSITE_REGISTERED", "TIER_MISMATCH",
               "CONDITIONS_PASS_BUT_SKIPPED"):
    assert reason in comp, reason
# вызовы: хвосты PipeBlock и UniversalFluidPipeBlock
assert pipe.count("CompositePipeBlock.logBundleSkip(level, player, this, stack)") == 1
assert ufp.count("CompositePipeBlock.logBundleSkip(level, player, this, stack)") == 1
assert "logBundleDone(player, \"universal-fluid corner added\")" in pipe
assert "logBundleDone(player, \"types \" + this.pipeType + \"+\" + adding)" in pipe
assert "logBundleDone(player, \"energy into universal-fluid bundle (\" + adding + \")\")" in ufp

# ── Визуальный фикс 0.3.94: ось нижнего слоя в универсальной ветке ──
assert ".setValue(CompositePipeBlock.AXIS_LOWER, axis)" in ufp

# ── Неизменность условий веток (семантика сборки) ──
assert "adding != null && adding != this.pipeType && !fluidClash" in pipe
assert "!connectsAllSides() && CompositePipeBlock.isUniversalPipeItem(stack)" in pipe
assert "adding != null && !adding.isFluid()" in ufp
assert "ModCompositeAccess.sameTier(this, stack)" in pipe and "ModCompositeAccess.sameTier(this, stack)" in ufp
assert "PipeType.WIRE, PipeType.HEAT, PipeType.WATER, PipeType.STEAM, PipeType.ITEM" in comp
# тир через интерфейс, у бандлов свои блоки
assert "getFor(PipeBlock pipe)" in access and "sameTier(PipeBlock existing" in access
assert "instanceof SecondTierPipe ? secondComposite : composite" in access

print("composite bundle pins passed (diagnostics + AXIS_LOWER fix + branch semantics)")

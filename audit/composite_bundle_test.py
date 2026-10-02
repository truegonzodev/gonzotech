#!/usr/bin/env python3
"""
Пучок труб (0.3.95).

0.3.93-эпоха: автор поймал КРАШ клиента при наведении ключом на пучок
(WrenchHud.aimedPart перебирал ВСЕ PipeType.values(): у жидкостей Эпохи III
нет PRESENT/MODE-проперти → state.getValue(null)). Тайна «пучки перестали
собираться» закрыта: 0.3.90–0.3.92 не компилировались (старая джарка у
автора), тир-микс и вода+пар — by design. Временная диагностика 0.3.94
снята (автор подтвердил: пучки собираются).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NET = ROOT / "src/main/java/com/gonzotech/machines/network"
HUD = ROOT / "src/main/java/com/gonzotech/machines/client/WrenchHud.java"

pipe = (NET / "PipeBlock.java").read_text()
ufp = (NET / "UniversalFluidPipeBlock.java").read_text()
comp = (NET / "CompositePipeBlock.java").read_text()
access = (NET / "ModCompositeAccess.java").read_text()
hud = HUD.read_text()

# ── ФИКС КРАША 0.3.95 (WrenchHud) ──
assert "for (PipeType t : CompositePipeBlock.BUNDLE_TYPES) {" in hud
assert "state.getValue(CompositePipeBlock.PRESENT.get(t))) present.add(t);" in hud
# в aimedPart больше нет полного перебора по композиту
assert "instanceof CompositePipeBlock) {\n            List<PipeType> present" in hud
# modeOf: страховка null-проперти + режим флюидного угла (вода → пар → AUTO)
assert "var prop = CompositePipeBlock.MODE.get(part);" in hud
assert "if (prop != null) return state.getValue(prop);" in hud
assert "MODE.get(PipeType.WATER) != null" in hud and "MODE.get(PipeType.STEAM) != null" in hud
# полный PipeType.values() в HUD остался только в carriedParts (без getValue — безопасно)
assert hud.count("List.of(PipeType.values())") == 1      # universal-node carriedParts
assert hud.count("for (PipeType t : PipeType.values()) {") == 1  # список всех флюидов (без getValue)

# ── Диагностика 0.3.94 снята ──
for f in (pipe, ufp, comp):
    assert "logBundle" not in f, "временная диагностика должна быть снята"
assert "BUNDLE_LOG" not in comp and "UUID" not in comp

# ── Визуальный фикс 0.3.94: ось нижнего слоя в универсальной ветке ──
assert ".setValue(CompositePipeBlock.AXIS_LOWER, axis)" in ufp

# ── Неизменность условий веток (семантика сборки) ──
assert "adding != null && adding != this.pipeType && !fluidClash" in pipe
assert "!connectsAllSides() && CompositePipeBlock.isUniversalPipeItem(stack)" in pipe
assert "adding != null && !adding.isFluid()" in ufp
assert "ModCompositeAccess.sameTier(this, stack)" in pipe and "ModCompositeAccess.sameTier(this, stack)" in ufp
assert "PipeType.WIRE, PipeType.HEAT, PipeType.WATER, PipeType.STEAM, PipeType.ITEM" in comp
assert "getFor(PipeBlock pipe)" in access and "sameTier(PipeBlock existing" in access
assert "instanceof SecondTierPipe ? secondComposite : composite" in access

print("composite bundle pins passed (crash fix + diagnostics removed + AXIS_LOWER + semantics)")

#!/usr/bin/env python3
"""
0.3.98: сшитые узлы (проект автора «смежные узлы = единая структура»).
Кламп: same-kind+same-tier смежные узлы; границы капируют реальные трубы,
внутри безлимит ×N; плоская потеря максимум-направления (1×1×3 → 0.66 GTH);
инвалидация по множеству (поршневой киллсвитч бесплатен); вспышка при слиянии
(только на росте); HUD «сшито узлов: N».
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NET = ROOT / "src/main/java/com/gonzotech/machines/network"
HUD = ROOT / "src/main/java/com/gonzotech/machines/client/WrenchHud.java"

idx = (NET / "NodeClumpIndex.java").read_text()
node = (NET / "NodeBlock.java").read_text()
uni = (NET / "UniversalNodeBlock.java").read_text()
routing = (NET / "PipeRouting.java").read_text()
hud = HUD.read_text()
flow = (NET / "PipeFlowNetwork.java").read_text()

# ── Реестр ──
# 0.3.100: слияние — флуд по СОСТОЯНИЯМ (индекс не видел одиночек → фича не работала)
assert "public static void onNodeChanged(Level level, BlockPos pos)" in idx
assert 'kind.equals(kindOf(level.getBlockState(next)))' in idx
assert "Deque<BlockPos> queue = new ArrayDeque<>();" in idx
assert "record Clump(long root, long lossMilli, Set<Long> members)" in idx
# 0.3.101: инвариант непересечения (лечит «экспоненциальный» HUD-счётчик автора)
assert "if (oldRoot != null && oldRoot != root) byRoot.remove(oldRoot);" in idx
# 0.3.101: уборка устаревших корней (живой кламп отображает корень в себя)
assert "self == null || self != r" in idx
# 0.3.101: визуал убран (вспышка + HUD-счётчик + sizeAt) — мёртвого кода нет
assert "sendParticles" not in idx and "DustParticleOptions" not in idx
assert "sizeAt" not in idx and "int size," not in idx
assert "hud.gonzotech.wrench_clump" not in hud and "clumpSize" not in hud
assert "clumpSize" not in flow
for lang in ("en_us.json", "ru_ru.json"):
    lang_src = (ROOT / "src/main/resources/assets/gonzotech/lang" / lang).read_text()
    assert "wrench_clump" not in lang_src, lang
assert "MEMBER_ROOT = new IdentityHashMap<>()" in idx and "BY_ROOT = new IdentityHashMap<>()" in idx
assert "public static void clearAll()" in idx
# род: уни ("U1"/"U2") и узлы одного типа+тира ("N1:HEAT"/"N2:HEAT"), ничего больше
assert 'b instanceof SecondTierPipe ? "U2" : "U1"' in idx
assert '(b instanceof SecondTierPipe ? "N2:" : "N1:") + node.pipeType().name()' in idx
# потери: только провод/тепло, плоская формула габарита dx+dy+dz+1
assert "lossMilliFor(kind, members.size())" in idx  # 0.3.99/101: потери × N всех членов (автор)
assert 'kind.endsWith("HEAT")' in idx and 'kind.endsWith("WIRE")' in idx
# 0.3.101: вспышка/габарит удалены — визуала слияния нет (автор: без мёртвого кода)
assert "largestMerged" not in idx and "Math.min(24" not in idx
# инвалидация по множеству: place = слияние по соседям, remove = раскол по флуду
assert "Set<Long> union" in idx and "Deque<BlockPos> queue" in idx
assert "component.size() >= 2" in idx
assert "if (!level.isLoaded(next)) continue;" in idx  # флуд не грузит чанки

# ── Хуки: оба блока узлов, тир-2 наследует ──
assert node.count("NodeClumpIndex.onNodeChanged(level, pos);") == 1
# 0.3.101: onRemove под гвардом смены блока (смена режима ключом не рвёт кламп)
assert node.count("NodeClumpIndex.onNodeChanged(level, pos, movedByPiston)") == 0
assert "NodeClumpIndex.onNodeRemoved(level, pos, state);" in node
assert "NodeClumpIndex.onNodeChanged(level, pos);" in uni
assert "NodeClumpIndex.onNodeRemoved(level, pos, state);" in uni
item_node = (NET / "ItemNodeBlock.java").read_text()
assert "extends NodeBlock" in item_node  # предметные узлы наследуют хуки
# сброс на остановке сервера
assert "NodeClumpIndex.clearAll();" in (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text()

# ── Маршрутизатор: кламп не капирует + плоская потеря при первом входе ──
assert "if (NodeClumpIndex.isMember(level, s.pipe())) continue;" in routing
# 0.3.101: трижды (индекс пересечений + оба цикла хвоста остатка — NPE-крэш автора)
assert routing.count("if (NodeClumpIndex.isMember(level, s.pipe())) continue;") == 3
assert "long root = NodeClumpIndex.rootOf(level, path.get(i).pipe());" in routing
assert "if (clumpsSeen.add(root)) {" in routing
assert "NodeClumpIndex.lossMilliOfRoot(level, root);" in routing

# ── 0.3.101: HUD-счётчик убран целиком (пейлоад/поле/строка/lang) ──
assert "clumpSize" not in flow and "clumpSize" not in hud
# ── 0.3.101: hover любого члена — сервер агрегирует граничные выходы клампа ──
assert "public static Set<BlockPos> membersOf(Level level, BlockPos pos)" in idx
assert "if (NodeClumpIndex.isMember(level, pos)) {" in flow
assert "if (NodeClumpIndex.isMember(level, m.relative(d))) continue;" in flow
assert "NodeClumpIndex.lossMilliOfRoot(level, NodeClumpIndex.rootOf(level, pos))" in flow

print("node clump pins passed (index + hooks + router law/loss + HUD + lang)")

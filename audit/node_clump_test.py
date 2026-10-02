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
assert 'kind.equals(kindOf(level, next, level.getBlockState(next)))' in idx
# 0.3.104: порты сформированных мультиблоков — не узлы сети (не сшиваются)
assert "if (TurbineStructure.isMember(level, pos) || SteamGenStructure.isMember(level, pos)) return null;" in idx
assert "public static boolean isMember(Level level, BlockPos pos) { return false; }" in (
    ROOT / "audit/stubs/com/gonzotech/machines/steamgen/SteamGenStructure.java").read_text()
assert "public static void detachPorts(ServerLevel level, List<BlockPos> ports)" in idx
assert "private static void removeInternal(ServerLevel level, BlockPos pos, String kind)" in idx
assert "Deque<BlockPos> queue = new ArrayDeque<>();" in idx
assert "record Clump(long root, long lossMilli, Set<Long> members)" in idx
# 0.3.101: инвариант непересечения (лечит «экспоненциальный» HUD-счётчик автора)
assert "if (oldRoot != null && oldRoot != root) byRoot.remove(oldRoot);" in idx
# 0.3.101: уборка устаревших корней (живой кламп отображает корень в себя)
assert "self == null || self != r" in idx
# 0.3.105: раскол не теряет клампы — два фикса по матсимуляции
# (1) компонент = ТОЛЬКО члены: членство проверяется ДО seen.add
assert "if (!allowed.contains(key) || !seen.add(key)) continue;" in idx
assert "!seen.add(key) || !allowed.contains(key)" not in idx
# (2) протухшие маппинги остатка стираются до раскладки (иначе инвариант
#     register второй компоненты съедает свежий кламп первой)
assert "for (long key : rest) members.remove(key);" in idx
# 0.3.101/102: вспышка убрана навсегда (автор: «партиклы тоже убираем»),
# но строка HUD «сшито узлов: N» возвращена с ЧЕСТНЫМ счётчиком (sizeAt)
assert "sendParticles" not in idx and "DustParticleOptions" not in idx
assert "public static int sizeAt(Level level, BlockPos pos)" in idx
assert "int size," not in idx  # поле рекорда не возвращаем — берём members.size()
assert "e.clumpSize = payload.clumpSize();" in hud
assert 'hud.gonzotech.wrench_clump' in hud
assert "ByteBufCodecs.VAR_INT, FlowPayload::clumpSize" in flow
for lang in ("en_us.json", "ru_ru.json"):
    lang_src = (ROOT / "src/main/resources/assets/gonzotech/lang" / lang).read_text()
    assert "hud.gonzotech.wrench_clump" in lang_src, lang
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

# ── 0.3.102: hover любого члена — агрегат МАКСОМ собственных сумм членов ──
# (0.3.101 суммировал граничные грани — в главном кейсе автора порт парогена
# сам узел клампа: путь кончается внутри, граничных записей нет → поток
# «пропадал». Сохранение потока: член на пути несёт весь транзит.)
assert "public static Set<BlockPos> membersOf(Level level, BlockPos pos)" in idx
assert "if (NodeClumpIndex.isMember(level, pos)) {" in flow
assert "if (own > clumpSum) clumpSum = own;" in flow
assert "NodeClumpIndex.lossMilliOfRoot(level, NodeClumpIndex.rootOf(level, pos))" in flow
assert "NodeClumpIndex.sizeAt(level, pos)" in flow

print("node clump pins passed (index + hooks + router law/loss + HUD + lang)")

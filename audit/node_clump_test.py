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
assert "record Clump(long root, int size, long lossMilli, BlockPos center, Set<Long> members)" in idx
assert "MEMBER_ROOT = new IdentityHashMap<>()" in idx and "BY_ROOT = new IdentityHashMap<>()" in idx
assert "public static void clearAll()" in idx
# род: уни ("U1"/"U2") и узлы одного типа+тира ("N1:HEAT"/"N2:HEAT"), ничего больше
assert 'b instanceof SecondTierPipe ? "U2" : "U1"' in idx
assert '(b instanceof SecondTierPipe ? "N2:" : "N1:") + node.pipeType().name()' in idx
# потери: только провод/тепло, плоская формула габарита dx+dy+dz+1
assert "int lossCells = members.size();" in idx  # 0.3.99: потери × N всех членов (автор)
assert "lossCells * PipeLoss.perCell(second, heat)" in idx
assert 'kind.endsWith("HEAT")' in idx and 'kind.endsWith("WIRE")' in idx
# вспышка только на росте, ≤24 частиц
assert "members.size() > oldTotal" in idx
assert "Math.min(24, members.size())" in idx
# инвалидация по множеству: place = слияние по соседям, remove = раскол по флуду
assert "Set<Long> union" in idx and "floodWithin" in idx
assert "component.size() >= 2" in idx
assert "if (!level.isLoaded(next)) continue;" in idx  # флуд не грузит чанки

# ── Хуки: оба блока узлов, тир-2 наследует ──
assert node.count("NodeClumpIndex.onNodeChanged(level, pos)") == 1
assert "NodeClumpIndex.onNodeRemoved(level, pos, state);" in node
assert "NodeClumpIndex.onNodeChanged(level, pos);" in uni
assert "NodeClumpIndex.onNodeRemoved(level, pos, state);" in uni
item_node = (NET / "ItemNodeBlock.java").read_text()
assert "extends NodeBlock" in item_node  # предметные узлы наследуют хуки
# сброс на остановке сервера
assert "NodeClumpIndex.clearAll();" in (ROOT / "src/main/java/com/gonzotech/GonzoTechMod.java").read_text()

# ── Маршрутизатор: кламп не капирует + плоская потеря при первом входе ──
assert "if (NodeClumpIndex.isMember(level, s.pipe())) continue;" in routing
assert "long root = NodeClumpIndex.rootOf(level, path.get(i).pipe());" in routing
assert "if (clumpsSeen.add(root)) {" in routing
assert "NodeClumpIndex.lossMilliOfRoot(level, root);" in routing

# ── HUD: серверный размер в пейлоаде + строка «сшито» ──
assert "long lossMilli, int clumpSize) implements CustomPacketPayload" in flow
assert "ByteBufCodecs.VAR_INT, FlowPayload::clumpSize" in flow
assert flow.count("NodeClumpIndex.sizeAt(level, pos)") == 2
assert "e.clumpSize = payload.clumpSize();" in hud
assert 'hud.gonzotech.wrench_clump' in hud
for lang in ("en_us.json", "ru_ru.json"):
    lang_src = (ROOT / "src/main/resources/assets/gonzotech/lang" / lang).read_text()
    assert "hud.gonzotech.wrench_clump" in lang_src, lang

print("node clump pins passed (index + hooks + router law/loss + HUD + lang)")

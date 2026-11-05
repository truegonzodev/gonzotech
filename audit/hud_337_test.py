#!/usr/bin/env python3
"""0.3.337: HUD ключа — только текущие потоки >0, без багованного мелькания.

Source-контракты по приёмке автора 05.11.2026 + закрытие D-PORTMODE1.
Ключевое уточнение автора: «1 сек есть / 1 сек нет» было НЕ поточным, а
БАГОМ ОБНОВЛЕНИЯ при 100% однородном постоянном потоке; поточное появление/
исчезновение при реально прерывистом потоке корректно и отражает реальность.
Поэтому «строка всегда видна априори» (первая редакция 0.3.337) отменена:
лечится серверный срез, а не рендер.

НЕ доказательство native Gradle/NeoForge/JAR-сборки, Minecraft-клиента/сервера,
Mixins-рантайма, GPU/TPS или GameTest.
"""
import json
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / 'src/main/resources/assets/gonzotech/lang'
DOCS = ROOT / 'docs'

_ver.at_least('0.3.337', '0.3.337 HUD flow filter and server sample window')


def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')


hud = read('src/main/java/com/gonzotech/machines/client/WrenchHud.java')
network = read('src/main/java/com/gonzotech/machines/network/PipeFlowNetwork.java')
flow_tracker = read('src/main/java/com/gonzotech/machines/network/FlowTracker.java')
item_tracker = read('src/main/java/com/gonzotech/machines/network/ItemFlowTracker.java')
ru = json.loads((LANG / 'ru_ru.json').read_text(encoding='utf-8'))
en = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))

# ── 1. Лечим ПРИЧИНУ багованного мелькания: серверный срез ────────────────
# respond() брал мгновенный FlowTracker.get(), живущий <=1 тик; при постоянном
# потоке пакет попадал в фазу, когда срез обнулился, и сервер слал ложный 0.
assert 'public static final int HUD_WINDOW_TICKS = 2;' in flow_tracker
assert 'public static long[] getHud(Level level, BlockPos pipe, PipeType type)' in flow_tracker
assert 'if (level.getGameTime() - h.tick > HUD_WINDOW_TICKS) return EMPTY;' in flow_tracker
# Игровая семантика get() для компаратора НЕ расширялась: окно осталось 1 тик.
assert 'if (level.getGameTime() - h.tick > 1) return EMPTY;' in flow_tracker
assert 'public static long[] get(Level level, BlockPos pipe, PipeType type)' in flow_tracker
assert 'public static final int HUD_WINDOW_TICKS = 2;' in item_tracker
assert 'public static Map<Item, Integer> getHud(Level level, BlockPos pipe)' in item_tracker
assert 'long[] flow = FlowTracker.getHud(level, pos, pipeType);' in network
assert 'for (long v : FlowTracker.getHud(level, m, pipeType)) own += v;' in network
assert 'java.util.Map<net.minecraft.world.item.Item, Integer> flow = ItemFlowTracker.getHud(level, pos);' in network
# Исторический пин 0.3.104 (second_pass_test) не тронут: потери идут из getLoss.
assert network.count('FlowTracker.getLoss(level, pos, pipeType)') == 1
# Нулевые предметные записи не уходят в HUD.
assert 'if (entries.get(i).getValue() <= 0) continue;' in network

# ── 2. HUD показывает ТОЛЬКО текущие потоки >0 ед./тик ────────────────────
fl = hud[hud.index('private static Component flowLine('):]
assert 'private static Component flowLine(BlockPos pos, PipeType part)' in fl
assert 'boolean fresh = e != null && clientTick - e.clientTick <= FLOW_STALE_TICKS;' in fl
assert 'long total = fresh ? Math.max(0L, e.pos3d + e.neg3d) : 0L;' in fl
assert 'if (fresh && total > 0) {' in fl
assert 'if (fresh) return null;' in fl        # подтверждённый ноль → строки НЕТ
assert 'String.format(java.util.Locale.ROOT, "%.1f", total / 1000.0)' in fl
# Маскировка нуля («строка априори», «поток: 0») полностью убрана.
for banned in ('if (total <= 0) return null;',
               'if (clientTick - e.clientTick > FLOW_STALE_TICKS) return null;',
               'if (flowPos == null || !flowPos.equals(pos)) return null;',
               'boolean stale = e == null || clientTick - e.clientTick > FLOW_STALE_TICKS;',
               'if (e == null && clientTick - flowWaitStartTick < PENDING_GRACE_TICKS) return null;',
               'Object value = stale || itemFlowClientTick == Long.MIN_VALUE ? "<?>" : 0L;'):
    assert banned not in fl, banned
assert 'Object value =' not in hud
# «<?>» — только для типа с недавно подтверждённой передачей при ЗАДЕРЖКЕ ответа.
assert 'long lastPositiveTick = Long.MIN_VALUE;' in hud
assert 'if (payload.posAmount() + payload.negAmount() > 0) e.lastPositiveTick = clientTick;' in hud
assert 'e.lastPositiveTick == Long.MIN_VALUE' in fl
assert 'return unknownFlowLine(part);' in fl
assert 'private static Component unknownFlowLine(PipeType part)' in hud
assert 'GtUnits.rate("<?>", GtUnits.key(part.unitKey(), color), color)' in hud

ifl = hud[hud.index('private static List<Component> itemFlowLines('):]
assert 'if (clientTick - itemFlowClientTick > FLOW_STALE_TICKS) return List.of();' not in ifl
assert 'if (!stale) return itemFlowLinesCache;' in ifl
assert 'itemFlowPositiveTick == Long.MIN_VALUE' in ifl
assert 'return java.util.List.of(unknownFlowLine(PipeType.ITEM));' in ifl
assert 'if (payload.counts().get(i) <= 0) continue;' in hud

# ── 3. Первокадровая вспышка и частота цифр ───────────────────────────────
assert 'private static final int PENDING_GRACE_TICKS = 2;' in hud
assert 'private static long flowWaitStartTick = Long.MIN_VALUE;' in hud
assert 'flowWaitStartTick = clientTick;' in hud
assert 'boolean awaitingFirst = clumpClientTick == Long.MIN_VALUE;' in hud
assert 'clientTick - flowWaitStartTick < PENDING_GRACE_TICKS' in hud
assert 'if (!grace) {' in hud
assert 'hud.gonzotech.wrench_clump_unknown' in hud
assert 'flowByType.clear();' in hud and 'portOwners' in hud
assert 'private static final int FLOW_REQUEST_INTERVAL = 2;' in hud
assert 'private static final int REQUEST_INTERVAL = 5;' in hud
assert 'clientTick - lastAllFlowRequestTick < FLOW_REQUEST_INTERVAL' in hud
assert 'clientTick - lastItemRequestTick >= FLOW_REQUEST_INTERVAL' in hud
assert 'private static final int FLOW_REQUESTS_PER_WINDOW = 24;' in network
assert network.count('FLOW_REQUESTS_PER_WINDOW') == 3
assert 'PayloadThrottle.allow(player, "pipeFlowAll", FLOW_REQUESTS_PER_WINDOW)' in network
assert 'PayloadThrottle.allow(player, "pipeItemFlow", FLOW_REQUESTS_PER_WINDOW)' in network
assert 'pipeFlowAll", 8' not in network and 'pipeItemFlow", 8' not in network
assert 'public record PortInfoPayload(BlockPos pos, int ownerId)' in network
assert 'PORT_PROBE_RADIUS' in network and 'PORT_PROBE_LIMIT' in network

# ── 4. Нижняя строка AUTO удалена ЦЕЛИКОМ ─────────────────────────────────
assert 'java.util.List<Component> title = new java.util.ArrayList<>(3)' in hud
assert 'hud.gonzotech.wrench_port_mode.' not in hud
assert 'portMode == PipeMode.AUTO' not in hud
assert 'portShortMode' in hud and 'EmbeddedPortMode.nodeMode(state)' in hud
assert 'title.add(Component.translatable("hud.gonzotech.wrench_embedded_port.host"' in hud
assert 'title.add(Component.translatable("hud.gonzotech.wrench_embedded_port.owner"' in hud
assert len(ru) == len(en)
for key in ('hud.gonzotech.wrench_port_mode.auto',
            'hud.gonzotech.wrench_port_mode.pull',
            'hud.gonzotech.wrench_port_mode.push',
            'hud.gonzotech.wrench_clump_unknown',
            'hud.gonzotech.flow_amount'):
    assert ru.get(key) and en.get(key), key
assert '<?>' in ru['hud.gonzotech.wrench_clump_unknown']

# ── 5. Документы приёмки внутри одного патча (version НЕ поднималась) ─────
current = (DOCS / 'CURRENT-STATE.md').read_text(encoding='utf-8')
history = (DOCS / 'HISTORY.md').read_text(encoding='utf-8')
rules = (DOCS / 'RULES.md').read_text(encoding='utf-8')
audit = (DOCS / 'AUDIT.md').read_text(encoding='utf-8')
deferred = (DOCS / 'DEFFERED.md').read_text(encoding='utf-8')
assert '0.3.337' in current and '**Статус: закрыто.**' in current
assert 'Закрыто в 0.3.337. Удалить при 0.3.341.' in current
assert 'Режимы работы вшитых портов приняты' in history
assert 'v-0-3-337' in history and 'v-0-3-337-accept' in history
assert 'image-4.png' in history
assert 'только текущие потоки' in history or 'только текущие потоки' in rules
assert 'D-PORTMODE1 выведен из отложенных' in deferred
assert '38.' in rules
assert 'hud337' in audit and 'hud337-accept' in audit
assert (DOCS / 'archive/editions/0.3.337/manifest.json').is_file()
mode = read('src/main/java/com/gonzotech/machines/network/EmbeddedPortMode.java')
assert 'allowsIntake' in mode and 'allowsOutput' in mode
assert 'sharedRemaining' in mode and 'cappedIntake' in mode
# one_patch: новый Z НЕ заводился.
assert 'mod_version=0.3.337' in read('gradle.properties')

print('0.3.337 HUD: only active flows >0 + fixed server sample window PASS; '
      'bug-flicker cause treated, not masked; source-only, not a native build or GameTest')

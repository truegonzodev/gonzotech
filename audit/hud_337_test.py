#!/usr/bin/env python3
"""0.3.337: HUD ключа без вспышки «сшито <?>» и без мерцания строки «поток».

Source-контракты по трём замечаниям автора 05.11.2026 + закрытие D-PORTMODE1
по игровой приёмке. НЕ доказательство native Gradle/NeoForge/JAR-сборки,
Minecraft-клиента/сервера, Mixins-рантайма, GPU/TPS или GameTest.
"""
import json
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / 'src/main/resources/assets/gonzotech/lang'
DOCS = ROOT / 'docs'

_ver.at_least('0.3.337', '0.3.337 HUD clump/flow flicker and AUTO line removal')


def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')


hud = read('src/main/java/com/gonzotech/machines/client/WrenchHud.java')
network = read('src/main/java/com/gonzotech/machines/network/PipeFlowNetwork.java')
ru = json.loads((LANG / 'ru_ru.json').read_text(encoding='utf-8'))
en = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))

# ── Замечание 1: «сшито <?>» не вспыхивает в первую миллисекунду ───────────
# Корень: строка с "<?>" рисовалась ДО первого подтверждённого ответа и на
# следующем кадре заменялась числом. Теперь первые PENDING_GRACE_TICKS тиков
# строка не рисуется вообще; "<?>" остаётся только для реально медленного
# сервера/пересчёта (полная строка, без вечного скрытия).
assert 'private static final int PENDING_GRACE_TICKS = 2;' in hud
assert 'private static long flowWaitStartTick = Long.MIN_VALUE;' in hud
assert 'flowWaitStartTick = clientTick;' in hud
assert 'boolean awaitingFirst = clumpClientTick == Long.MIN_VALUE;' in hud
assert 'clientTick - flowWaitStartTick < PENDING_GRACE_TICKS' in hud
assert 'if (!grace) {' in hud
assert 'hud.gonzotech.wrench_clump_unknown' in hud        # полная строка сохранена
assert 'hud.gonzotech.wrench_clump' in hud                # и число сохранено
assert 'clumpFresh && clumpSize > 0' in hud
assert 'clumpSize = -1' in hud and 'clumpSize = 1' not in hud
assert 'flowByType.clear();' in hud                       # смена цели чистит поток/кламп
assert 'portOwners' in hud                               # а кэш владельца — НЕТ

# ── Замечание 2: строка «поток» не мигает «1 сек есть / 1 сек нет» ────────
# Раньше flowLine возвращал null при total<=0 и при ответе старше
# FLOW_STALE_TICKS. Теперь после первого ответа строка рисуется ВСЕГДА:
# свежий ответ → текущее число (в т.ч. ноль), задержка → полная строка с "<?>".
fl = hud[hud.index('private static Component flowLine('):]
assert 'private static Component flowLine(BlockPos pos, PipeType part)' in fl
assert 'if (total <= 0) return null;' not in fl
assert 'if (clientTick - e.clientTick > FLOW_STALE_TICKS) return null;' not in fl
assert 'if (flowPos == null || !flowPos.equals(pos)) return null;' not in fl
assert 'long total = Math.max(0L, e.pos3d + e.neg3d);' in fl
assert 'boolean stale = e == null || clientTick - e.clientTick > FLOW_STALE_TICKS;' in fl
assert 'GtUnits.rate("<?>", GtUnits.key(part.unitKey(), color), color)' in fl
assert 'if (e == null && clientTick - flowWaitStartTick < PENDING_GRACE_TICKS) return null;' in fl
# Предметная строка тоже больше не гаснет по истечении свежести.
ifl = hud[hud.index('private static List<Component> itemFlowLines('):]
assert 'if (clientTick - itemFlowClientTick > FLOW_STALE_TICKS) return List.of();' not in ifl
assert 'return itemFlowLinesCache;' in ifl
assert 'boolean stale = clientTick - itemFlowClientTick > FLOW_STALE_TICKS;' in ifl
assert 'Object value = stale || itemFlowClientTick == Long.MIN_VALUE ? "<?>" : 0L;' in ifl
assert 'hud.gonzotech.flow_amount' in ifl

# ── Замечание 3: цифры обновляются раз в 1–2 тика ──────────────────────────
assert 'private static final int FLOW_REQUEST_INTERVAL = 2;' in hud
assert 'private static final int REQUEST_INTERVAL = 5;' in hud   # прочие запросы не участились
assert 'clientTick - lastAllFlowRequestTick < FLOW_REQUEST_INTERVAL' in hud
assert 'clientTick - lastItemRequestTick >= FLOW_REQUEST_INTERVAL' in hud
# Серверный троттлинг больше не отбрасывает половину запросов.
assert 'private static final int FLOW_REQUESTS_PER_WINDOW = 24;' in network
assert network.count('FLOW_REQUESTS_PER_WINDOW') == 3
assert 'PayloadThrottle.allow(player, "pipeFlowAll", FLOW_REQUESTS_PER_WINDOW)' in network
assert 'PayloadThrottle.allow(player, "pipeItemFlow", FLOW_REQUESTS_PER_WINDOW)' in network
assert 'pipeFlowAll", 8' not in network and 'pipeItemFlow", 8' not in network
# Пакеты/ответы и area-probe не переписаны (пины 0.3.335 целы).
assert 'public record PortInfoPayload(BlockPos pos, int ownerId)' in network
assert 'PORT_PROBE_RADIUS' in network and 'PORT_PROBE_LIMIT' in network

# ── Замечание 5: нижняя строка AUTO удалена ЦЕЛИКОМ ────────────────────────
assert 'java.util.List<Component> title = new java.util.ArrayList<>(3)' in hud
assert 'hud.gonzotech.wrench_port_mode.' not in hud
assert 'portMode == PipeMode.AUTO' not in hud
assert 'portShortMode' in hud and 'EmbeddedPortMode.nodeMode(state)' in hud
assert 'title.add(Component.translatable("hud.gonzotech.wrench_embedded_port.host"' in hud
assert 'title.add(Component.translatable("hud.gonzotech.wrench_embedded_port.owner"' in hud
# Ключи режимов остаются в RU/EN как канонические названия, строка не рендерится.
assert len(ru) == len(en)
for key in ('hud.gonzotech.wrench_port_mode.auto',
            'hud.gonzotech.wrench_port_mode.pull',
            'hud.gonzotech.wrench_port_mode.push',
            'hud.gonzotech.wrench_clump_unknown',
            'hud.gonzotech.flow_amount'):
    assert ru.get(key) and en.get(key), key
assert '<?>' in ru['hud.gonzotech.wrench_clump_unknown']

# ── Замечание 4: D-PORTMODE1 закрыт по игровой приёмке ────────────────────
current = (DOCS / 'CURRENT-STATE.md').read_text(encoding='utf-8')
history = (DOCS / 'HISTORY.md').read_text(encoding='utf-8')
rules = (DOCS / 'RULES.md').read_text(encoding='utf-8')
deferred = (DOCS / 'DEFFERED.md').read_text(encoding='utf-8')
audit = (DOCS / 'AUDIT.md').read_text(encoding='utf-8')
assert '0.3.337' in current
assert '**Статус: закрыто.**' in current
assert 'Закрыто в 0.3.337. Удалить при 0.3.341.' in current
assert 'Режимы работы вшитых портов приняты' in history
assert 'v-0-3-337' in history
assert 'D-PORTMODE1 выведен из отложенных' in deferred
assert '38.' in rules or 'HUD' in rules
assert 'hud337' in audit
# Реализация режимов 0.3.336 не откатывалась.
mode = read('src/main/java/com/gonzotech/machines/network/EmbeddedPortMode.java')
assert 'allowsIntake' in mode and 'allowsOutput' in mode
assert 'sharedRemaining' in mode and 'cappedIntake' in mode

print('0.3.337 HUD clump/flow stability + AUTO line removal source contracts PASS; '
      'D-PORTMODE1 closed by author acceptance; source-only, not a native build or GameTest')

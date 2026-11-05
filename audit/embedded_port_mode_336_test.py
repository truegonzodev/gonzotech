#!/usr/bin/env python3
"""0.3.336 / D-PORTMODE1: реальные режимы вшитых корпусных портов.

Source-контракты и отрицательные пины по четырём решениям автора 05.11.2026.
НЕ доказательство native Gradle/NeoForge/JAR-сборки, Minecraft-клиента/сервера,
Mixins-рантайма, GPU/TPS или GameTest; игровая приёмка режимов ещё впереди.
"""
import json
import re
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/gonzotech'
NET = JAVA / 'machines' / 'network'
LANG = ROOT / 'src/main/resources/assets/gonzotech/lang'
DOCS = ROOT / 'docs'

_ver.at_least('0.3.336', '0.3.336 D-PORTMODE1 embedded port I/O modes')


def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')


mode = read('src/main/java/com/gonzotech/machines/network/EmbeddedPortMode.java')
routing = read('src/main/java/com/gonzotech/machines/network/PipeRouting.java')
hud = read('src/main/java/com/gonzotech/machines/client/WrenchHud.java')
formed = read('src/main/java/com/gonzotech/machines/FormedPorts.java')
pipe_mode = read('src/main/java/com/gonzotech/machines/network/PipeMode.java')
defs = read('src/main/java/com/gonzotech/machines/energy/MachineDefs.java')
reactor = read('src/main/java/com/gonzotech/machines/reactor/ReactorStructure.java')
hud_lang_ru = json.loads((LANG / 'ru_ru.json').read_text(encoding='utf-8'))
hud_lang_en = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))

# ── Решение 1: общий бюджет на СУММУ приёма и выдачи, ротация по тикам ──────
assert 'public static long sharedRemaining(' in mode
assert 'cap - slice.usedOutput - slice.usedInput' in mode
assert '(level.getGameTime() & 1L) == 0L' in mode          # rotation по тикам
assert 'cap / 2L' in mode                                  # резерв приоритета
assert 'public static void charge(' in mode
assert 'slice.usedOutput += amount' in mode and 'slice.usedInput += amount' in mode
assert 'private record PortKey(long pos, int type)' in mode  # ключ (порт, канал)
assert 'WeakHashMap' in mode and 'slice.tick != tick' in mode  # сброс на тике, без утечки
assert 'public static Transfer.Receiver cappedIntake(' in mode
assert 'if (!simulate && accepted > 0L) charge' in mode     # simulate ничего не списывает
assert 'long cap = portCap(level, pos, type);   // один раз на lane' in mode

# ── Решение 2: забор ТОЛЬКО принимает, отдача ТОЛЬКО отдаёт ───────────────
assert 'public static boolean allowsIntake(PipeMode mode)' in mode
assert 'return mode == PipeMode.AUTO || mode == PipeMode.PULL;' in mode
assert 'public static boolean allowsOutput(PipeMode mode)' in mode
assert 'return mode == PipeMode.AUTO || mode == PipeMode.PUSH;' in mode
assert 'EmbeddedPortMode.allowsOutput(sourceMode)' in routing      # исходный порт
assert '"mode-output-blocked"' in routing                        # GTH в «заборе» закрыт
assert 'EmbeddedPortMode.allowsIntake(EmbeddedPortMode.nodeMode(pstate))' in routing
assert routing.count('EmbeddedPortMode.cappedIntake') == 2        # оба BFS-сайта
assert 'через отдачу нельзя ввести в условный' in routing
# Направление не подменяется на старую логику «машина ↔ сеть»: гейт стоит
# на СОБСТВЕННЫХ lane'ах порта, а ordinary machine lanes остаются при modeOf.
assert 'PipeMode mode = modeOf(pstate, type);' in routing
assert routing.count('distributeLanes(level, type, lanes, effectiveBudget') == 1
assert 'EmbeddedPortMode.charge(level, port, type, sourceTaken, true)' in routing

# ── Решение 3: ОДИН режим на весь узел, а не на канал ──────────────────────
assert 'public static PipeMode nodeMode(BlockState state)' in mode
assert 'for (PipeType type : PipeType.values())' in mode
assert 'if (carrier.carries(state, type)) return carrier.modeFor(state, type);' in mode
assert 'public static PipeMode resolve(' in mode and 'return nodeMode(state);' in mode
assert 'EmbeddedPortMode.nodeMode(state)' in hud                # HUD показывает тот же режим
assert 'portShortMode' in hud
# 0.3.337: четвёртая строка AUTO удалена по указанию автора, режим остался в первой.
assert 'portMode == PipeMode.AUTO' not in hud
assert 'hud.gonzotech.wrench_port_mode.' not in hud
assert 'PipeMode portMode = embedded' in hud
# Политика не вводит per-канального хранения режимов.
assert 'MODE.get(type)' not in mode
assert 'CompositePipeBlock.MODE' not in mode

# ── Решение 4: режим сохраняется при распаде сборки ────────────────────────
for rel in ('src/main/java/com/gonzotech/machines/reactor/ReactorStructure.java',
            'src/main/java/com/gonzotech/machines/turbine/TurbineStructure.java',
            'src/main/java/com/gonzotech/machines/steamgen/SteamGenStructure.java',
            'src/main/java/com/gonzotech/machines/litho/SiliconFactoryStructure.java',
            'src/main/java/com/gonzotech/machines/network/NodeClumpIndex.java'):
    src = read(rel)
    assert not re.search(r'setValue\s*\([^)]*\bMODE\b', src), rel + ' must not rewrite MODE'
assert 'сохраняется в блокстейте' in mode

# ── Без перебалансировки ───────────────────────────────────────────────────
assert 'REACTOR_GTH_PER_PORT_MILLI = 16_364 * MILLI' in defs
assert 'public static long gthPortLimit(' in reactor
assert 'ReactorStructure.gthPortLimit(level, pos)' in mode
assert 'SteamGenStructure.steamPortLimit(level, pos)' in mode
assert 'TurbineStructure.outputPortLimit(level, pos)' in mode
assert 'Math.max(structural, pipe > 0L ? pipe : 1L)' in mode  # потолок не ниже лимита трубы
assert 'MachineDefs.REACTOR_GTH_PER_PORT_MILLI' not in mode  # число не дублируется
assert 'static final int REACTOR_GTH_PER_PORT_MILLI' not in mode
assert '388000' in read('src/main/java/com/gonzotech/machines/network/PipeType.java')
assert 'PipeFlowLedger' in routing                          # закон на трубе цел
# Все formed-структуры по-прежнему распознаются сервером.
for owner in ('OWNER_REACTOR', 'OWNER_TURBINE', 'OWNER_STEAMGEN',
              'OWNER_BLAST_FURNACE', 'OWNER_SILICON_FACTORY'):
    assert owner in formed, owner
assert 'public static boolean isEmbeddedPort(Level level, BlockPos pos, BlockState state)' in mode
assert 'FormedPorts.ownerId(server, pos, state)' in mode

# ── Заглушка 0.3.335 снята, а не «реализована рядом» ───────────────────────
assert '0.3.336 / D-PORTMODE1' in mode
assert '0.3.336+ D-PORTMODE1' not in routing
assert '// PipeMode effective = EmbeddedPortMode.resolve' not in routing
assert 'политика режимов вшитого порта обсуждается' not in routing.lower()
assert 'return current;' in mode          # только для НЕ вшитого узла
assert 'if (!isEmbeddedPort(level, pos, state)) return current;' in mode

# ── Документы и локализация ────────────────────────────────────────────────
current = (DOCS / 'CURRENT-STATE.md').read_text(encoding='utf-8')
history = (DOCS / 'HISTORY.md').read_text(encoding='utf-8')
rules = (DOCS / 'RULES.md').read_text(encoding='utf-8')
deferred = (DOCS / 'DEFFERED.md').read_text(encoding='utf-8')
assert '0.3.336' in current and 'D-PORTMODE1' in current
assert 'v-0-3-336' in history
assert 'D-PORTMODE1' in rules
assert 'D-PORTMODE1 выведен из отложенных' in deferred  # пункт не числится в двух реестрах
assert len(hud_lang_ru) == len(hud_lang_en)
for key in ('hud.gonzotech.wrench_port_mode.auto',
            'hud.gonzotech.wrench_port_mode.pull',
            'hud.gonzotech.wrench_port_mode.push'):
    assert key in hud_lang_ru and key in hud_lang_en, key
assert 'забор' in hud_lang_ru['hud.gonzotech.wrench_port_mode.pull']
assert 'отдача' in hud_lang_ru['hud.gonzotech.wrench_port_mode.push']
assert 'общую проводимость порта' in hud_lang_ru['hud.gonzotech.wrench_port_mode.auto']

print('0.3.336 D-PORTMODE1 embedded port I/O modes source contracts PASS; '
      'source-only, not a native build, GameTest or in-game acceptance')

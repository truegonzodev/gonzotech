#!/usr/bin/env python3
"""0.3.335: вшитые корпусные порты, возврат обшивки поршнем, стабильный счётчик клампа.

Source-контракты и отрицательные пины, НЕ доказательство Minecraft/NeoForge сборки,
нативного GameTest или игровой приёмки автора. Конкретная политика режимов
вшитого порта отложена в D-PORTMODE1 (0.3.336+) и здесь не реализуется.
"""
import json
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/gonzotech'
LANG = ROOT / 'src/main/resources/assets/gonzotech/lang'

_ver.at_least('0.3.335', '0.3.335 embedded ports / piston shell return / stable clump counter')


def read(rel):
    return (JAVA / rel).read_text(encoding='utf-8')


structure = read('machines/reactor/ReactorStructure.java')
panel_be = read('machines/block/entity/ReactorControlPanelBlockEntity.java')
clumps = read('machines/network/NodeClumpIndex.java')
flow = read('machines/network/PipeFlowNetwork.java')
hud = read('machines/client/WrenchHud.java')
routing = read('machines/network/PipeRouting.java')
mixin = read('mixin/LevelChunkCleanRoomMixin.java')
ports = read('machines/FormedPorts.java')
mode_stub = read('machines/network/EmbeddedPortMode.java')

# ── REACT-VAL1: узел в корпусе = порт, а не узел сети ────────────────────────
# Точная строка старого гварда сохранена (пин 0.3.104), реактор добавлен ОТДЕЛЬНО.
assert 'if (TurbineStructure.isMember(level, pos) || SteamGenStructure.isMember(level, pos)) return null;' in clumps
assert 'if (ReactorStructure.isMember(level, pos)) return null;' in clumps
assert 'NodeClumpIndex.detachPorts(level, ports);' in structure
assert 'level.getBlockState(pos).getBlock() instanceof PipeCarrier && !ports.contains(pos)' in structure
# после настоящего распада порты снова обычные узлы
assert 'for (BlockPos port : formerPorts) NodeClumpIndex.onNodeChanged(level, port);' in structure
# смена режима/формированного флага не рвёт форму; FACING клапана остаётся структурным
assert 'public static boolean isCosmeticStateChange(BlockState before, BlockState after)' in structure
assert 'ReactorValveBlock.FACING' in structure
assert 'public static void onWorldEditStateOnly(Level level, BlockPos pos, BlockState before, BlockState after)' in structure
assert 'public static void onWorldEditCell(Level level, BlockPos pos) {' in structure  # пин 0.3.306 сохранён
assert 'ReactorStructure.onWorldEditCell(level, pos);' in mixin                    # пин 0.3.306 сохранён
assert 'isCosmeticStateChange(old, actual)' in mixin
assert 'onWorldEditStateOnly(level, pos, old, actual)' in mixin
# запрещено возвращать старое поведение «любая правка состояния = ревалидация»
assert 'onWorldEditStateOnly' in structure and 'revalidateSavedBounds(server, panel)' in structure
# FACING клапана — единственное состояние, которое читает validateBox
assert 'if (!before.is(after.getBlock())) return false;' in structure

# ── REACT-VAL2: поршневой возврат обшивки ранее собранного реактора ─────────
assert 'PISTON_REVALIDATION' in structure
assert 'public static void schedulePistonShellRevalidation(Level level, BlockPos pos)' in structure
assert 'public static void tickPistonRevalidation(ServerLevel level, ReactorControlPanelBlockEntity panel)' in structure
assert 'private static Validation revalidateSavedBounds(' in structure
assert 'private static void clearFormedVisuals(' in structure
assert 'ReactorStructure.tickPistonRevalidation(serverLevel, this);' in panel_be
assert 'isShellPart(actual)' in mixin and 'schedulePistonShellRevalidation(level, pos)' in mixin
assert 'public static boolean isShellPart(BlockState state)' in structure
# только сохранённые границы, не первичная сборка от chunk-load/глобального скана
assert 'panel.isFormedController() || !panel.hasSavedBounds()' in structure
assert 'server.getGameTime() + 3L' in structure
# декоративный formed не считается валидностью
assert 'state.setValue(ReactorCasingBlock.FORMED, false)' in structure

# ── HUD-NODE1: счётчик топологии отделён от свежести flow ───────────────────
assert 'e.clumpSize = payload.clumpSize();' in hud          # пин 0.3.102 сохранён
assert 'hud.gonzotech.wrench_clump' in hud                  # пин 0.3.102 сохранён
assert 'CLUMP_STALE_TICKS' in hud and 'clumpClientTick' in hud
assert 'hud.gonzotech.wrench_clump_unknown' in hud
assert 'clumpFresh && clumpSize > 0' in hud
assert 'FlowEntry entry = flowByType.get(part.ordinal());' not in hud  # старое сцепление убрано
assert 'entry.clumpSize > 0' not in hud
assert 'Component.translatable("hud.gonzotech.wrench_clump", entry.clumpSize)' not in hud
assert 'private static Component flowLine(BlockPos pos, PipeType part)' in hud  # flow-метрики остаются у FlowEntry
assert 'aimedPos == null || !aimedPos.equals(payload.pos())' in hud  # задержанный ответ старой цели
# один C2S-пакет на все типы вместо N пакетов в тик
assert 'maybeRequestAllFlow(pos, targetChanged);' in hud
assert 'AllFlowRequestPayload' in hud
assert 'for (PipeType t : parts) maybeRequestFlow(pos, t);' not in hud
assert 'REQUEST_INTERVAL' in hud
# <?> только до свежего ответа; заморозка/фиктивная единица запрещены
assert 'clumpSize = -1' in hud
assert 'clumpSize = 1' not in hud

# ── Подсказка вшитого порта: ГОСТ, реальное имя, режимы для всех formed ─────
assert 'hud.gonzotech.wrench_embedded_port' in hud
assert 'hud.gonzotech.wrench_embedded_port.host' in hud
assert 'hud.gonzotech.wrench_embedded_port.owner' in hud
assert 'hud.gonzotech.wrench_port_mode.' in hud
assert 'state.getBlock().getName()' in hud
assert 'public static void acceptPortInfo(' in hud
assert 'resource.gonzotech.' in hud and 'ownerKey(' in hud
assert 'java.util.List<Component> title' in hud and 'title.add' in hud
# ГОСТ: несколько КОРОТКИХ строк, а не одна непомещающаяся
assert 'Вшитый корпусный порт (%s) — режим: %s' not in hud
assert 'font.lineHeight + 1' in hud
# Приёмка автора (замечание 2): НЕ подменяем классическую строку после проверок.
# До подтверждения заголовок не рисуется вовсе; кэш постоянный по позиции.
assert 'portEmbedded' not in hud and 'portInfoPos' not in hud
assert 'portOwners' in hud and 'ownerId' in hud
assert 'if (info == null)' in hud
assert 'info.ownerId() == com.gonzotech.machines.FormedPorts.OWNER_NONE' in hud
assert 'portOwners.remove(pos)' in hud
assert 'title.add(Component.translatable("hud.gonzotech.wrench_pipe"' in hud
# Ответ приходит пачкой по области и кэшируется без срока жизни
assert 'public record PortInfoPayload(BlockPos pos, int ownerId) implements CustomPacketPayload' in flow
assert 'public record AllFlowRequestPayload(BlockPos pos, boolean probe) implements CustomPacketPayload' in flow
assert 'private static void respondAll(ServerPlayer player, BlockPos pos, boolean probe)' in flow
assert 'PORT_PROBE_RADIUS' in flow and 'PORT_PROBE_LIMIT' in flow
assert 'FormedPorts.ownerId(level, pos, state)' in flow
assert 'private static int clumpSizeForHud(' in flow
# «поставил два узла на узел корпуса → Сшито 2»: считаем кламп СНАРУЖИ
assert 'NodeClumpIndex.sizeAt(level, pos.relative(direction))' in flow
# FlowPayload не переписан (пины 0.3.102/0.3.310 целы)
assert 'long lossMilli, int clumpSize) implements CustomPacketPayload' in flow
assert 'ByteBufCodecs.VAR_INT, FlowPayload::clumpSize' in flow

# все formed-структуры участвуют в определении порта
assert 'public static boolean isEmbeddedPort(ServerLevel level, BlockPos pos, BlockState state)' in ports
assert 'public static int ownerId(ServerLevel level, BlockPos pos, BlockState state)' in ports
for owner in ('OWNER_REACTOR', 'OWNER_TURBINE', 'OWNER_STEAMGEN', 'OWNER_BLAST_FURNACE', 'OWNER_SILICON_FACTORY'):
    assert owner in ports, owner
for symbol in ('ReactorStructure.isMember', 'TurbineStructure.isMember', 'SteamGenStructure.isMember',
               'FireboxBlockEntity', 'BlastFurnaceStructure.nodePositions',
               'SiliconFactoryStructure.controllerAt', 'SiliconFactoryBlockEntity'):
    assert symbol in ports, symbol
assert 'PipeCarrier' in ports

# ── D-PORTMODE1: только заглушки/комменты, конкретной реализации НЕТ ────────
assert 'D-PORTMODE1' in mode_stub and 'return current;' in mode_stub
assert routing.count('0.3.336+ D-PORTMODE1') == 2
assert 'PipeMode mode = modeOf(pstate, type);' in routing  # поведение не подменено
assert 'EmbeddedPortMode.resolve(level, pipe, pstate, type, mode);' in routing  # в комментарии
assert 'PipeMode effective = EmbeddedPortMode.resolve' not in routing.replace(
    '// PipeMode effective = EmbeddedPortMode.resolve', '')  # реального вызова нет

# ── Языковые ключи ──────────────────────────────────────────────────────────
keys = {
    'hud.gonzotech.wrench_clump_unknown',
    'hud.gonzotech.wrench_embedded_port',
    'hud.gonzotech.wrench_embedded_port.host',
    'hud.gonzotech.wrench_embedded_port.owner',
    'hud.gonzotech.wrench_embedded_port.owner.reactor',
    'hud.gonzotech.wrench_embedded_port.owner.turbine',
    'hud.gonzotech.wrench_embedded_port.owner.steamgen',
    'hud.gonzotech.wrench_embedded_port.owner.blast_furnace',
    'hud.gonzotech.wrench_embedded_port.owner.silicon_factory',
    'hud.gonzotech.wrench_embedded_port.owner.unknown',
    'hud.gonzotech.wrench_port_mode.auto',
    'hud.gonzotech.wrench_port_mode.pull',
    'hud.gonzotech.wrench_port_mode.push',
}
ru = json.loads((LANG / 'ru_ru.json').read_text(encoding='utf-8'))
en = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))
for key in keys:
    assert ru.get(key) and en.get(key), key
assert '<?>' in ru['hud.gonzotech.wrench_clump_unknown']
assert 'Принадлежит' in ru['hud.gonzotech.wrench_embedded_port.owner']
assert 'Реактор I-поколения' in ru['hud.gonzotech.wrench_embedded_port.owner.reactor']
assert '%s' in ru['hud.gonzotech.wrench_embedded_port'] and '(%s)' not in ru['hud.gonzotech.wrench_embedded_port']
assert 'Belongs to' in en['hud.gonzotech.wrench_embedded_port.owner']
assert 'обе стороны' in ru['hud.gonzotech.wrench_port_mode.auto']
assert 'both sides' in en['hud.gonzotech.wrench_port_mode.auto']
assert len(ru) == len(en), (len(ru), len(en))

# ── Документы: решения зафиксированы, а не только в чате ────────────────────
docs = ROOT / 'docs'
current = (docs / 'CURRENT-STATE.md').read_text(encoding='utf-8')
deferred = (docs / 'DEFFERED.md').read_text(encoding='utf-8')
rules = (docs / 'RULES.md').read_text(encoding='utf-8')
history = (docs / 'HISTORY.md').read_text(encoding='utf-8')
assert '0.3.335' in current and 'D-PORTMODE1' in deferred
assert 'X.Y.Z' in rules and '32bit' in rules
assert 'v-0-3-335' in history
assert '18³' in current or '18³' in rules

print('0.3.335 embedded port / piston shell / clump HUD source contracts PASS; '
      'D-PORTMODE1 deliberately deferred; not a native build or gameplay acceptance')

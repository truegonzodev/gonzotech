package com.gonzotech.machines.network;

import com.gonzotech.machines.FormedPorts;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.reactor.ReactorStructure;
import com.gonzotech.machines.steamgen.SteamGenStructure;
import com.gonzotech.machines.turbine.TurbineStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 0.3.336 / D-PORTMODE1: РЕАЛЬНАЯ политика режимов вшитого корпусного порта.
 * Решения автора 05.11.2026 (после приёмки 0.3.335):
 * <ul>
 *   <li>«забор» ({@link PipeMode#PULL}) = порт ТОЛЬКО принимает ресурс ВНУТРЬ
 *       сборки; выдача из сборки через него запрещена, в т.ч. GTH реактора;</li>
 *   <li>«отдача» ({@link PipeMode#PUSH}) = порт ТОЛЬКО выдаёт ресурс ИЗ сборки;
 *       приём в сборку запрещён («через отдачу нельзя ввести в условный
 *       пароген воду»);</li>
 *   <li>«авто» ({@link PipeMode#AUTO}) = обе стороны, но они ДЕЛЯТ ОДНУ общую
 *       проводимость порта: сумма приёма и выдачи за тик не превышает потолка.
 *       Первый занявший получает лимит; {@code level.getGameTime()} задаёт
 *       ротацию приоритета по тикам, чтобы ни одно направление не голодало;</li>
 *   <li>режим ОДИН на весь узел (все каналы), а не на канал;</li>
 *   <li>при распаде сборки режим сохраняется в блокстейте (код распада MODE не
 *       пишет) и после повторной сборки действует снова.</li>
 * </ul>
 *
 * <p>Политика намеренно серверная: индексы членов сформированных структур живут
 * в {@link ServerLevel}. Никакой перебалансировки: потолок реактора
 * {@code REACTOR_GTH_PER_PORT_MILLI = 16_364×MILLI}, {@code gthPortLimit},
 * лимиты труб и {@code PipeType.HEAT} (комментарий 388000 / код 256000) не
 * меняются. Общий бюджет ключуется по (позиция, канал): у разных ресурсов
 * разные единицы и потолки, поэтому они не делят один счётчик.</p>
 */
public final class EmbeddedPortMode {

    private EmbeddedPortMode() {
    }

    /**
     * ОДИН режим на весь узел. У {@code UniversalNodeBlock}/{@code PipeBlock}
     * свойство MODE уже одно на блок; у составного пучка с per-канальными MODE
     * берём режим первого несомого канала в фиксированном порядке
     * {@link PipeType#values()} — политика узла едина для всех каналов.
     */
    public static PipeMode nodeMode(BlockState state) {
        if (!(state.getBlock() instanceof PipeCarrier carrier)) return PipeMode.AUTO;
        for (PipeType type : PipeType.values()) {
            if (carrier.carries(state, type)) return carrier.modeFor(state, type);
        }
        return PipeMode.AUTO;
    }

    /** «Забор»/AUTO: ресурс можно ввести ВНУТРЬ сформированной сборки. */
    public static boolean allowsIntake(PipeMode mode) {
        return mode == PipeMode.AUTO || mode == PipeMode.PULL;
    }

    /** «Отдача»/AUTO: сборка может выдать ресурс НАРУЖУ через этот порт. */
    public static boolean allowsOutput(PipeMode mode) {
        return mode == PipeMode.AUTO || mode == PipeMode.PUSH;
    }

    /** Позиция действительно является портом сформированной сборки? */
    public static boolean isEmbeddedPort(Level level, BlockPos pos, BlockState state) {
        return level instanceof ServerLevel server
            && FormedPorts.ownerId(server, pos, state) != FormedPorts.OWNER_NONE;
    }

    /**
     * Точка вызова политики: для вшитого порта возвращает режим ВСЕГО УЗЛА,
     * для обычной трубы — прежний {@code current} без побочных действий.
     */
    public static PipeMode resolve(Level level, BlockPos pos, BlockState state,
                                   PipeType type, PipeMode current) {
        if (!isEmbeddedPort(level, pos, state)) return current;
        return nodeMode(state);
    }

    /**
     * Общая проводимость порта для канала: потолок СТРУКТУРЫ (реактор/пароген/
     * турбина), но не ниже собственного лимита трубы. Для сборок без отдельного
     * потолка (доменная печь, литографическая фабрика) остаётся лимит трубы.
     * Перебалансировки нет: числитель реактора сохранён дословно.
     */
    public static long portCap(Level level, BlockPos pos, PipeType type) {
        long structural = 0L;
        if (level instanceof ServerLevel server) {
            int owner = FormedPorts.ownerId(server, pos, level.getBlockState(pos));
            structural = switch (owner) {
                case FormedPorts.OWNER_REACTOR ->
                    type == PipeType.HEAT ? ReactorStructure.gthPortLimit(level, pos) : 0L;
                case FormedPorts.OWNER_STEAMGEN ->
                    type == PipeType.STEAM ? SteamGenStructure.steamPortLimit(level, pos) : 0L;
                case FormedPorts.OWNER_TURBINE ->
                    type == PipeType.WIRE ? TurbineStructure.outputPortLimit(level, pos) : 0L;
                default -> 0L;
            };
        }
        long pipe = type.maxThroughput();
        return Math.max(structural, pipe > 0L ? pipe : 1L);
    }

    // ── Общий бюджет порта на тик (сумма приёма и выдачи) ──────────────────

    /** Ключ (позиция, канал): у разных каналов разные единицы и потолки. */
    private record PortKey(long pos, int type) {
    }

    private static final class Slice {
        long tick = Long.MIN_VALUE;
        long usedOutput;
        long usedInput;
    }

    /** WeakHashMap по миру: снимки не переживают выгрузку измерения. */
    private static final Map<Level, Map<PortKey, Slice>> USAGE = new WeakHashMap<>();

    private static Slice slice(Level level, BlockPos pos, PipeType type) {
        Map<PortKey, Slice> perLevel;
        synchronized (USAGE) {
            perLevel = USAGE.computeIfAbsent(level, ignored -> new HashMap<>());
        }
        PortKey key = new PortKey(pos.asLong(), type.ordinal());
        Slice slice = perLevel.computeIfAbsent(key, ignored -> new Slice());
        long tick = level.getGameTime();
        if (slice.tick != tick) {
            slice.tick = tick;
            slice.usedOutput = 0L;
            slice.usedInput = 0L;
        }
        return slice;
    }

    /**
     * Сколько ещё может пройти через порт в этом тике в запрошенном
     * направлении. Ротация: в чётные тики первым ходом обладает выдача, в
     * нечётные — приём; не-приоритетное направление, пришедшее первым, не
     * может забрать весь потолок (резервирует половину). Так «кто успел»
     * сохраняется, но ни одна сторона не голодает тик за тиком.
     */
    public static long sharedRemaining(Level level, BlockPos pos, PipeType type, boolean output) {
        return sharedRemaining(level, pos, type, portCap(level, pos, type), output);
    }

    /** То же, но с уже вычисленным потолком (горячий путь приёмника порта). */
    public static long sharedRemaining(Level level, BlockPos pos, PipeType type,
                                       long cap, boolean output) {
        Slice slice = slice(level, pos, type);
        long remaining = cap - slice.usedOutput - slice.usedInput;
        if (remaining <= 0L) return 0L;
        boolean priority = ((level.getGameTime() & 1L) == 0L) == output;
        if (!priority && slice.usedOutput + slice.usedInput == 0L) {
            return Math.min(remaining, Math.max(1L, cap / 2L));
        }
        return remaining;
    }

    /** Списать фактически прошедший ресурс с общего бюджета порта. */
    public static void charge(Level level, BlockPos pos, PipeType type, long amount, boolean output) {
        if (amount <= 0L) return;
        Slice slice = slice(level, pos, type);
        if (output) slice.usedOutput += amount;
        else slice.usedInput += amount;
    }

    /**
     * Обёртка приёмника ВНУТРИ сборки: режет приём общим остатком порта и
     * списывает только реально принятое (simulate ничего не списывает).
     */
    public static Transfer.Receiver cappedIntake(Level level, BlockPos pos, PipeType type,
                                                 Transfer.Receiver delegate) {
        long cap = portCap(level, pos, type);   // один раз на lane, не на каждый receive
        return (amount, simulate) -> {
            long allowance = sharedRemaining(level, pos, type, cap, false);
            if (allowance <= 0L) return 0L;
            long accepted = delegate.receive(Math.min(amount, allowance), simulate);
            if (!simulate && accepted > 0L) charge(level, pos, type, accepted, false);
            return accepted;
        };
    }
}

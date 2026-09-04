package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Sinks.GthSource;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.GtuSource;
import com.gonzotech.machines.energy.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Один связный контур труб одного {@link PipeType}. Тикает целиком (не по одной
 * трубе): за тик забирает ресурс с «входных» концов и раздаёт на «выходные»
 * равномерно ({@link Transfer#distributeAmong}) — независимо от длины и формы
 * трассы. Это и есть «телепорт»: латентность ≤ 1 тик, стоимость тика зависит от
 * числа портов, а не от длины труб.
 * <p>
 * <b>Порты</b> — примыкающие к трубам машины (не-трубы). Труба в режиме,
 * разрешающем забор ({@link PipeMode#canPull}), делает соседний
 * источник ({@code *Source}) ВХОДОМ; в режиме отдачи ({@link PipeMode#canPush})
 * делает соседний приёмник ({@code *Sink}) ВЫХОДОМ. Порты кэшируются и
 * пересчитываются только при {@link #markPortsDirty()} (смена соседей/режима/формы).
 * <p>
 * <b>Внутренний буфер.</b> Сеть держит крошечный транзитный буфер: за тик сперва
 * закачивает из источников в буфер, затем сливает из буфера в приёмники. Это
 * делает передачу строго сохраняющей (ничего не создаётся и не теряется) даже
 * при неравных ёмкостях концов. Буфер не сохраняется в NBT (транзитный, ≤ пары
 * тиков) — при перезагрузке мира обнуляется, потеря пренебрежимо мала.
 */
public final class EnergyNetwork {

    private final PipeType type;
    private final Set<BlockPos> pipes = new HashSet<>();

    /** Транзитный буфер (в единицах ресурса типа сети). */
    private int buffer;

    private boolean portsDirty = true;
    private List<Transfer.Receiver> sources = new ArrayList<>();
    private List<Transfer.Receiver> sinks = new ArrayList<>();

    public EnergyNetwork(PipeType type) {
        this.type = type;
    }

    public PipeType type() {
        return type;
    }

    public Set<BlockPos> pipes() {
        return pipes;
    }

    public boolean contains(BlockPos pos) {
        return pipes.contains(pos);
    }

    public int size() {
        return pipes.size();
    }

    public void addPipe(BlockPos pos) {
        pipes.add(pos.immutable());
        portsDirty = true;
    }

    public void removePipe(BlockPos pos) {
        pipes.remove(pos);
        portsDirty = true;
    }

    public int buffer() {
        return buffer;
    }

    public void addBuffer(int amount) {
        buffer += amount;
    }

    public void markPortsDirty() {
        portsDirty = true;
    }

    // ─────────────────────────── тик ───────────────────────────

    public void tick(Level level, long gameTime) {
        if (pipes.isEmpty()) return;
        if (portsDirty) {
            recomputePorts(level);
        }
        if (sources.isEmpty() && buffer == 0) return;

        int throughput = throughputPerTick();
        int bufferCap = bufferCapacity();

        // Фаза забора: источники → буфер (равномерно), в пределах пропускной
        // способности и свободного места буфера.
        if (!sources.isEmpty()) {
            int space = bufferCap - buffer;
            int pullBudget = Math.min(throughput, space);
            if (pullBudget > 0) {
                int pulled = Transfer.distributeAmong(sources, pullBudget, gameTime);
                buffer += pulled;
            }
        }

        // Фаза раздачи: буфер → приёмники (равномерно), в пределах пропускной
        // способности и содержимого буфера.
        if (!sinks.isEmpty() && buffer > 0) {
            int pushBudget = Math.min(throughput, buffer);
            int inserted = Transfer.distributeAmong(sinks, pushBudget, gameTime);
            buffer -= inserted;
        }
    }

    /**
     * Пересобрать списки портов. Проходим по трубам, у каждой смотрим 6 соседей;
     * сосед-труба пропускается, сосед-машина по режиму трубы становится входом
     * и/или выходом. Дедуп по позиции (машину, примыкающую к двум трубам, берём
     * один раз). Порядок детерминирован (по {@code BlockPos.asLong}) — для
     * честной ротации остатка в {@link Transfer#distributeAmong}.
     */
    private void recomputePorts(Level level) {
        TreeMap<Long, Transfer.Receiver> src = new TreeMap<>();
        TreeMap<Long, Transfer.Receiver> snk = new TreeMap<>();

        for (BlockPos pipe : pipes) {
            BlockState state = level.getBlockState(pipe);
            if (!(state.getBlock() instanceof PipeBlock pb) || pb.pipeType() != type) {
                continue; // устаревшая запись — будет вычищена при следующей перестройке
            }
            PipeMode mode = state.getValue(PipeBlock.MODE);
            boolean canPull = mode.canPull();
            boolean canPush = mode.canPush();
            if (!canPull && !canPush) continue;

            for (Direction dir : Direction.values()) {
                BlockPos npos = pipe.relative(dir);
                if (pipes.contains(npos)) continue; // это тоже труба
                BlockEntity be = level.getBlockEntity(npos);
                if (be == null) continue;
                long key = npos.asLong();

                switch (type) {
                    case WIRE -> {
                        if (canPull && be instanceof GtuSource s && !src.containsKey(key)) {
                            src.put(key, (amt, sim) -> s.extractGtu(amt, sim));
                        }
                        if (canPush && be instanceof GtuSink s && !snk.containsKey(key)) {
                            snk.put(key, s::receiveGtu);
                        }
                    }
                    case HEAT -> {
                        if (canPull && be instanceof GthSource s && !src.containsKey(key)) {
                            src.put(key, (amt, sim) -> s.extractGth(amt, sim));
                        }
                        if (canPush && be instanceof GthSink s && !snk.containsKey(key)) {
                            snk.put(key, s::receiveGth);
                        }
                    }
                }
            }
        }

        sources = new ArrayList<>(src.values());
        sinks = new ArrayList<>(snk.values());
        portsDirty = false;
    }

    private int throughputPerTick() {
        return switch (type) {
            case WIRE -> MachineDefs.WIRE_THROUGHPUT;
            case HEAT -> MachineDefs.HEAT_THROUGHPUT;
        };
    }

    private int bufferCapacity() {
        return switch (type) {
            case WIRE -> MachineDefs.WIRE_BUFFER;
            case HEAT -> MachineDefs.HEAT_BUFFER;
        };
    }
}

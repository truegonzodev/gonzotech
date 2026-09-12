package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.ComparatorOutput;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.SecondAccumulatorMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Аккумулятор II с отдельным балансом второго открытия.
 *
 * <p>Запас 48 900 GTU не помещается в один short-синхрослот ContainerData, поэтому
 * GUI получает его двумя безопасными частями (тысячи + остаток). Это не меняет
 * точный внутренний milli-GTU буфер.</p>
 */
public final class SecondAccumulatorBlockEntity extends BaseMachineBlockEntity implements GtuSink {

    private static final int GUI_GTU_BASE = 1_000;

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.ACCUMULATOR_GTU_CAPACITY);
    private int lastComparatorOutput;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            int units = gtu.amountUnitsInt();
            return switch (i) {
                case 0 -> units % GUI_GTU_BASE;
                case 1 -> units / GUI_GTU_BASE;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            int units = switch (i) {
                case 0 -> data.get(1) * GUI_GTU_BASE + Math.max(0, value);
                case 1 -> Math.max(0, value) * GUI_GTU_BASE + data.get(0);
                default -> gtu.amountUnitsInt();
            };
            gtu.set((long) units * 1_000L);
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public SecondAccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECOND_ACCUMULATOR.get(), pos, state, 0);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    public int comparatorOutput() {
        return ComparatorOutput.from(gtu);
    }

    private void updateComparatorOutput() {
        int next = comparatorOutput();
        if (next == lastComparatorOutput) return;
        lastComparatorOutput = next;
        if (level != null && !level.isClientSide()) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateComparatorOutput();
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        long accepted = gtu.receive(Math.min(amount, (long) SecondTierDefs.ACCUMULATOR_GTU_INTAKE), simulate);
        if (!simulate && accepted > 0) {
            setChanged();
            updateComparatorOutput();
        }
        return accepted;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecondAccumulatorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        if (!be.gtu.isEmpty()) {
            be.gtu.extract(SecondTierDefs.ACCUMULATOR_GTU_LOSS, false);
            changed = true;
        }
        if (be.pushGtu(server, pos)) changed = true;

        if (changed) {
            be.setChanged();
            be.updateComparatorOutput();
        }
    }

    private boolean pushGtu(Level level, BlockPos pos) {
        if (gtu.isEmpty()) return false;
        final long stored = gtu.amountAsLong();
        long budget = Math.min((long) SecondTierDefs.ACCUMULATOR_GTU_OUTPUT, stored);
        long moved = PipeRouting.drain(level, pos, PipeType.WIRE, budget, level.getGameTime(), (target, ignored) -> {
            if (target instanceof AccumulatorBlockEntity other) {
                long diff = stored - other.gtuBuffer().amountAsLong();
                if (diff <= 0) return null;
                long cap = diff / 2;
                if (cap <= 0) return null;
                return (amount, simulate) -> other.receiveGtu(Math.min(amount, cap), simulate);
            }
            if (target instanceof SecondAccumulatorBlockEntity other) {
                long diff = stored - other.gtu.amountAsLong();
                if (diff <= 0) return null;
                long cap = diff / 2;
                if (cap <= 0) return null;
                return (amount, simulate) -> other.receiveGtu(Math.min(amount, cap), simulate);
            }
            if (target instanceof GtuSink sink) return sink::receiveGtu;
            return null;
        });
        if (moved <= 0) return false;
        gtu.extract(moved, false);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SecondAccumulatorMenu(id, inv, this, data);
    }
}

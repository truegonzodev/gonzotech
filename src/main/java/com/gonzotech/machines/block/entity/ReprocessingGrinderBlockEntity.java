package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.ReprocessingGrinderMenu;
import com.gonzotech.machines.processing.ReprocessingRecipes;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Измельчитель отработки (0.3.250): отработанный ТВЭЛ + пустой корпус из
 * отдельного слота → «в меру опасные шлаки» + бочка отходов. Энергетика и
 * такт — как у измельчителя (160 тиков, 0.9 GTU/т). Оба выхода резервируются
 * до запуска и сохраняются до конца операции: выгрузка или слом не дублируют
 * и не теряют материал.
 */
public final class ReprocessingGrinderBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer {

    public static final int SLOT_TVEL_INPUT = 0;
    public static final int SLOT_HULL_INPUT = 1;
    public static final int SLOT_SLAG_OUTPUT = 2;
    public static final int SLOT_BARREL_OUTPUT = 3;

    private static final int[] INPUT_SLOTS = { SLOT_TVEL_INPUT, SLOT_HULL_INPUT };
    private static final int[] OUTPUT_SLOTS = { SLOT_SLAG_OUTPUT, SLOT_BARREL_OUTPUT };

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.GRINDER_GTU_CAPACITY);
    private ItemStack pendingSlag = ItemStack.EMPTY;
    private ItemStack pendingBarrel = ItemStack.EMPTY;
    private int grindProgress;
    private int grindTotal;

    // A machine can be offered energy from several connected routes in one tick;
    // this ledger makes the intake a total tick cap rather than a per-call cap.
    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> grindProgress;
                case 2 -> grindTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> gtu.set(MachineDefs.toMilli(Math.max(0, value)));
                case 1 -> grindProgress = Math.max(0, value);
                case 2 -> grindTotal = Math.max(0, value);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public ReprocessingGrinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REPROCESSING_GRINDER.get(), pos, state, 4);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long remaining = Math.max(0L, (long) SecondTierDefs.GRINDER_GTU_INTAKE - acceptedGtuThisTick);
        long accepted = gtu.receive(Math.min(amount, remaining), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        return accepted;
    }

    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReprocessingGrinderBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        boolean changed = false;

        if (be.grindTotal == 0 && be.tryStartGrinding()) changed = true;
        if (be.grindTotal > 0 && be.tickGrinding()) changed = true;

        if (changed) be.setChanged();
    }

    /** Reserves both outputs and a first work tick before consuming one TVEL and one hull. */
    private boolean tryStartGrinding() {
        ReprocessingRecipes.Outputs outputs =
            ReprocessingRecipes.find(items.get(SLOT_TVEL_INPUT), items.get(SLOT_HULL_INPUT));
        if (outputs == null
            || !canStore(SLOT_SLAG_OUTPUT, outputs.slag())
            || !canStore(SLOT_BARREL_OUTPUT, outputs.barrel())
            || !gtu.has(SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK)) {
            return false;
        }
        items.get(SLOT_TVEL_INPUT).shrink(1);
        items.get(SLOT_HULL_INPUT).shrink(1);
        pendingSlag = outputs.slag();
        pendingBarrel = outputs.barrel();
        grindProgress = 0;
        grindTotal = SecondTierDefs.GRINDER_TICKS;
        return true;
    }

    /** Pays the grinder's per-tick cost for each work tick. */
    private boolean tickGrinding() {
        if (pendingSlag.isEmpty() && pendingBarrel.isEmpty()) {
            // A malformed legacy NBT record must not consume energy forever.
            grindProgress = 0;
            grindTotal = 0;
            return true;
        }
        if (grindProgress >= grindTotal) return tryFinishGrinding();
        if (!gtu.has(SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK)) return false;

        gtu.extract(SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK, false);
        grindProgress++;
        if (grindProgress >= grindTotal) return tryFinishGrinding();
        return true;
    }

    private boolean tryFinishGrinding() {
        if (!canStore(SLOT_SLAG_OUTPUT, pendingSlag) || !canStore(SLOT_BARREL_OUTPUT, pendingBarrel)) {
            return false;
        }
        insert(SLOT_SLAG_OUTPUT, pendingSlag);
        insert(SLOT_BARREL_OUTPUT, pendingBarrel);
        pendingSlag = ItemStack.EMPTY;
        pendingBarrel = ItemStack.EMPTY;
        grindProgress = 0;
        grindTotal = 0;
        return true;
    }

    private void insert(int slot, ItemStack incoming) {
        ItemStack output = items.get(slot);
        if (output.isEmpty()) {
            items.set(slot, incoming.copy());
        } else {
            output.grow(incoming.getCount());
        }
    }

    private boolean canStore(int slot, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        ItemStack output = items.get(slot);
        int limit = Math.min(getMaxStackSize(), incoming.getMaxStackSize());
        return output.isEmpty()
            ? incoming.getCount() <= limit
            : ItemStack.isSameItemSameComponents(output, incoming)
                && output.getCount() + incoming.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    // ─────────────────────────── inventory automation ───────────────────────────

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return (slot == SLOT_TVEL_INPUT && ReprocessingRecipes.acceptsTvel(stack))
            || (slot == SLOT_HULL_INPUT && ReprocessingRecipes.acceptsHull(stack));
    }

    /** Outputs leave below; the TVEL and the hull are inserted from the other sides. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != null && side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN
            && (slot == SLOT_SLAG_OUTPUT || slot == SLOT_BARREL_OUTPUT);
    }

    // ─────────────────────────── persistence / menu ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("GrindProgress", grindProgress);
        tag.putInt("GrindTotal", grindTotal);
        if (!pendingSlag.isEmpty()) tag.put("PendingSlag", pendingSlag.save(registries));
        if (!pendingBarrel.isEmpty()) tag.put("PendingBarrel", pendingBarrel.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        grindProgress = Math.max(0, tag.getInt("GrindProgress"));
        grindTotal = Math.max(0, tag.getInt("GrindTotal"));
        pendingSlag = tag.contains("PendingSlag")
            ? ItemStack.parseOptional(registries, tag.getCompound("PendingSlag"))
            : ItemStack.EMPTY;
        pendingBarrel = tag.contains("PendingBarrel")
            ? ItemStack.parseOptional(registries, tag.getCompound("PendingBarrel"))
            : ItemStack.EMPTY;
        if (pendingSlag.isEmpty() && pendingBarrel.isEmpty()) {
            grindProgress = 0;
            grindTotal = 0;
        } else {
            grindTotal = SecondTierDefs.GRINDER_TICKS;
            grindProgress = Math.min(grindProgress, grindTotal);
        }
    }

    /** A running operation has already consumed its sources; keep the reserved results on break. */
    public void dropPendingOutputForBreak() {
        if (level == null || level.isClientSide()) return;
        if (!pendingSlag.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), pendingSlag.copy());
            pendingSlag = ItemStack.EMPTY;
        }
        if (!pendingBarrel.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), pendingBarrel.copy());
            pendingBarrel = ItemStack.EMPTY;
        }
        grindProgress = 0;
        grindTotal = 0;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReprocessingGrinderMenu(id, inventory, this, data);
    }
}

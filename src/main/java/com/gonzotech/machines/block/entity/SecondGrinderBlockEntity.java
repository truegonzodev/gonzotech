package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.SecondGrinderMenu;
import com.gonzotech.machines.processing.GrinderRecipes;
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
 * Grinder II: one validated input, one reserved output, and an exact 35 paid
 * ticks per operation. The input is consumed only after the result slot has
 * room and the first 2.6-GTU tick can be paid; a pending result is persisted so
 * an unloaded chunk or a break cannot duplicate or delete material.
 */
public final class SecondGrinderBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    private static final int[] INPUT_SLOTS = { SLOT_INPUT };
    private static final int[] OUTPUT_SLOTS = { SLOT_OUTPUT };

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.GRINDER_GTU_CAPACITY);
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private int grindProgress;
    private int grindTotal;

    // A machine can be offered energy from several connected routes in one tick;
    // this ledger makes 96 GTU/t a total intake cap rather than a per-call cap.
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

    public SecondGrinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECOND_GRINDER.get(), pos, state, 2);
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecondGrinderBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        boolean changed = false;

        if (be.grindTotal == 0 && be.tryStartGrinding()) changed = true;
        if (be.grindTotal > 0 && be.tickGrinding()) changed = true;

        if (changed) be.setChanged();
    }

    /** Reserves output space and a first work tick before consuming one input item. */
    private boolean tryStartGrinding() {
        ItemStack output = GrinderRecipes.find(items.get(SLOT_INPUT));
        if (output == null || !canStore(output)
            || !gtu.has(SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK)) {
            return false;
        }
        items.get(SLOT_INPUT).shrink(1);
        pendingOutput = output;
        grindProgress = 0;
        grindTotal = SecondTierDefs.GRINDER_TICKS;
        return true;
    }

    /** Pays exactly 2.6 GTU for each of the 35 work ticks (91 GTU total). */
    private boolean tickGrinding() {
        if (pendingOutput.isEmpty()) {
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
        if (!canStore(pendingOutput)) return false;
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, pendingOutput.copy());
        } else {
            output.grow(pendingOutput.getCount());
        }
        pendingOutput = ItemStack.EMPTY;
        grindProgress = 0;
        grindTotal = 0;
        return true;
    }

    private boolean canStore(ItemStack incoming) {
        ItemStack output = items.get(SLOT_OUTPUT);
        int limit = Math.min(getMaxStackSize(), incoming.getMaxStackSize());
        return output.isEmpty()
            ? incoming.getCount() <= limit
            : ItemStack.isSameItemSameComponents(output, incoming)
                && output.getCount() + incoming.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    // ─────────────────────────── inventory automation ───────────────────────────

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && GrinderRecipes.accepts(stack);
    }

    /** The sole output is exposed below; every other side is a raw-material input. */
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
        return side == Direction.DOWN && slot == SLOT_OUTPUT;
    }

    // ─────────────────────────── persistence / menu ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("GrindProgress", grindProgress);
        tag.putInt("GrindTotal", grindTotal);
        if (!pendingOutput.isEmpty()) tag.put("PendingOutput", pendingOutput.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        grindProgress = Math.max(0, tag.getInt("GrindProgress"));
        grindTotal = Math.max(0, tag.getInt("GrindTotal"));
        pendingOutput = tag.contains("PendingOutput")
            ? ItemStack.parseOptional(registries, tag.getCompound("PendingOutput"))
            : ItemStack.EMPTY;
        if (pendingOutput.isEmpty()) {
            grindProgress = 0;
            grindTotal = 0;
        } else {
            grindTotal = SecondTierDefs.GRINDER_TICKS;
            grindProgress = Math.min(grindProgress, grindTotal);
        }
    }

    /** A running operation has already consumed its source; keep the reserved result on break. */
    public void dropPendingOutputForBreak() {
        if (level == null || level.isClientSide() || pendingOutput.isEmpty()) return;
        net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
            worldPosition.getZ(), pendingOutput.copy());
        pendingOutput = ItemStack.EMPTY;
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
        return new SecondGrinderMenu(id, inventory, this, data);
    }
}

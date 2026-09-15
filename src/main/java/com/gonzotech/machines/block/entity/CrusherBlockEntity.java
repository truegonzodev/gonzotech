package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.CrusherMenu;
import com.gonzotech.machines.processing.CrusherRecipes;
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
 * Дробилка: принимает только actual ore/rock block-items, выдаёт raw и результаты
 * host-породы. Она не принимает raw, поэтому не может бесконечно перерабатывать
 * собственный output. Все броски и output reservation выполняются только сервером.
 */
public class CrusherBlockEntity extends BaseMachineBlockEntity implements GtuSink, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_PRIMARY_OUTPUT = 1;
    public static final int SLOT_SECONDARY_OUTPUT = 2;
    public static final int SLOT_BYPRODUCT_1 = 3;
    public static final int SLOT_BYPRODUCT_2 = 4;

    private static final int[] OUTPUT_SLOTS = {
        SLOT_PRIMARY_OUTPUT, SLOT_SECONDARY_OUTPUT, SLOT_BYPRODUCT_1, SLOT_BYPRODUCT_2
    };
    private static final int[] SLOTS_ALL = {
        SLOT_INPUT, SLOT_PRIMARY_OUTPUT, SLOT_SECONDARY_OUTPUT, SLOT_BYPRODUCT_1, SLOT_BYPRODUCT_2
    };

    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.CRUSHER_GTU_CAPACITY);
    private int crushProgress;
    private int crushTotal;
    /** Fixed at start from stored GTU, making empty/full endpoints exactly 100/80 ticks. */
    private int crushSpeed;
    private final ItemStack[] pendingOutputs = new ItemStack[] {
        ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };

    /** One machine may receive from several networks in a tick; cap total rather than each call. */
    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> crushProgress;
                case 2 -> crushTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> gtu.set(MachineDefs.toMilli(value));
                case 1 -> crushProgress = Math.max(0, value);
                case 2 -> crushTotal = Math.max(0, value);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state, 5);
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
        long accepted = gtu.receive(Math.min(amount,
            (long) Math.max(0, MachineDefs.CRUSHER_GTU_INTAKE - acceptedGtuThisTick)), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        return accepted;
    }

    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        if (be.crushTotal == 0 && be.tryStartCrush(server)) {
            changed = true;
        }
        if (be.crushTotal > 0 && be.tickCrush()) {
            changed = true;
        }

        if (changed) be.setChanged();
    }

    /** Rolls only after all possible outputs have reserve capacity and the first energy tick can be paid. */
    private boolean tryStartCrush(ServerLevel server) {
        CrusherRecipes.Recipe recipe = CrusherRecipes.find(items.get(SLOT_INPUT));
        if (recipe == null || !canStoreAll(recipe.outputCapacityPreview())) return false;

        int firstTickCost = MachineDefs.crusherGtuMilliPerTick(gtu.amountAsLong());
        if (!gtu.has(firstTickCost)) return false;

        ItemStack[] rolled = recipe.rollOutputs(server.getRandom());
        for (int i = 0; i < pendingOutputs.length; i++) {
            pendingOutputs[i] = rolled[i].copy();
        }
        items.get(SLOT_INPUT).shrink(1);
        crushProgress = 0;
        crushTotal = MachineDefs.CRUSHER_PROGRESS_TOTAL;
        crushSpeed = MachineDefs.crusherProgressUnitsPerTick(gtu.amountAsLong());
        return true;
    }

    /** Pays one tick at the current GTU-dependent rate; prior stored GTU fixed this operation's speed. */
    private boolean tickCrush() {
        if (crushProgress >= crushTotal) return tryFinishCrush();

        int cost = MachineDefs.crusherGtuMilliPerTick(gtu.amountAsLong());
        if (!gtu.has(cost)) return false;

        gtu.extract(cost, false);
        crushProgress = Math.min(crushTotal, crushProgress + Math.max(1, crushSpeed));
        if (crushProgress >= crushTotal) tryFinishCrush();
        return true;
    }

    /** Publishes all pre-rolled outputs atomically to slots 1..4. */
    private boolean tryFinishCrush() {
        if (!canStoreAll(pendingOutputs)) return false;
        for (int i = 0; i < pendingOutputs.length; i++) {
            ItemStack pending = pendingOutputs[i];
            if (pending.isEmpty()) continue;
            ItemStack existing = items.get(OUTPUT_SLOTS[i]);
            if (existing.isEmpty()) {
                items.set(OUTPUT_SLOTS[i], pending.copy());
            } else {
                existing.grow(pending.getCount());
            }
            pendingOutputs[i] = ItemStack.EMPTY;
        }
        crushProgress = 0;
        crushTotal = 0;
        crushSpeed = 0;
        return true;
    }

    private boolean canStoreAll(ItemStack[] outputs) {
        if (outputs.length != OUTPUT_SLOTS.length) return false;
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            ItemStack incoming = outputs[i];
            if (incoming.isEmpty()) continue;
            ItemStack current = items.get(OUTPUT_SLOTS[i]);
            if (current.isEmpty()) {
                if (incoming.getCount() > Math.min(incoming.getMaxStackSize(), getMaxStackSize())) return false;
            } else if (!ItemStack.isSameItemSameComponents(current, incoming)
                || current.getCount() + incoming.getCount() > Math.min(current.getMaxStackSize(), getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && CrusherRecipes.find(stack) != null;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_INPUT && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= SLOT_PRIMARY_OUTPUT && slot <= SLOT_BYPRODUCT_2;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("CrushProgress", crushProgress);
        tag.putInt("CrushTotal", crushTotal);
        tag.putInt("CrushSpeed", crushSpeed);
        for (int i = 0; i < pendingOutputs.length; i++) {
            if (!pendingOutputs[i].isEmpty()) {
                tag.put("PendingOutput" + i, pendingOutputs[i].save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        crushTotal = Math.max(0, tag.getInt("CrushTotal"));
        crushProgress = Math.max(0, tag.getInt("CrushProgress"));
        crushSpeed = Math.max(0, tag.getInt("CrushSpeed"));
        for (int i = 0; i < pendingOutputs.length; i++) {
            pendingOutputs[i] = tag.contains("PendingOutput" + i)
                ? ItemStack.parseOptional(registries, tag.getCompound("PendingOutput" + i))
                : ItemStack.EMPTY;
        }

        boolean hasPending = false;
        for (ItemStack pending : pendingOutputs) {
            if (!pending.isEmpty()) {
                hasPending = true;
                break;
            }
        }
        if (hasPending && crushTotal == 0) crushTotal = MachineDefs.CRUSHER_PROGRESS_TOTAL;
        if (crushTotal > 0) {
            crushTotal = MachineDefs.CRUSHER_PROGRESS_TOTAL;
            crushProgress = Math.min(crushProgress, crushTotal);
            if (crushSpeed == 0) crushSpeed = MachineDefs.crusherProgressUnitsPerTick(gtu.amountAsLong());
        } else {
            crushProgress = 0;
            crushSpeed = 0;
        }
    }

    /**
     * Pending results represent an input already consumed at operation start.
     * Drop them before the base MachineBlock drops ordinary inventory contents,
     * so breaking a running crusher cannot delete server-rolled material.
     */
    public void dropPendingOutputsForBreak() {
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < pendingOutputs.length; i++) {
            ItemStack pending = pendingOutputs[i];
            if (pending.isEmpty()) continue;
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), pending.copy());
            pendingOutputs[i] = ItemStack.EMPTY;
        }
        crushProgress = 0;
        crushTotal = 0;
        crushSpeed = 0;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.crusher");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CrusherMenu(id, inv, this, data);
    }
}

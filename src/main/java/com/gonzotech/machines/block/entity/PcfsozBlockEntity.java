package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.menu.PcfsozMenu;
import com.gonzotech.machines.processing.PCFSOZRecipes;
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
 * Центрифуга ПЦФСОЗ (0.3.89, автор 04.10.2026) — изотопное разделение
 * топливного цикла (жёлтый кек → уран-238 + уран-235).
 *
 * <p>Копия ЦФ1УР ({@link CentrifugeBlockEntity}) с числами автора: стор ГТУ
 * 5202, приём ГТУ 64/т, приём воды/пара 388 mB/т, кипяток 9000 + скрытая вода
 * 8000, нагрев 32 mB/т за 3.2 GTU/т, разделение 480 т за (5.32 GTU + 32 mB
 * кипятка)/тик (ровно, без кривой прогресса). Один цикл потребляет 15360 mB
 * кипятка — больше буфера, поэтому машина требует постоянной подачи.</p>
 */
public class PcfsozBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WaterSink, SteamSink, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_PRIMARY_OUTPUT = 1;
    public static final int SLOT_BYPRODUCT_1 = 2;
    public static final int SLOT_BYPRODUCT_2 = 3;
    public static final int SLOT_BYPRODUCT_3 = 4;

    private static final int[] OUTPUT_SLOTS = {
        SLOT_PRIMARY_OUTPUT, SLOT_BYPRODUCT_1, SLOT_BYPRODUCT_2, SLOT_BYPRODUCT_3
    };
    private static final int[] SLOTS_ALL = {
        SLOT_INPUT, SLOT_PRIMARY_OUTPUT, SLOT_BYPRODUCT_1, SLOT_BYPRODUCT_2, SLOT_BYPRODUCT_3
    };

    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.PCFSOZ_GTU_CAPACITY);
    /** Скрытый от GUI бак обычной воды. */
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.PCFSOZ_WATER_CAPACITY);
    /** Единственная видимая жидкостная шкала: кипяток. */
    private final ResourceBuffer hotWater = new ResourceBuffer(MachineDefs.PCFSOZ_HOT_WATER_CAPACITY);

    /** 0 = нет операции; иначе всегда {@link MachineDefs#PCFSOZ_SEPARATION_TICKS}. */
    private int washTotal;
    private int washProgress;
    private final ItemStack[] pendingOutputs = new ItemStack[] {
        ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };

    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;
    private int acceptedWaterThisTick;
    private int acceptedSteamThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> hotWater.amount();
                case 2 -> washProgress;
                case 3 -> washTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case 0 -> gtu.set(MachineDefs.toMilli(value));
                case 1 -> hotWater.set(value);
                case 2 -> washProgress = Math.max(0, value);
                case 3 -> washTotal = Math.max(0, value);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public PcfsozBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PCFSOZ.get(), pos, state, 5);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ResourceBuffer waterBuffer() {
        return water;
    }

    public ResourceBuffer hotWaterBuffer() {
        return hotWater;
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── resource sinks ───────────────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long accepted = gtu.receive(Math.min(amount,
            (long) Math.max(0, MachineDefs.PCFSOZ_GTU_INTAKE - acceptedGtuThisTick)), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        return accepted;
    }

    @Override
    public long receiveWater(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long accepted = water.receive(Math.min(amount,
            (long) Math.max(0, MachineDefs.PCFSOZ_WATER_INTAKE - acceptedWaterThisTick)), simulate);
        if (!simulate) acceptedWaterThisTick += (int) accepted;
        return accepted;
    }

    /** Пар не буферизуется: каждый принятый mB сразу становится кипятком. */
    @Override
    public long receiveSteam(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long accepted = hotWater.receive(Math.min(amount,
            (long) Math.max(0, MachineDefs.PCFSOZ_STEAM_INTAKE - acceptedSteamThisTick)), simulate);
        if (!simulate) acceptedSteamThisTick += (int) accepted;
        return accepted;
    }

    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
        acceptedWaterThisTick = 0;
        acceptedSteamThisTick = 0;
    }

    // ─────────────────────────── server tick ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, PcfsozBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 1. Фиксированная паразитная утечка GTU.
        if (!be.gtu.isEmpty()) {
            be.gtu.extract(MachineDefs.PCFSOZ_GTU_LOSS_PER_TICK, false);
            changed = true;
        }

        // 2. Кипяток остывает 1:1 обратно в скрытую обычную воду.
        if (!be.hotWater.isEmpty()) {
            be.hotWater.extract(MachineDefs.PCFSOZ_HOT_WATER_COOLING_PER_TICK, false);
            be.water.receive(MachineDefs.PCFSOZ_HOT_WATER_COOLING_PER_TICK, false);
            changed = true;
        }

        // 3. Электрический нагрев 32 mB/т за 3.2 GTU/т.
        if (be.water.has(MachineDefs.PCFSOZ_WATER_TO_HOT_WATER_PER_TICK)
            && be.hotWater.space() >= MachineDefs.PCFSOZ_WATER_TO_HOT_WATER_PER_TICK
            && be.gtu.has(MachineDefs.PCFSOZ_WATER_HEAT_GTU_MILLI_PER_TICK)) {
            be.water.extract(MachineDefs.PCFSOZ_WATER_TO_HOT_WATER_PER_TICK, false);
            be.gtu.extract(MachineDefs.PCFSOZ_WATER_HEAT_GTU_MILLI_PER_TICK, false);
            be.hotWater.receive(MachineDefs.PCFSOZ_WATER_TO_HOT_WATER_PER_TICK, false);
            changed = true;
        }

        // 4. Новая операция / продвижение текущей.
        if (be.washTotal == 0 && be.tryStartWash(server)) {
            changed = true;
        }
        if (be.washTotal > 0 && be.tickWash(server)) {
            changed = true;
        }

        if (changed) be.setChanged();
    }

    private boolean tryStartWash(ServerLevel server) {
        PCFSOZRecipes.Recipe recipe = PCFSOZRecipes.find(items.get(SLOT_INPUT));
        if (recipe == null) return false;

        if (!canStoreAll(recipe.outputCapacityPreview())) return false;
        if (!hotWater.has(MachineDefs.PCFSOZ_HOT_WATER_PER_TICK)
            || !gtu.has(MachineDefs.PCFSOZ_GTU_MILLI_PER_TICK)) return false;

        ItemStack[] rolled = recipe.rollOutputs(server.getRandom());

        items.get(SLOT_INPUT).shrink(1);
        for (int i = 0; i < pendingOutputs.length; i++) {
            pendingOutputs[i] = rolled[i].copy();
        }
        washProgress = 0;
        washTotal = MachineDefs.PCFSOZ_SEPARATION_TICKS;
        return true;
    }

    private boolean tickWash(ServerLevel server) {
        if (washProgress >= washTotal) {
            return tryFinishWash(server);
        }

        if (!hotWater.has(MachineDefs.PCFSOZ_HOT_WATER_PER_TICK)
            || !gtu.has(MachineDefs.PCFSOZ_GTU_MILLI_PER_TICK)) {
            return false;
        }

        hotWater.extract(MachineDefs.PCFSOZ_HOT_WATER_PER_TICK, false);
        gtu.extract(MachineDefs.PCFSOZ_GTU_MILLI_PER_TICK, false);
        washProgress++;
        if (washProgress >= washTotal) {
            tryFinishWash(server);
        }
        return true;
    }

    private boolean tryFinishWash(ServerLevel server) {
        if (!canStoreAll(pendingOutputs)) return false;
        ItemStack primaryOutput = pendingOutputs[0].copy();
        for (int i = 0; i < pendingOutputs.length; i++) {
            ItemStack pending = pendingOutputs[i];
            if (pending.isEmpty()) continue;
            int slot = OUTPUT_SLOTS[i];
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, pending.copy());
            } else {
                existing.grow(pending.getCount());
            }
            pendingOutputs[i] = ItemStack.EMPTY;
        }
        washProgress = 0;
        washTotal = 0;

        // Паритет с ЦФ1УР: цезиевые побочки здесь невозможны, helper безопасно
        // ничего не делает для урана.
        SmeltSideEffects.apply(server, worldPosition, primaryOutput, this);
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

    // ─────────────────────── Container / WorldlyContainer ───────────────────────

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && PCFSOZRecipes.find(stack) != null;
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
        return slot >= SLOT_PRIMARY_OUTPUT && slot <= SLOT_BYPRODUCT_3;
    }

    // ─────────────────────────── persistence ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
        hotWater.save(tag, "HotWater");
        tag.putInt("WashProgress", washProgress);
        tag.putInt("WashTotal", washTotal);
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
        water.load(tag, "Water");
        hotWater.load(tag, "HotWater");
        washTotal = Math.max(0, tag.getInt("WashTotal"));
        washProgress = Math.max(0, tag.getInt("WashProgress"));
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
        if (hasPending && washTotal == 0) washTotal = MachineDefs.PCFSOZ_SEPARATION_TICKS;
        if (washTotal > 0) {
            washTotal = MachineDefs.PCFSOZ_SEPARATION_TICKS;
            washProgress = Math.min(washProgress, washTotal);
        } else {
            washProgress = 0;
        }
    }

    public void dropPendingOutputsForBreak() {
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < pendingOutputs.length; i++) {
            ItemStack pending = pendingOutputs[i];
            if (pending.isEmpty()) continue;
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), pending.copy());
            pendingOutputs[i] = ItemStack.EMPTY;
        }
        washProgress = 0;
        washTotal = 0;
        setChanged();
    }

    // ─────────────────────────── menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.pcfsoz");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new PcfsozMenu(id, inv, this, data);
    }
}

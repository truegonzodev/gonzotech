package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.SteamSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.menu.CentrifugeMenu;
import com.gonzotech.machines.processing.CentrifugeRecipes;
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
 * Центрифуга ЦФ1УР — первая промывочная машина атомной эры.
 *
 * <p>Одна операция занимает ровно 240 тиков, за которые равномерно списываются
 * ровно 366 GTU и 1000 mB кипятка. Броски побочных выходов серверные; до
 * уничтожения входа машина уже знает все четыре результата и атомарно проверяет
 * закреплённые slots. Поэтому заполненный output не превращает редкую побочку в
 * «невидимый ноль», а перезапуск/выгрузка чанка не вызывает повторный бросок.</p>
 */
public class CentrifugeBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WaterSink, SteamSink, WorldlyContainer {

    /** Единственный ввод: raw-руда или один из соответствующих ore block-item. */
    public static final int SLOT_INPUT = 0;
    /** Гарантированная металлическая пыль. */
    public static final int SLOT_PRIMARY_OUTPUT = 1;
    /** Первый, второй и третий независимые побочные результаты. */
    public static final int SLOT_BYPRODUCT_1 = 2;
    public static final int SLOT_BYPRODUCT_2 = 3;
    public static final int SLOT_BYPRODUCT_3 = 4;

    private static final int[] OUTPUT_SLOTS = {
        SLOT_PRIMARY_OUTPUT, SLOT_BYPRODUCT_1, SLOT_BYPRODUCT_2, SLOT_BYPRODUCT_3
    };
    /** Возвращаем все slots: insertion/extraction окончательно ограничены side methods below. */
    private static final int[] SLOTS_ALL = {
        SLOT_INPUT, SLOT_PRIMARY_OUTPUT, SLOT_BYPRODUCT_1, SLOT_BYPRODUCT_2, SLOT_BYPRODUCT_3
    };

    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.CENTRIFUGE_GTU_CAPACITY);
    /** Скрытый от GUI бак обычной воды. */
    private final ResourceBuffer water = new ResourceBuffer(MachineDefs.CENTRIFUGE_WATER_CAPACITY);
    /** Единственная видимая жидкостная шкала: именно кипяток, а не обычная вода. */
    private final ResourceBuffer hotWater = new ResourceBuffer(MachineDefs.CENTRIFUGE_HOT_WATER_CAPACITY);

    /** 0 = нет операции; иначе всегда {@link MachineDefs#CENTRIFUGE_WASH_TICKS}. */
    private int washTotal;
    /** Сколько ресурсно оплаченных тиков операции уже прошло. */
    private int washProgress;
    /**
     * Выпавшие на старте результаты. Их NBT хранится до выдачи, поэтому никакой
     * restart не перебрасывает шанс и не теряет components результата.
     */
    private final ItemStack[] pendingOutputs = new ItemStack[] {
        ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };

    // Per-server-tick intake ledgers. A machine can touch several pipes/sources in
    // one tick, so a per-call clamp alone would not actually enforce the advertised
    // 192 GTU/t and 1000 mB/t maxima.
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

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, state, 5);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    /** Скрытый внутренний бак обычной воды — нужен жидкостной сети и тестам, не GUI. */
    public ResourceBuffer waterBuffer() {
        return water;
    }

    /** Видимый бак кипятка. */
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
            (long) Math.max(0, MachineDefs.CENTRIFUGE_GTU_INTAKE - acceptedGtuThisTick)), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        return accepted;
    }

    @Override
    public long receiveWater(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long accepted = water.receive(Math.min(amount,
            (long) Math.max(0, MachineDefs.CENTRIFUGE_WATER_INTAKE - acceptedWaterThisTick)), simulate);
        if (!simulate) acceptedWaterThisTick += (int) accepted;
        return accepted;
    }

    /** Steam is deliberately not buffered: every accepted mB becomes hot water in the same call. */
    @Override
    public long receiveSteam(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long accepted = hotWater.receive(Math.min(amount,
            (long) Math.max(0, MachineDefs.CENTRIFUGE_STEAM_INTAKE - acceptedSteamThisTick)), simulate);
        if (!simulate) acceptedSteamThisTick += (int) accepted;
        return accepted;
    }

    /** Clears the three intake counters exactly when the server advances to another game tick. */
    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
        acceptedWaterThisTick = 0;
        acceptedSteamThisTick = 0;
    }

    // ─────────────────────────── server tick ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 1. Fixed passive energy leakage. It cannot make the buffer negative.
        if (!be.gtu.isEmpty()) {
            be.gtu.extract(MachineDefs.CENTRIFUGE_GTU_LOSS_PER_TICK, false);
            changed = true;
        }

        // 2. Hot water cools 1:1 back to regular water. The latter is hidden, but
        // remains a real persistent buffer. If it is full, the converted overflow is
        // safely discarded rather than duplicating water or leaving hot water intact.
        if (!be.hotWater.isEmpty()) {
            be.hotWater.extract(MachineDefs.CENTRIFUGE_HOT_WATER_COOLING_PER_TICK, false);
            be.water.receive(MachineDefs.CENTRIFUGE_HOT_WATER_COOLING_PER_TICK, false);
            changed = true;
        }

        // 3. Electrically boil 16 mB from the hidden normal-water buffer when a full
        // exact packet and both buffer space and GTU are present.
        if (be.water.has(MachineDefs.CENTRIFUGE_WATER_TO_HOT_WATER_PER_TICK)
            && be.hotWater.space() >= MachineDefs.CENTRIFUGE_WATER_TO_HOT_WATER_PER_TICK
            && be.gtu.has(MachineDefs.CENTRIFUGE_WATER_HEAT_GTU_MILLI_PER_TICK)) {
            be.water.extract(MachineDefs.CENTRIFUGE_WATER_TO_HOT_WATER_PER_TICK, false);
            be.gtu.extract(MachineDefs.CENTRIFUGE_WATER_HEAT_GTU_MILLI_PER_TICK, false);
            be.hotWater.receive(MachineDefs.CENTRIFUGE_WATER_TO_HOT_WATER_PER_TICK, false);
            changed = true;
        }

        // 4. Pick up a new validated operation, then let it pay its first tick now.
        // No recipe lookup or random roll happens while a persisted operation exists.
        if (be.washTotal == 0 && be.tryStartWash(server)) {
            changed = true;
        }
        if (be.washTotal > 0 && be.tickWash()) {
            changed = true;
        }

        if (changed) be.setChanged();
    }

    /**
     * Rolls outputs once, verifies every result's dedicated target slot, and only
     * then consumes one input. No output or input changes on failed capacity check.
     */
    private boolean tryStartWash(ServerLevel server) {
        CentrifugeRecipes.Recipe recipe = CentrifugeRecipes.find(items.get(SLOT_INPUT));
        if (recipe == null) return false;

        // Do this BEFORE touching server RNG. All four fixed slots must have room
        // for their possible item, otherwise a blocked machine would silently
        // advance chance rolls despite not having started an operation.
        if (!canStoreAll(recipe.outputCapacityPreview())) return false;
        // Do not lock/consume an input in an entirely unpowered or dry machine.
        // Once begun, the operation can still pause safely at a later missing tick.
        if (!hotWater.has(MachineDefs.centrifugeHotWaterCostForProgressTick(0))
            || !gtu.has(MachineDefs.CENTRIFUGE_WASH_GTU_MILLI_PER_TICK)) return false;

        ItemStack[] rolled = recipe.rollOutputs(server.getRandom());

        items.get(SLOT_INPUT).shrink(1);
        for (int i = 0; i < pendingOutputs.length; i++) {
            pendingOutputs[i] = rolled[i].copy();
        }
        washProgress = 0;
        washTotal = MachineDefs.CENTRIFUGE_WASH_TICKS;
        return true;
    }

    /** Advances a running wash by at most one paid tick; it simply pauses when a resource is missing. */
    private boolean tickWash() {
        // The saved output is emitted only after all 240 costs were paid. This extra
        // capacity check guards against malformed NBT/external mods without loss.
        if (washProgress >= washTotal) {
            return tryFinishWash();
        }

        int waterCost = MachineDefs.centrifugeHotWaterCostForProgressTick(washProgress);
        if (!hotWater.has(waterCost) || !gtu.has(MachineDefs.CENTRIFUGE_WASH_GTU_MILLI_PER_TICK)) {
            return false;
        }

        hotWater.extract(waterCost, false);
        gtu.extract(MachineDefs.CENTRIFUGE_WASH_GTU_MILLI_PER_TICK, false);
        washProgress++;
        if (washProgress >= washTotal) {
            tryFinishWash();
        }
        return true;
    }

    /** Atomically publishes all pending results into their four fixed slots. */
    private boolean tryFinishWash() {
        if (!canStoreAll(pendingOutputs)) return false;
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
        return true;
    }

    /** Validates all four designated target slots without mutating any item stack. */
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
        // Player menus, hoppers and item pipes share the same accepted-input rule.
        return slot == SLOT_INPUT && CentrifugeRecipes.find(stack) != null;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        // ItemRouting first asks this list, then asks the directional methods below.
        // Including input permits insertion; canTake below still forbids extracting it.
        return SLOTS_ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_INPUT && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Automation may take only the primary dust and the three byproduct slots.
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

        // Never permit invalid/malformed progress values to pay extra or discard a
        // persisted result. A saved pending result represents a real already-rolled
        // operation, so it resumes as a standard 240-tick wash rather than rerolling.
        boolean hasPending = false;
        for (ItemStack pending : pendingOutputs) {
            if (!pending.isEmpty()) {
                hasPending = true;
                break;
            }
        }
        if (hasPending && washTotal == 0) washTotal = MachineDefs.CENTRIFUGE_WASH_TICKS;
        if (washTotal > 0) {
            washTotal = MachineDefs.CENTRIFUGE_WASH_TICKS;
            washProgress = Math.min(washProgress, washTotal);
        } else {
            washProgress = 0;
        }
    }

    // ─────────────────────────── menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.centrifuge");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CentrifugeMenu(id, inv, this, data);
    }
}

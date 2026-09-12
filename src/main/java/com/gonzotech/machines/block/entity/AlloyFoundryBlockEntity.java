package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.AlloyFoundryMenu;
import com.gonzotech.machines.processing.AlloyFoundryRecipes;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Завод сплавов II: 5×5 (25 ячеек) входная сетка, GTU-буфер и один нижний выход.
 *
 * <p>После проверки всего состава входы списываются атомарно, а вычисленный
 * результат сохраняется до конца оплачиваемой операции. Это не позволяет
 * изменениям сетки или перезагрузке чанка поменять уже начатую плавку. Время
 * операции равно 80 тикам плюс 8 тиков на каждый предмет исходной 5×5 сетки.</p>
 */
public final class AlloyFoundryBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer {

    public static final int GRID_WIDTH = 5;
    public static final int GRID_HEIGHT = 5;
    public static final int INPUT_SLOTS = GRID_WIDTH * GRID_HEIGHT;
    public static final int SLOT_OUTPUT = INPUT_SLOTS;

    private static final int[] INPUT_SLOT_IDS = inputSlots();
    private static final int[] OUTPUT_SLOT_IDS = { SLOT_OUTPUT };

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.ALLOY_FOUNDRY_GTU_CAPACITY);

    /** Result fixed at operation start; it survives a chunk unload until it is published. */
    private ItemStack pendingOutput = ItemStack.EMPTY;
    /** Number of energy-paid operation ticks already completed. */
    private int alloyProgress;
    /** Total paid ticks for {@link #pendingOutput}; zero means that no operation is running. */
    private int alloyTotal;

    // A single machine can receive from several wires/networks during one server
    // tick. This ledger enforces the advertised total 220 GTU/t rather than only
    // applying that cap separately to every receiveGtu call.
    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> alloyProgress;
                case 2 -> alloyTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case 0 -> gtu.set(MachineDefs.toMilli(Math.max(0, value)));
                case 1 -> alloyProgress = Math.max(0, value);
                case 2 -> alloyTotal = Math.max(0, value);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public AlloyFoundryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALLOY_FOUNDRY.get(), pos, state, INPUT_SLOTS + 1);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── GTU intake ───────────────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        resetIntakeLedgerIfNeeded();
        long remainingBudget = Math.max(0L,
            (long) SecondTierDefs.ALLOY_FOUNDRY_GTU_INTAKE - acceptedGtuThisTick);
        long accepted = gtu.receive(Math.min(amount, remainingBudget), simulate);
        if (!simulate) acceptedGtuThisTick += (int) accepted;
        return accepted;
    }

    /** Resets the aggregate intake cap only when the server enters a new game tick. */
    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
    }

    // ─────────────────────────── server processing ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlloyFoundryBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        boolean changed = false;

        // A new recipe is only locked once it can pay its first working tick.
        // Until then the player/automation can still rearrange the 5×5 input grid.
        if (be.alloyTotal == 0 && be.tryStartAlloy()) {
            changed = true;
        }
        if (be.alloyTotal > 0 && be.tickAlloy()) {
            changed = true;
        }

        if (changed) be.setChanged();
    }

    /**
     * Validates the full grid and reserves room for the output before consuming
     * anything. Input is taken at start so a completed batch cannot be altered by
     * pipes during the operation.
     */
    private boolean tryStartAlloy() {
        AlloyFoundryRecipes.Batch batch = AlloyFoundryRecipes.find(inputGrid());
        if (batch == null || !canStore(batch.output())
            || !gtu.has(SecondTierDefs.ALLOY_FOUNDRY_GTU_MILLI_PER_TICK)) {
            return false;
        }

        for (Map.Entry<Item, Integer> need : batch.ingredients().entrySet()) {
            consume(need.getKey(), need.getValue());
        }
        pendingOutput = batch.output();
        alloyProgress = 0;
        alloyTotal = SecondTierDefs.alloyFoundryTicksForIngredients(batch.ingredientItemCount());
        return true;
    }

    /** Pays one exact 2.8-GTU work tick, then publishes the reserved result at completion. */
    private boolean tickAlloy() {
        if (pendingOutput.isEmpty()) {
            // Defensive recovery for malformed legacy NBT: no result means no real
            // operation can exist, so never burn GTU against an empty transaction.
            alloyProgress = 0;
            alloyTotal = 0;
            return true;
        }
        if (alloyProgress >= alloyTotal) return tryFinishAlloy();
        if (!gtu.has(SecondTierDefs.ALLOY_FOUNDRY_GTU_MILLI_PER_TICK)) return false;

        gtu.extract(SecondTierDefs.ALLOY_FOUNDRY_GTU_MILLI_PER_TICK, false);
        alloyProgress++;
        if (alloyProgress >= alloyTotal) {
            tryFinishAlloy();
        }
        return true;
    }

    /** Output was reserved at start, but validate again to protect against malformed external inventory writes. */
    private boolean tryFinishAlloy() {
        if (!canStore(pendingOutput)) return false;
        store(pendingOutput);
        pendingOutput = ItemStack.EMPTY;
        alloyProgress = 0;
        alloyTotal = 0;
        return true;
    }

    /** Current 5×5 input view; the output slot is deliberately excluded. */
    private Iterable<ItemStack> inputGrid() {
        return items.subList(0, INPUT_SLOTS);
    }

    private boolean canStore(ItemStack incoming) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return incoming.getCount() <= Math.min(incoming.getMaxStackSize(), getMaxStackSize());
        }
        return ItemStack.isSameItemSameComponents(output, incoming)
            && output.getCount() + incoming.getCount() <= Math.min(output.getMaxStackSize(), getMaxStackSize());
    }

    private void consume(Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < INPUT_SLOTS && remaining > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        if (remaining != 0) {
            throw new IllegalStateException("Validated alloy input vanished during transaction");
        }
    }

    private void store(ItemStack incoming) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, incoming.copy());
        } else {
            output.grow(incoming.getCount());
        }
    }

    // ─────────────────────────── inventory automation ───────────────────────────

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < INPUT_SLOTS && isCurrentIngredient(stack);
    }

    /** The factory accepts every catalogued material form; matching is server-side. */
    private static boolean isCurrentIngredient(ItemStack stack) {
        return AlloyFoundryRecipes.isSupportedInput(stack);
    }

    /**
     * Only the bottom face is output-facing. Every other face exposes input slots
     * only, which lets hoppers and the existing item pipes/nodes distinguish the
     * factory's result path from its material-loading paths without special cases.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOT_IDS : INPUT_SLOT_IDS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != null && side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot == SLOT_OUTPUT;
    }

    // ─────────────────────────── persistence ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("AlloyProgress", alloyProgress);
        tag.putInt("AlloyTotal", alloyTotal);
        if (!pendingOutput.isEmpty()) {
            tag.put("PendingOutput", pendingOutput.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        alloyProgress = Math.max(0, tag.getInt("AlloyProgress"));
        alloyTotal = Math.max(0, tag.getInt("AlloyTotal"));
        pendingOutput = tag.contains("PendingOutput")
            ? ItemStack.parseOptional(registries, tag.getCompound("PendingOutput"))
            : ItemStack.EMPTY;

        // Never invent an output or let an incomplete NBT record pay forever.
        // Valid saves always contain an exact total; the base time is merely a
        // safe recovery value for an otherwise real persisted pending output.
        if (pendingOutput.isEmpty()) {
            alloyProgress = 0;
            alloyTotal = 0;
        } else {
            if (alloyTotal <= 0) alloyTotal = SecondTierDefs.ALLOY_FOUNDRY_BASE_TICKS;
            alloyProgress = Math.min(alloyProgress, alloyTotal);
        }
    }

    /**
     * A running batch has already consumed its ingredients, therefore its reserved
     * result must be dropped if the factory is broken before completion. Called by
     * {@link com.gonzotech.machines.block.AlloyFoundryBlock} before normal slots.
     */
    public void dropPendingOutputForBreak() {
        if (level != null && !level.isClientSide && !pendingOutput.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), pendingOutput);
            pendingOutput = ItemStack.EMPTY;
            alloyProgress = 0;
            alloyTotal = 0;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AlloyFoundryMenu(id, inv, this, data);
    }

    private static int[] inputSlots() {
        int[] slots = new int[INPUT_SLOTS];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }
}

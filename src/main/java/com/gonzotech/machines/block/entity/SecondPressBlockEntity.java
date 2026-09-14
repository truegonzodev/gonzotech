package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.SecondPressMenu;
import com.gonzotech.machines.processing.PressRecipes;
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
 * Tier-two Press with an immediate stamping stroke and a 60-tick punch-return
 * cooldown. The form and punch stay in their slots forever; every completed
 * fatigue cycle immediately attempts the next valid operation.
 */
public final class SecondPressBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_PUNCH = 2;
    public static final int SLOT_FORM = 3;

    private static final int[] INPUT_SLOTS = { SLOT_INPUT };
    private static final int[] OUTPUT_SLOTS = { SLOT_OUTPUT };

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.PRESS_GTU_CAPACITY);
    /** Number of elapsed punch-return ticks; a fresh strike starts visibly at 0. */
    private int fatigueProgress;
    /** Distinguishes an idle 0% bar from an active cooldown that has just begun. */
    private boolean fatigueActive;

    // The entire machine must not accept more than 128 GTU in a server tick,
    // even when several wires/nodes invoke receiveGtu independently.
    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> fatigueProgress;
                case 2 -> SecondTierDefs.PRESS_FATIGUE_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> gtu.set(MachineDefs.toMilli(Math.max(0, value)));
                case 1 -> fatigueProgress = Math.max(0,
                    Math.min(SecondTierDefs.PRESS_FATIGUE_TICKS, value));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public SecondPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECOND_PRESS.get(), pos, state, 4);
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
        long remaining = Math.max(0L, (long) SecondTierDefs.PRESS_GTU_INTAKE - acceptedGtuThisTick);
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecondPressBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        boolean changed = false;

        // Unlike a furnace operation, this is deliberately not energy-gated: the
        // return stroke is a physical cooldown after an already-paid stamp.
        if (be.fatigueActive) {
            be.fatigueProgress = Math.min(SecondTierDefs.PRESS_FATIGUE_TICKS, be.fatigueProgress + 1);
            changed = true;
            if (be.fatigueProgress >= SecondTierDefs.PRESS_FATIGUE_TICKS) {
                be.fatigueProgress = 0;
                be.fatigueActive = false;
            } else {
                if (changed) be.setChanged();
                return;
            }
        }

        // A complete return stroke falls through on this same tick. If a matching
        // input, output space, and 126 GTU are ready, the next result is immediate.
        if (be.tryPress()) changed = true;
        if (changed) be.setChanged();
    }

    /** Executes one whole atomic stamp: reserve output, pay 126 GTU, consume one source, publish result. */
    private boolean tryPress() {
        ItemStack result = PressRecipes.find(items.get(SLOT_INPUT), items.get(SLOT_FORM), items.get(SLOT_PUNCH));
        if (result == null || !canStore(result) || !gtu.has(SecondTierDefs.PRESS_GTU_PER_STAMP)) return false;

        gtu.extract(SecondTierDefs.PRESS_GTU_PER_STAMP, false);
        items.get(SLOT_INPUT).shrink(1);
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, result.copy());
        } else {
            output.grow(result.getCount());
        }
        // The just-completed strike starts a new 60-tick recovery period. Forms
        // and punches are never mutated or consumed.
        fatigueProgress = 0;
        fatigueActive = true;
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
        return switch (slot) {
            case SLOT_INPUT -> PressRecipes.acceptsInput(stack);
            case SLOT_FORM -> PressRecipes.isForm(stack);
            case SLOT_PUNCH -> PressRecipes.isPunch(stack);
            default -> false;
        };
    }

    /** Hoppers/nodes may extract only the finished product from below. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    /** Non-bottom automation can load raw material only; it cannot alter tool selectors. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != null && side != Direction.DOWN && slot == SLOT_INPUT && canPlaceItem(slot, stack);
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
        tag.putInt("FatigueProgress", fatigueProgress);
        tag.putBoolean("FatigueActive", fatigueActive);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        fatigueProgress = Math.max(0, Math.min(SecondTierDefs.PRESS_FATIGUE_TICKS,
            tag.getInt("FatigueProgress")));
        fatigueActive = tag.getBoolean("FatigueActive") || fatigueProgress > 0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SecondPressMenu(id, inventory, this, data);
    }
}

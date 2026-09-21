package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.NuclearDefs;
import com.gonzotech.machines.menu.NuclearFireboxMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.nuclear.ThermalHazards;
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
 * A one-slot Discovery-2 nuclear firebox. Accepted uranium/thorium items burn at
 * a constant 262 GTH/t; a hotter internal GTH buffer shortens the next fuel burn
 * by up to 25 percent. Its heat must be exported or absorbed before meltdown.
 */
public final class NuclearFireboxBlockEntity extends BaseMachineBlockEntity implements WorldlyContainer {

    public static final int SLOT_FUEL = 0;
    private static final int[] FUEL_SLOT = {SLOT_FUEL};
    private static final int GUI_GTH_BASE = 1_000;

    private final GtBuffer gth = new GtBuffer((long) NuclearDefs.NUCLEAR_FIREBOX_GTH_CAPACITY);
    private int litTime;
    private int litDuration;
    private int lastComparatorOutput;
    /**
     * Ticks the buffer has been above the corium threshold without a single
     * reset; at {@link NuclearDefs#NUCLEAR_FIREBOX_SELF_MELT_GRACE_TICKS} the
     * firebox block itself becomes eligible for melting.
     */
    private int overheatTicks;

    /**
     * [GTH remainder, GTH thousands, lit remainder, lit thousands, duration
     * remainder, duration thousands]. ContainerData transmits signed shorts, so
     * the 162,000-tick uranium-block burn duration must be split just like GTH.
     */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int units = gth.amountUnitsInt();
            return switch (index) {
                case 0 -> units % GUI_GTH_BASE;
                case 1 -> units / GUI_GTH_BASE;
                case 2 -> litTime % GUI_GTH_BASE;
                case 3 -> litTime / GUI_GTH_BASE;
                case 4 -> litDuration % GUI_GTH_BASE;
                case 5 -> litDuration / GUI_GTH_BASE;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0 || index == 1) {
                int units = index == 0
                    ? data.get(1) * GUI_GTH_BASE + Math.max(0, value)
                    : Math.max(0, value) * GUI_GTH_BASE + data.get(0);
                gth.set((long) units * MachineDefs.MILLI);
            } else if (index == 2 || index == 3) {
                litTime = index == 2
                    ? data.get(3) * GUI_GTH_BASE + Math.max(0, value)
                    : Math.max(0, value) * GUI_GTH_BASE + data.get(2);
            } else if (index == 4 || index == 5) {
                litDuration = index == 4
                    ? data.get(5) * GUI_GTH_BASE + Math.max(0, value)
                    : Math.max(0, value) * GUI_GTH_BASE + data.get(4);
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public NuclearFireboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NUCLEAR_FIREBOX.get(), pos, state, 1);
    }

    public ContainerData data() {
        return data;
    }

    public int storedGth() {
        return gth.amountUnitsInt();
    }

    public int gthCapacity() {
        return MachineDefs.toUnits(NuclearDefs.NUCLEAR_FIREBOX_GTH_CAPACITY);
    }

    public int litTime() {
        return litTime;
    }

    public int litDuration() {
        return litDuration;
    }

    /** Only the specified three-band reactor warning comparator policy. */
    public int comparatorOutput() {
        long stored = gth.amountAsLong();
        if (stored > NuclearDefs.NUCLEAR_FIREBOX_CORIUM_THRESHOLD) return 15;
        return stored > NuclearDefs.NUCLEAR_FIREBOX_IGNITION_THRESHOLD ? 1 : 0;
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

    /**
     * Единственная точка приёма топлива (слот, {@code canPlaceItem} и воронка
     * спрашивают именно её). Топка Discovery-2 работает только на ПРИРОДНОМ
     * уране и тории — слиток, самородок, блок, пыль и сырая руда
     * (уранинит/торианит — тоже природная форма, оставлена автором 21.09).
     * <p>
     * Всё наработанное ядерное топливо и изотопы ({@code uranium_238/235/233},
     * {@code weapons_plutonium}, {@code plutonium_238/242}, {@code thorium_229},
     * {@code uranium_fuel}, {@code mox_fuel}, {@code tmox_fuel}, {@code snup_fuel},
     * {@code ut_fuel}) сюда НЕ принимается by design: это сырьё будущих
     * продвинутых реакторов, а не топливо «природной» топки.
     */
    public static boolean isNuclearFuel(ItemStack stack) {
        return burnTicks(stack) > 0;
    }

    /** Base durations specified in seconds, converted once to game ticks. */
    public static int burnTicks(ItemStack stack) {
        if (stack.is(ModItems.INGOT_ITEMS.get("uranium_ingot").get())) return NuclearDefs.URANIUM_INGOT_BURN_TICKS;
        if (stack.is(ModItems.NUGGET_ITEMS.get("uranium_nugget").get())) return NuclearDefs.URANIUM_NUGGET_BURN_TICKS;
        if (stack.is(ModItems.METAL_BLOCK_ITEMS.get("uranium_block").get())) return NuclearDefs.URANIUM_BLOCK_BURN_TICKS;
        if (stack.is(ModItems.DUST_ITEMS.get("uranium_dust").get())) return NuclearDefs.URANIUM_DUST_BURN_TICKS;
        if (stack.is(ModItems.RAW_ORE_ITEMS.get("uranium").get())) return NuclearDefs.URANINITE_BURN_TICKS;
        if (stack.is(ModItems.INGOT_ITEMS.get("thorium_ingot").get())) return NuclearDefs.THORIUM_INGOT_BURN_TICKS;
        if (stack.is(ModItems.NUGGET_ITEMS.get("thorium_nugget").get())) return NuclearDefs.THORIUM_NUGGET_BURN_TICKS;
        if (stack.is(ModItems.METAL_BLOCK_ITEMS.get("thorium_block").get())) return NuclearDefs.THORIUM_BLOCK_BURN_TICKS;
        if (stack.is(ModItems.DUST_ITEMS.get("thorium_dust").get())) return NuclearDefs.THORIUM_DUST_BURN_TICKS;
        if (stack.is(ModItems.RAW_ORE_ITEMS.get("thorium").get())) return NuclearDefs.THORIANITE_BURN_TICKS;
        return 0;
    }

    /**
     * At or below 22,040 GTH fuel keeps its exact base duration. Above it, the
     * duration scales linearly to 75% at 68,408 GTH. The factor is fixed when a
     * fuel item starts burning, so the GUI's flame has a stable total duration.
     */
    private int adjustedBurnTicks(int baseTicks) {
        long stored = gth.amountAsLong();
        if (stored <= NuclearDefs.NUCLEAR_FIREBOX_ACCELERATION_START) return baseTicks;
        long span = NuclearDefs.NUCLEAR_FIREBOX_ACCELERATION_END - NuclearDefs.NUCLEAR_FIREBOX_ACCELERATION_START;
        long above = Math.min(span, stored - NuclearDefs.NUCLEAR_FIREBOX_ACCELERATION_START);
        long reduction = (long) NuclearDefs.NUCLEAR_FIREBOX_MAX_BURN_REDUCTION_PERMILLE * above / span;
        return Math.max(1, (int) ((long) baseTicks * (1_000L - reduction) / 1_000L));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NuclearFireboxBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        if (be.litTime > 0) {
            be.litTime--;
            be.gth.receive(NuclearDefs.NUCLEAR_FIREBOX_GTH_PER_TICK, false);
            changed = true;
        }

        if (be.litTime <= 0) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            int baseBurn = burnTicks(fuel);
            if (baseBurn > 0) {
                be.litDuration = be.adjustedBurnTicks(baseBurn);
                be.litTime = be.litDuration;
                fuel.shrink(1);
                changed = true;
            }
        }

        if (!be.gth.isEmpty()) {
            be.gth.extract(NuclearDefs.NUCLEAR_FIREBOX_GTH_LOSS, false);
            changed = true;
        }

        // Above the thresholds the machine rolls once per second for a 5%
        // ignition and a 3% melt of one random block in the 3×3×3 around it.
        // The first twenty seconds above 64,000 GTH the firebox block itself
        // is protected; a sustained overheat (no drop below the threshold)
        // makes the firebox itself a melt candidate.
        if (be.gth.amountAsLong() > NuclearDefs.NUCLEAR_FIREBOX_CORIUM_THRESHOLD) {
            be.overheatTicks++;
            boolean selfEligible = be.overheatTicks >= NuclearDefs.NUCLEAR_FIREBOX_SELF_MELT_GRACE_TICKS;
            ThermalHazards.maybeMeltToCorium(server, pos, selfEligible);
            changed = true;
        } else {
            // Any drop back below 64,000 GTH restarts the grace timer.
            be.overheatTicks = 0;
        }
        if (be.gth.amountAsLong() > NuclearDefs.NUCLEAR_FIREBOX_IGNITION_THRESHOLD) {
            ThermalHazards.maybeIgniteAround(server, pos);
        }

        if (be.pushGth(server, pos)) changed = true;
        if (changed) {
            be.setChanged();
            be.updateComparatorOutput();
        }
    }

    private boolean pushGth(Level level, BlockPos pos) {
        if (gth.isEmpty()) return false;
        long budget = Math.min((long) NuclearDefs.NUCLEAR_FIREBOX_GTH_OUTPUT, gth.amountAsLong());
        long moved = PipeRouting.drain(level, pos, PipeType.HEAT, budget, level.getGameTime(), (target, ignored) -> {
            if (target instanceof NuclearFireboxBlockEntity) return null;
            if (target instanceof com.gonzotech.machines.energy.Sinks.GthSink sink) return sink::receiveGth;
            return null;
        });
        if (moved <= 0) return false;
        gth.extract(moved, false);
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && isNuclearFuel(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return FUEL_SLOT;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gth.save(tag, "Gth");
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("OverheatTicks", overheatTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gth.load(tag, "Gth");
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        overheatTicks = tag.getInt("OverheatTicks");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.nuclear_firebox");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new NuclearFireboxMenu(id, inv, this, data);
    }
}

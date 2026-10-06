package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.block.SecondLumberBlock;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.SecondLumberMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Redstone-controlled linear lumber machine. It checks the eight blocks in front
 * in nearest-to-farthest order and only works on registry names ending in
 * {@code _wood} or {@code _log}. A cut takes 55 paid work ticks and 56 GTU;
 * the separate 0.005 GTU/t parasitic draw applies while redstone-powered.
 */
public final class SecondLumberBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer {

    public static final int SLOT_AXE = 0;
    private static final int[] AXE_SLOTS = { SLOT_AXE };

    private final GtBuffer gtu = new GtBuffer(SecondTierDefs.SECOND_LUMBER_GTU_CAPACITY);

    /** Next distance to search after the current cut; valid range is [1, depth + 1]. */
    private int searchDistance = 1;
    /** Distance currently being cut, or zero when the machine is between blocks. */
    private int workingDistance;
    private int cutProgress;

    // Enforce the intake ceiling across all pipe callbacks received during one game tick.
    private long intakeBudgetTick = Long.MIN_VALUE;
    private int acceptedGtuThisTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> cutProgress;
                case 2 -> workingDistance == 0 ? 0 : SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK;
                case 3 -> level != null && level.hasNeighborSignal(worldPosition) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> gtu.set(MachineDefs.toMilli(Math.max(0, value)));
                case 1 -> cutProgress = Math.max(0, Math.min(SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK, value));
                case 2 -> { /* total is a constant derived from workingDistance */ }
                case 3 -> { /* redstone status is read from the level */ }
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SecondLumberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECOND_LUMBER.get(), pos, state, 1);
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
        long remaining = Math.max(0L,
            (long) SecondTierDefs.SECOND_LUMBER_GTU_INTAKE - acceptedGtuThisTick);
        long accepted = gtu.receive(Math.min(amount, remaining), simulate);
        if (!simulate && accepted > 0) {
            acceptedGtuThisTick += (int) accepted;
            setChanged();
        }
        return accepted;
    }

    private void resetIntakeLedgerIfNeeded() {
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (tick == intakeBudgetTick) return;
        intakeBudgetTick = tick;
        acceptedGtuThisTick = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecondLumberBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;
        boolean powered = server.hasNeighborSignal(pos);

        if (state.hasProperty(SecondLumberBlock.ACTIVE)
            && state.getValue(SecondLumberBlock.ACTIVE) != powered) {
            server.setBlock(pos, state.setValue(SecondLumberBlock.ACTIVE, powered), Block.UPDATE_CLIENTS);
            changed = true;
        }

        if (powered) {
            // The parasitic draw is paid on every powered tick, including idle/no-axe ticks.
            if (be.gtu.extract(SecondTierDefs.SECOND_LUMBER_PARASITIC_MILLI_PER_TICK, false) > 0) {
                changed = true;
            }

            int oldSearchDistance = be.searchDistance;
            int oldWorkingDistance = be.workingDistance;
            int oldProgress = be.cutProgress;
            BlockPos target = be.getOrFindTarget(server, pos, state.getValue(SecondLumberBlock.FACING));
            if (oldSearchDistance != be.searchDistance || oldWorkingDistance != be.workingDistance
                || oldProgress != be.cutProgress) {
                changed = true;
            }
            if (target != null && !be.items.get(SLOT_AXE).isEmpty()) {
                int workCost = workCostForTick(be.cutProgress);
                if (be.gtu.has(workCost)) {
                    be.gtu.extract(workCost, false);
                    be.cutProgress++;
                    changed = true;

                    if (be.cutProgress >= SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK) {
                        be.finishCut(server, target);
                        changed = true;
                    }
                }
            }
        }

        if (changed) be.setChanged();
    }

    /**
     * Returns the active target, starting the next eligible cut if needed. Unloaded
     * chunks pause the scan rather than being force-loaded or skipped over.
     */
    private BlockPos getOrFindTarget(ServerLevel level, BlockPos machinePos, Direction facing) {
        if (workingDistance > 0) {
            BlockPos current = machinePos.relative(facing, workingDistance);
            if (!level.hasChunkAt(current)) return null;
            if (isLumberBlock(level.getBlockState(current))) return current;

            // The target was externally removed/replaced while progress was stored.
            searchDistance = workingDistance;
            workingDistance = 0;
            cutProgress = 0;
        }

        if (searchDistance > SecondTierDefs.SECOND_LUMBER_DEPTH) searchDistance = 1;
        for (int distance = searchDistance; distance <= SecondTierDefs.SECOND_LUMBER_DEPTH; distance++) {
            BlockPos candidate = machinePos.relative(facing, distance);
            if (!level.hasChunkAt(candidate)) return null;
            if (isLumberBlock(level.getBlockState(candidate))) {
                workingDistance = distance;
                cutProgress = 0;
                return candidate;
            }
        }

        // A completed scan wraps so newly placed logs in the near part of the lane are found.
        searchDistance = 1;
        return null;
    }

    private static boolean isLumberBlock(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_wood") || path.endsWith("_log");
    }

    /** Distributes 56,000 mGTU across 55 ticks without losing rounding remainders. */
    private static int workCostForTick(int completedTicks) {
        int completed = Math.max(0, Math.min(SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK, completedTicks));
        int next = Math.min(SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK, completed + 1);
        long before = (long) SecondTierDefs.SECOND_LUMBER_GTU_PER_BLOCK * MachineDefs.MILLI
            * completed / SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK;
        long after = (long) SecondTierDefs.SECOND_LUMBER_GTU_PER_BLOCK * MachineDefs.MILLI
            * next / SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK;
        return (int) (after - before);
    }

    private void finishCut(ServerLevel level, BlockPos target) {
        int finishedDistance = workingDistance;
        if (level.destroyBlock(target, true)) {
            ItemStack axe = items.get(SLOT_AXE);
            if (!axe.isEmpty()) {
                axe.hurtAndBreak(1, level, null, brokenItem -> items.set(SLOT_AXE, ItemStack.EMPTY));
            }
            searchDistance = finishedDistance + 1;
            if (searchDistance > SecondTierDefs.SECOND_LUMBER_DEPTH) searchDistance = 1;
        } else {
            // If a block-break hook vetoes the cut, retry from the same near-to-far position.
            searchDistance = finishedDistance;
        }
        workingDistance = 0;
        cutProgress = 0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_AXE && stack.is(ItemTags.AXES);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return AXE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_AXE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("SearchDistance", searchDistance);
        tag.putInt("WorkingDistance", workingDistance);
        tag.putInt("CutProgress", cutProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        searchDistance = Math.max(1, Math.min(SecondTierDefs.SECOND_LUMBER_DEPTH + 1,
            tag.getInt("SearchDistance")));
        workingDistance = Math.max(0, Math.min(SecondTierDefs.SECOND_LUMBER_DEPTH,
            tag.getInt("WorkingDistance")));
        cutProgress = workingDistance == 0 ? 0 : Math.max(0,
            Math.min(SecondTierDefs.SECOND_LUMBER_TICKS_PER_BLOCK - 1, tag.getInt("CutProgress")));
        if (workingDistance > 0) searchDistance = workingDistance;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.gonzotech.second_lumber");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SecondLumberMenu(id, inventory, this, data);
    }
}

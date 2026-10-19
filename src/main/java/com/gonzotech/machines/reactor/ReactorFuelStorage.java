package com.gonzotech.machines.reactor;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.block.entity.ReactorFuelSheathBlockEntity;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fuel ownership, migration and conservative break-time reallocation for reactor sheaths. */
public final class ReactorFuelStorage {

    private ReactorFuelStorage() {
    }

    /** Collect only already-loaded sheath block entities in the saved reactor volume. */
    public static List<ReactorFuelSheathBlockEntity> collectSheaths(ServerLevel level,
                                                                    BlockPos min,
                                                                    BlockPos max) {
        if (level == null || min == null || max == null || !chunksLoaded(level, min, max)) return List.of();
        List<ReactorFuelSheathBlockEntity> result = new ArrayList<>();
        for (int x = min.getX() + 1; x < max.getX(); x++) {
            for (int y = min.getY() + 1; y < max.getY(); y++) {
                for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get())) continue;
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity instanceof ReactorFuelSheathBlockEntity sheath) result.add(sheath);
                }
            }
        }
        sort(result);
        return result;
    }

    /** Avoid opening a proxy menu when some of its persistent sheath containers are unloaded. */
    public static boolean canOpenFuelInventory(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        return level != null && panel != null && panel.isFormedController()
            && chunksLoaded(level, panel.min(), panel.max());
    }

    /** Binds the live sheath set, preserving its items and importing any pre-0.3.238 panel inventory. */
    public static int bindStructure(ServerLevel level,
                                    BlockPos min,
                                    BlockPos max,
                                    List<ItemStack> legacyPanelItems) {
        List<ReactorFuelSheathBlockEntity> sheaths = collectSheaths(level, min, max);
        if (sheaths.isEmpty()) return -1;

        List<ItemStack> allFuel = collectFuelItems(sheaths);
        appendSingleTvels(allFuel, legacyPanelItems);
        // Preserve every item from legacy/malformed NBT; the normal GUI can create at most 18.
        redistribute(allFuel, sheaths, min, max);
        return allFuel.size();
    }

    public static int totalTvels(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (level == null || panel == null || !panel.isFormedController()) return 0;
        return countActiveTvels(collectFuelItems(collectSheaths(level, panel.min(), panel.max())));
    }

    public static int totalTvels(List<ReactorFuelSheathBlockEntity> sheaths) {
        return countActiveTvels(collectFuelItems(sheaths));
    }

    /** Both fueled and spent TVELs occupy fuel slots; only fueled ones count as load. */
    static boolean isFuelSlotItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.is(ModItems.UF_TVEL.get()) || stack.is(ModItems.DEPRESSED_UF_TVEL.get()));
    }

    /** Adds one durability point per loaded TVEL; exhausted rods become spent TVELs. */
    public static boolean ageLoadedFuel(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (level == null || panel == null || !panel.isFormedController()) return false;
        boolean changed = false;
        for (ReactorFuelSheathBlockEntity sheath : collectSheaths(level, panel.min(), panel.max())) {
            changed |= sheath.ageFuel();
        }
        return changed;
    }

    public static List<ItemStack> collectFuelItems(List<ReactorFuelSheathBlockEntity> sheaths) {
        List<ItemStack> result = new ArrayList<>();
        for (ReactorFuelSheathBlockEntity sheath : sheaths) {
            for (ItemStack stack : sheath.copyFuelItems()) {
                if (isFuelSlotItem(stack)) appendSingleTvels(result, List.of(stack));
            }
        }
        return result;
    }

    /** Replace the aggregate GUI view's contents with even, deterministic per-sheath item ownership. */
    public static void replaceFuelItems(List<ReactorFuelSheathBlockEntity> sheaths,
                                        List<ItemStack> fuelItems,
                                        BlockPos min,
                                        BlockPos max) {
        redistribute(fuelItems, sheaths, min, max);
    }

    /** A player break is deferred unless all chunks holding the old share set are loaded. */
    public static boolean canSafelyRemoveSheath(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof ReactorFuelSheathBlockEntity sheath) || !sheath.hasReactorBounds()) return true;
        return chunksLoaded(level, sheath.reactorMin(), sheath.reactorMax());
    }

    /** Called before the sheath BE is removed; ceil(T/S) items leave with the first broken share. */
    public static void beforeSheathRemoved(ServerLevel level,
                                           BlockPos pos,
                                           ReactorFuelSheathBlockEntity removed) {
        BlockPos min = removed.reactorMin();
        BlockPos max = removed.reactorMax();
        if (min == null || max == null || !chunksLoaded(level, min, max)) {
            List<ItemStack> localFuel = collectFuelItems(List.of(removed));
            removed.clearContent();
            dropItems(level, pos, localFuel);
            return;
        }

        List<ReactorFuelSheathBlockEntity> peers = new ArrayList<>();
        for (ReactorFuelSheathBlockEntity sheath : collectSheaths(level, min, max)) {
            if (!sheath.getBlockPos().equals(pos)) peers.add(sheath);
        }
        sort(peers);
        int sheathCountBeforeBreak = peers.size() + 1;
        List<ReactorFuelSheathBlockEntity> wholeGroup = new ArrayList<>(peers.size() + 1);
        wholeGroup.addAll(peers);
        wholeGroup.add(removed);
        List<ItemStack> allFuel = collectFuelItems(wholeGroup);
        int total = countFuelItems(allFuel);
        int dropCount = total == 0 ? 0 : Math.min(total, ceilDivide(total, sheathCountBeforeBreak));

        List<ItemStack> dropped = new ArrayList<>(dropCount);
        List<ItemStack> remaining = new ArrayList<>(Math.max(0, total - dropCount));
        int leftToDrop = dropCount;
        for (ItemStack stack : allFuel) {
            int count = stack.getCount();
            while (count-- > 0) {
                ItemStack one = stack.copyWithCount(1);
                if (leftToDrop > 0) {
                    dropped.add(one);
                    leftToDrop--;
                } else {
                    remaining.add(one);
                }
            }
        }

        removed.clearContent();
        if (!peers.isEmpty()) redistribute(remaining, peers, min, max);
        dropItems(level, pos, dropped);
    }

    private static void redistribute(List<ItemStack> items,
                                     List<ReactorFuelSheathBlockEntity> sheaths,
                                     BlockPos min,
                                     BlockPos max) {
        sort(sheaths);
        if (sheaths.isEmpty()) return;
        if (items.size() > sheaths.size() * ReactorFuelSheathBlockEntity.MAX_TVEL_SLOTS) {
            throw new IllegalStateException("Reactor sheath fuel capacity exceeded");
        }

        List<List<ItemStack>> assigned = new ArrayList<>(sheaths.size());
        for (int i = 0; i < sheaths.size(); i++) assigned.add(new ArrayList<>());
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!isFuelSlotItem(stack)) continue;
            int sheathIndex = i % sheaths.size();
            assigned.get(sheathIndex).add(stack.copyWithCount(1));
        }
        for (int i = 0; i < sheaths.size(); i++) {
            ReactorFuelSheathBlockEntity sheath = sheaths.get(i);
            sheath.setReactorBounds(min, max);
            sheath.replaceFuelItems(assigned.get(i));
        }
    }

    private static void appendSingleTvels(List<ItemStack> destination, List<ItemStack> source) {
        for (ItemStack stack : source) {
            if (!isFuelSlotItem(stack)) continue;
            for (int count = 0; count < stack.getCount(); count++) {
                destination.add(stack.copyWithCount(1));
            }
        }
    }

    /** Counts every fuel-slot item, including spent TVELs that still occupy a slot. */
    private static int countFuelItems(List<ItemStack> items) {
        int total = 0;
        for (ItemStack stack : items) {
            if (isFuelSlotItem(stack)) total += stack.getCount();
        }
        return total;
    }

    /** Counts only fueled TVELs — the matrix load and the GUI "loaded" reading. */
    private static int countActiveTvels(List<ItemStack> items) {
        int total = 0;
        for (ItemStack stack : items) {
            if (stack.is(ModItems.UF_TVEL.get())) total += stack.getCount();
        }
        return total;
    }

    private static int ceilDivide(int numerator, int denominator) {
        return numerator / denominator + (numerator % denominator == 0 ? 0 : 1);
    }

    private static void dropItems(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        for (ItemStack stack : items) {
            int remaining = stack.getCount();
            while (remaining-- > 0) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, stack.copyWithCount(1));
            }
        }
    }

    private static boolean chunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                BlockPos check = new BlockPos((chunkX << 4) + 8, min.getY(), (chunkZ << 4) + 8);
                if (!level.hasChunkAt(check)) return false;
            }
        }
        return true;
    }

    private static void sort(List<ReactorFuelSheathBlockEntity> sheaths) {
        sheaths.sort(Comparator.comparingLong(sheath -> sheath.getBlockPos().asLong()));
    }
}

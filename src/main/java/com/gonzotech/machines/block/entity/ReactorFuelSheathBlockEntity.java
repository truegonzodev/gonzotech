package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Fuel owner for one reactor sheath; panel GUI accesses these inventories through a composite view. */
public final class ReactorFuelSheathBlockEntity extends BlockEntity implements Container {

    /** The existing panel UI admits at most 18 TVELs per reactor; one sheath can hold that legacy total. */
    public static final int MAX_TVEL_SLOTS = 18;

    private final NonNullList<ItemStack> fuelItems = NonNullList.withSize(MAX_TVEL_SLOTS, ItemStack.EMPTY);
    private BlockPos reactorMin;
    private BlockPos reactorMax;

    public ReactorFuelSheathBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get(), pos, state);
    }

    public BlockPos reactorMin() {
        return reactorMin;
    }

    public BlockPos reactorMax() {
        return reactorMax;
    }

    public boolean hasReactorBounds() {
        return reactorMin != null && reactorMax != null;
    }

    public void setReactorBounds(BlockPos min, BlockPos max) {
        BlockPos nextMin = min == null ? null : min.immutable();
        BlockPos nextMax = max == null ? null : max.immutable();
        if (java.util.Objects.equals(reactorMin, nextMin) && java.util.Objects.equals(reactorMax, nextMax)) return;
        reactorMin = nextMin;
        reactorMax = nextMax;
        setChanged();
    }

    public int storedTvelCount() {
        int total = 0;
        for (ItemStack stack : fuelItems) {
            if (stack.is(ModItems.UF_TVEL.get())) total += stack.getCount();
        }
        return total;
    }

    /** Copies the concrete item stacks so a controller menu never owns the persistent fuel. */
    public List<ItemStack> copyFuelItems() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : fuelItems) {
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }

    /** Replaces this sheath's persistent share of the reactor-wide item inventory. */
    public void replaceFuelItems(List<ItemStack> replacements) {
        boolean changed = false;
        for (int slot = 0; slot < fuelItems.size(); slot++) {
            ItemStack replacement = slot < replacements.size() ? replacements.get(slot) : ItemStack.EMPTY;
            if (!ItemStack.matches(fuelItems.get(slot), replacement)) changed = true;
            fuelItems.set(slot, replacement.isEmpty()
                ? ItemStack.EMPTY
                : replacement.copyWithCount(Math.min(1, replacement.getCount())));
        }
        if (changed) setChanged();
    }

    // Container is the local persisted inventory; it is not opened directly or exposed to automation.
    @Override
    public int getContainerSize() {
        return MAX_TVEL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : fuelItems) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < fuelItems.size() ? fuelItems.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= fuelItems.size()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(fuelItems, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= fuelItems.size()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(fuelItems, slot);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= fuelItems.size()
            || (!stack.isEmpty() && !stack.is(ModItems.UF_TVEL.get()))) return;
        fuelItems.set(slot, stack.isEmpty()
            ? ItemStack.EMPTY
            : stack.copyWithCount(Math.min(1, stack.getCount())));
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < fuelItems.size() && stack.is(ModItems.UF_TVEL.get());
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < fuelItems.size(); slot++) fuelItems.set(slot, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, fuelItems, registries);
        if (reactorMin != null) tag.putLong("ReactorMin", reactorMin.asLong());
        if (reactorMax != null) tag.putLong("ReactorMax", reactorMax.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int slot = 0; slot < fuelItems.size(); slot++) fuelItems.set(slot, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, fuelItems, registries);
        reactorMin = tag.contains("ReactorMin") ? BlockPos.of(tag.getLong("ReactorMin")) : null;
        reactorMax = tag.contains("ReactorMax") ? BlockPos.of(tag.getLong("ReactorMax")) : null;
        if (reactorMin == null || reactorMax == null
            || reactorMin.getX() > reactorMax.getX()
            || reactorMin.getY() > reactorMax.getY()
            || reactorMin.getZ() > reactorMax.getZ()) {
            reactorMin = null;
            reactorMax = null;
        }
    }
}

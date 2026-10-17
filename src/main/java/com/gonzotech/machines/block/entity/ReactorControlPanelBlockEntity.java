package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.menu.ReactorMenu;
import com.gonzotech.machines.reactor.ReactorStructure;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent controller, dynamically sized fuel-slot inventory, and saved structure bounds. */
public final class ReactorControlPanelBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int MAX_FUEL_SLOTS = 18;

    private final NonNullList<ItemStack> fuelItems = NonNullList.withSize(MAX_FUEL_SLOTS, ItemStack.EMPTY);
    private boolean formed;
    private boolean validationPending;
    private BlockPos min;
    private BlockPos max;
    /** Active menu slots; the item list remains 18-wide so a temporarily deformed reactor keeps its contents. */
    private int fuelSlotCount;

    public ReactorControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_URANIUM_REACTOR_CONTROL_PANEL.get(), pos, state);
    }

    /** Current working assumption (not author-confirmed): 3 slots for the first sheath, then +1 per sheath, capped at 18. */
    public static int fuelSlotsForSheaths(int sheathCount) {
        return sheathCount <= 0 ? 0 : Math.min(MAX_FUEL_SLOTS, sheathCount + 2);
    }

    public boolean isFormedController() {
        return formed && min != null && max != null;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public int fuelSlotCount() {
        return Math.min(MAX_FUEL_SLOTS, Math.max(0, fuelSlotCount));
    }

    public boolean contains(BlockPos pos) {
        return isFormedController()
            && pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** Called only after the entire outer shell and interior pass validation. */
    public void configureStructure(BlockPos min, BlockPos max, int sheathCount) {
        this.min = min.immutable();
        this.max = max.immutable();
        this.fuelSlotCount = fuelSlotsForSheaths(sheathCount);
        this.formed = true;
        this.validationPending = false;
        setChanged();
    }

    /** The server sends this authoritative count with the menu-open packet for client slot prediction. */
    public void syncMenuFuelSlotCount(int count) {
        fuelSlotCount = Math.min(MAX_FUEL_SLOTS, Math.max(0, count));
    }

    /** The structure is no longer valid; stored fuel stays in the controller until reform or break. */
    public void clearStructure() {
        formed = false;
        validationPending = false;
        min = null;
        max = null;
        fuelSlotCount = 0;
        setChanged();
    }

    public void markValidationPending() {
        validationPending = true;
    }

    public void clearValidationPending() {
        validationPending = false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ReactorStructure.panelLoaded(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            ReactorStructure.panelUnloaded(serverLevel, getBlockPos());
        }
        super.setRemoved();
    }

    /** Retry validation of this saved formed structure after its remaining chunks load. */
    public void serverTick() {
        if (!validationPending || !(level instanceof ServerLevel serverLevel)
            || serverLevel.getGameTime() % 20L != 0L) {
            return;
        }
        ReactorStructure.restorePanel(serverLevel, this);
    }

    // The inventory is menu-only; sided automation exposes no slots.
    @Override
    public int getContainerSize() {
        return MAX_FUEL_SLOTS;
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
        if (slot < 0 || slot >= fuelSlotCount() || (!stack.isEmpty() && !canPlaceItem(slot, stack))) return;
        fuelItems.set(slot, stack.isEmpty()
            ? ItemStack.EMPTY
            : stack.copyWithCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), getMaxStackSize()))));
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** Current unconfirmed fuel filter: only fresh UF TVEL stacks are accepted. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < fuelSlotCount() && stack.is(ModItems.UF_TVEL.get());
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < fuelItems.size(); slot++) {
            fuelItems.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReactorMenu(id, inventory, this, fuelSlotCount());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, fuelItems, registries);
        tag.putBoolean("Formed", formed);
        tag.putInt("FuelSlotCount", fuelSlotCount());
        if (min != null) tag.putLong("StructureMin", min.asLong());
        if (max != null) tag.putLong("StructureMax", max.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int slot = 0; slot < fuelItems.size(); slot++) {
            fuelItems.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, fuelItems, registries);
        formed = tag.getBoolean("Formed");
        fuelSlotCount = Math.min(MAX_FUEL_SLOTS, Math.max(0, tag.getInt("FuelSlotCount")));
        min = tag.contains("StructureMin") ? BlockPos.of(tag.getLong("StructureMin")) : null;
        max = tag.contains("StructureMax") ? BlockPos.of(tag.getLong("StructureMax")) : null;
        validationPending = formed;
        if (!formed || min == null || max == null
            || min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            clearStructure();
        }
    }
}

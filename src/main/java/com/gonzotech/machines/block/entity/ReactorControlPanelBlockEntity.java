package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.menu.ReactorMenu;
import com.gonzotech.machines.reactor.ReactorMatrix;
import com.gonzotech.machines.reactor.ReactorRuntime;
import com.gonzotech.machines.reactor.ReactorStructure;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Persistent controller and saved structure bounds; fuel items belong to the sheath block entities. */
public final class ReactorControlPanelBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int MAX_FUEL_SLOTS = 18;

    /** Compatibility-only NBT buffer from 0.3.237; new menu writes never target this list. */
    private final NonNullList<ItemStack> legacyFuelItems = NonNullList.withSize(MAX_FUEL_SLOTS, ItemStack.EMPTY);
    private boolean formed;
    private boolean validationPending;
    private BlockPos min;
    private BlockPos max;
    private int fuelSlotCount;
    private final ReactorRuntime runtime = new ReactorRuntime();
    private final int[] clientMenuData = new int[ReactorRuntime.MENU_DATA_COUNT];
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index < 0 || index >= ReactorRuntime.MENU_DATA_COUNT) return 0;
            return getLevel() != null && getLevel().isClientSide
                ? clientMenuData[index] : runtime.menuData(index);
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < ReactorRuntime.MENU_DATA_COUNT
                && getLevel() != null && getLevel().isClientSide) {
                clientMenuData[index] = value;
            }
        }

        @Override
        public int getCount() {
            return ReactorRuntime.MENU_DATA_COUNT;
        }
    };

    public ReactorControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_URANIUM_REACTOR_CONTROL_PANEL.get(), pos, state);
    }

    /** Current GUI layout: three initial slots, one more for each later sheath, capped at 18. */
    public static int fuelSlotsForSheaths(int sheathCount) {
        return sheathCount <= 0 ? 0 : Math.min(MAX_FUEL_SLOTS, sheathCount + 2);
    }

    /** Existing stored fuel stays visible after a shape change; it can never become a hidden panel stack. */
    public static int fuelSlotsForSheaths(int sheathCount, int storedFuel) {
        return Math.min(MAX_FUEL_SLOTS,
            Math.max(fuelSlotsForSheaths(sheathCount), Math.max(0, storedFuel)));
    }

    public boolean isFormedController() {
        return formed && min != null && max != null;
    }

    public boolean isValidationPending() {
        return validationPending;
    }

    public boolean isRuntimeReady() {
        return isFormedController() && !validationPending;
    }

    public ContainerData menuData() {
        return menuData;
    }

    public ReactorRuntime.Metrics runtimeMetrics() {
        return runtime.metrics();
    }

    /** Авария уже случилась: панель мертва, диагностика объясняет, что делать. */
    public boolean accidentTriggered() {
        return runtime.accidentTriggered();
    }

    public boolean isWaterPort(BlockPos pos) {
        return isRuntimeReady() && runtime.isWaterPort(pos);
    }

    public long receiveWaterFromPort(BlockPos port, long amount, boolean simulate) {
        if (!isRuntimeReady()) return 0L;
        long accepted = runtime.receiveWater(port, amount, simulate);
        if (!simulate && accepted > 0L) setChanged();
        return accepted;
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
    public void configureStructure(BlockPos min, BlockPos max, int sheathCount, int storedFuel,
                                   long[] heatPorts, long[] waterPorts, long[] valvePorts) {
        this.min = min.immutable();
        this.max = max.immutable();
        this.fuelSlotCount = fuelSlotsForSheaths(sheathCount, storedFuel);
        this.formed = true;
        this.validationPending = false;
        runtime.configure(max.getX() - min.getX() - 1, heatPorts, waterPorts, valvePorts);
        setChanged();
    }

    /** The server sends this authoritative count with the menu-open packet for client slot prediction. */
    public void syncMenuFuelSlotCount(int count) {
        fuelSlotCount = Math.min(MAX_FUEL_SLOTS, Math.max(0, count));
    }

    /** Shape deinitialization does not clear fuel: every surviving sheath remains its owner. */
    public void clearStructure() {
        formed = false;
        validationPending = false;
        min = null;
        max = null;
        fuelSlotCount = 0;
        runtime.deconfigure();
        setChanged();
    }

    public List<ItemStack> copyLegacyFuelItems() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : legacyFuelItems) {
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }

    public int legacyFuelCount() {
        int total = 0;
        for (ItemStack stack : legacyFuelItems) total += stack.getCount();
        return total;
    }

    /** Clear the old panel NBT only after its contents have been copied into formed sheaths. */
    public void clearLegacyFuelItems() {
        for (int slot = 0; slot < legacyFuelItems.size(); slot++) legacyFuelItems.set(slot, ItemStack.EMPTY);
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
            // Invalidate by this live BE reference; the chunk may already be unloading,
            // so a world lookup by position here can deadlock the server thread.
            ReactorMatrix.invalidate(this);
            ReactorStructure.panelUnloaded(serverLevel, getBlockPos());
        }
        super.setRemoved();
    }

    /** Restore saved bounds when possible, then advance the ReaCalc simulation each server tick. */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (validationPending) {
            if (serverLevel.getGameTime() % 20L == 0L) {
                ReactorStructure.restorePanel(serverLevel, this);
            }
            return;
        }
        if (isFormedController()) runtime.serverTick(serverLevel, this);
    }

    // These Container methods exist only to migrate/drop old 0.3.237 panel NBT. New menus use ReactorFuelInventory.
    @Override
    public int getContainerSize() {
        return MAX_FUEL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : legacyFuelItems) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < legacyFuelItems.size() ? legacyFuelItems.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= legacyFuelItems.size()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(legacyFuelItems, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= legacyFuelItems.size()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(legacyFuelItems, slot);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // New GUI slots never target the controller. Only permit clearing a legacy slot during migration/drop.
        if (slot >= 0 && slot < legacyFuelItems.size() && stack.isEmpty()) {
            legacyFuelItems.set(slot, ItemStack.EMPTY);
            setChanged();
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void clearContent() {
        clearLegacyFuelItems();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
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
        ContainerHelper.saveAllItems(tag, legacyFuelItems, registries);
        tag.putBoolean("Formed", formed);
        tag.putInt("FuelSlotCount", fuelSlotCount());
        if (min != null) tag.putLong("StructureMin", min.asLong());
        if (max != null) tag.putLong("StructureMax", max.asLong());
        runtime.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int slot = 0; slot < legacyFuelItems.size(); slot++) legacyFuelItems.set(slot, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, legacyFuelItems, registries);
        formed = tag.getBoolean("Formed");
        fuelSlotCount = Math.min(MAX_FUEL_SLOTS, Math.max(0, tag.getInt("FuelSlotCount")));
        min = tag.contains("StructureMin") ? BlockPos.of(tag.getLong("StructureMin")) : null;
        max = tag.contains("StructureMax") ? BlockPos.of(tag.getLong("StructureMax")) : null;
        runtime.load(tag);
        validationPending = formed;
        if (!formed || min == null || max == null
            || min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            clearStructure();
        }
    }
}

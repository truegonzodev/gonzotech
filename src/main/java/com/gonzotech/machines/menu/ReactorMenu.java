package com.gonzotech.machines.menu;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.reactor.ReactorFuelInventory;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Panel menu whose visible slots proxy the persistent inventories owned by the fuel sheaths. */
public final class ReactorMenu extends BaseMachineMenu {

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int GRID_FIRST_X = 62;
    private static final int GRID_FIRST_Y = 17;
    private static final int GRID_STEP = 18;
    private static final int GRID_ROWS = 3;

    private final ReactorControlPanelBlockEntity panel;
    private final int activeFuelSlots;

    public ReactorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, ReactorControlPanelBlockEntity.class),
            buffer.readVarInt());
    }

    public ReactorMenu(int id, Inventory inventory, ReactorControlPanelBlockEntity panel, int activeFuelSlots) {
        this(id, inventory, panel, activeFuelSlots, new ReactorFuelInventory(panel, activeFuelSlots));
    }

    private ReactorMenu(int id,
                        Inventory inventory,
                        ReactorControlPanelBlockEntity panel,
                        int activeFuelSlots,
                        ReactorFuelInventory fuelInventory) {
        super(ModMenus.REACTOR.get(), id, fuelInventory, panel.menuData(), clampSlots(activeFuelSlots));
        this.panel = panel;
        this.activeFuelSlots = clampSlots(activeFuelSlots);
        panel.syncMenuFuelSlotCount(this.activeFuelSlots);

        for (int slot = 0; slot < this.activeFuelSlots; slot++) {
            addSlot(new FuelSlot(fuelInventory, slot, fuelSlotX(slot), fuelSlotY(slot)));
        }
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    private static int clampSlots(int count) {
        return Math.max(0, Math.min(ReactorControlPanelBlockEntity.MAX_FUEL_SLOTS, count));
    }

    public int activeFuelSlots() {
        return activeFuelSlots;
    }

    public int waterAmount() {
        return data.get(0);
    }

    public int waterCapacity() {
        return data.get(1);
    }

    public long gthAmountTenths() {
        return data.get(2) + (long) data.get(3) * 10_000L;
    }

    public long gthCapacityTenths() {
        return data.get(4) + (long) data.get(5) * 10_000L;
    }

    public long hottestTemperatureTenths() {
        return data.get(6) + (long) data.get(7) * 10_000L;
    }

    public long meanTemperatureTenths() {
        return data.get(8) + (long) data.get(9) * 10_000L;
    }

    public int strongestFlowTenthsPercent() {
        return data.get(10);
    }

    public int averageFlowTenthsPercent() {
        return data.get(11);
    }

    public int strongestLeakTenthsPercent() {
        return data.get(12);
    }

    public int averageLeakTenthsPercent() {
        return data.get(13);
    }

    public long generationTenthsGthPerTick() {
        return data.get(14) + (long) data.get(15) * 10_000L;
    }

    public int loadedTvels() {
        return data.get(16);
    }

    public long modelWaterTemperatureTenths() {
        return data.get(17) + (long) data.get(18) * 10_000L;
    }

    public int modelWaterPressureTenthsMpa() {
        return data.get(19);
    }

    public float gthFraction() {
        long capacity = gthCapacityTenths();
        return capacity <= 0L ? 0.0F : (float) gthAmountTenths() / capacity;
    }

    public float waterFraction() {
        int capacity = waterCapacity();
        return capacity <= 0 ? 0.0F : (float) waterAmount() / capacity;
    }

    public float temperatureFraction() {
        return hottestTemperatureTenths() / 100_000.0F;
    }

    public float neutronFraction() {
        return strongestFlowTenthsPercent() / 1_000.0F;
    }

    public static int fuelSlotX(int slot) {
        return GRID_FIRST_X + (slot / GRID_ROWS) * GRID_STEP;
    }

    public static int fuelSlotY(int slot) {
        return GRID_FIRST_Y + (slot % GRID_ROWS) * GRID_STEP;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return super.stillValid(player);
        return panel.isFormedController()
            && panel.fuelSlotCount() == activeFuelSlots
            && super.stillValid(player);
    }

    private static final class FuelSlot extends Slot {
        FuelSlot(ReactorFuelInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.UF_TVEL.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}

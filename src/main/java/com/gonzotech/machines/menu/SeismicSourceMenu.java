package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SeismicSourceBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Источник колебаний: один центральный слот (80,35), принимает только
 * одну единицу динамита.
 */
public final class SeismicSourceMenu extends BaseMachineMenu {

    private final SeismicSourceBlockEntity source;

    public SeismicSourceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, SeismicSourceBlockEntity.class));
    }

    public SeismicSourceMenu(int id, Inventory inventory, SeismicSourceBlockEntity source) {
        super(ModMenus.SEISMIC_SOURCE.get(), id, source, new SimpleContainerData(0),
            SeismicSourceBlockEntity.CONTAINER_SIZE);
        this.source = source;
        addSlot(new DynamiteSlot(source, SeismicSourceBlockEntity.SLOT_DYNAMITE, 80, 35));
        addPlayerInventory(inventory, 8, 84);
    }

    public SeismicSourceBlockEntity source() {
        return source;
    }

    private static final class DynamiteSlot extends Slot {

        private DynamiteSlot(SeismicSourceBlockEntity source, int index, int x, int y) {
            super(source, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return SeismicSourceBlockEntity.isDynamite(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}

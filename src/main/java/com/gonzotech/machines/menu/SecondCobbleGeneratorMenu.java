package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondCobbleGeneratorBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Cobblestone generator II: lava requirement slot plus output, no pickaxe slot. */
public final class SecondCobbleGeneratorMenu extends BaseMachineMenu {

    public SecondCobbleGeneratorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, SecondCobbleGeneratorBlockEntity.class),
            new SimpleContainerData(4));
    }

    public SecondCobbleGeneratorMenu(int id, Inventory inv, SecondCobbleGeneratorBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_COBBLE_GENERATOR.get(), id, be, data, 2);
        addSlot(new FilteredSlot(be, SecondCobbleGeneratorBlockEntity.SLOT_LAVA, 26, 35));
        addSlot(new OutputOnlySlot(be, SecondCobbleGeneratorBlockEntity.SLOT_OUTPUT, 132, 35));
        addPlayerInventory(inv, 8, 84);
    }

    public int gtu() {
        return data.get(0);
    }

    public int water() {
        return data.get(1);
    }

    public int digProgress() {
        return data.get(2);
    }

    public int digTotal() {
        return data.get(3);
    }

    private static final class FilteredSlot extends net.minecraft.world.inventory.Slot {
        private final net.minecraft.world.Container backing;
        private final int index;

        FilteredSlot(net.minecraft.world.Container backing, int index, int x, int y) {
            super(backing, index, x, y);
            this.backing = backing;
            this.index = index;
        }

        @Override
        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
            return backing.canPlaceItem(index, stack);
        }
    }
}

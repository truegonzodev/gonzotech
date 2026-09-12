package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondElectricFurnaceBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/** Menu for two parallel level-II electric-furnace lanes. */
public final class SecondElectricFurnaceMenu extends BaseMachineMenu {

    private final SecondElectricFurnaceBlockEntity be;

    public SecondElectricFurnaceMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, SecondElectricFurnaceBlockEntity.class),
            new SimpleContainerData(5));
    }

    public SecondElectricFurnaceMenu(int id, Inventory inv, SecondElectricFurnaceBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_ELECTRIC_FURNACE.get(), id, be, data, 4);
        this.be = be;
        addSlot(new Slot(be, SecondElectricFurnaceBlockEntity.SLOT_INPUT_A, 47, 25));
        addSlot(new Slot(be, SecondElectricFurnaceBlockEntity.SLOT_INPUT_B, 47, 49));
        addSlot(new SmeltResultSlot(be, be, SecondElectricFurnaceBlockEntity.SLOT_OUTPUT_A, 116, 25));
        addSlot(new SmeltResultSlot(be, be, SecondElectricFurnaceBlockEntity.SLOT_OUTPUT_B, 116, 49));
        addPlayerInventory(inv, 8, 84);
    }

    public int gtu() {
        return data.get(0);
    }

    public int cookProgress(int lane) {
        return data.get(lane == 0 ? 1 : 3);
    }

    public int cookTotal(int lane) {
        return data.get(lane == 0 ? 2 : 4);
    }
}

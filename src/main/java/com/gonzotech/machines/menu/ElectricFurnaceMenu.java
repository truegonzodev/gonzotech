package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ElectricFurnaceBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * Меню электропечи: слот входа и слот выхода (без топлива), шкала GTU и
 * прогресс переплавки.
 */
public class ElectricFurnaceMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final ElectricFurnaceBlockEntity be;

    public ElectricFurnaceMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, ElectricFurnaceBlockEntity.class), new SimpleContainerData(3));
    }

    public ElectricFurnaceMenu(int id, Inventory inv, ElectricFurnaceBlockEntity be, ContainerData data) {
        super(ModMenus.ELECTRIC_FURNACE.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new Slot(be, ElectricFurnaceBlockEntity.SLOT_INPUT, 56, 35));
        addSlot(new SmeltResultSlot(be, be, ElectricFurnaceBlockEntity.SLOT_OUTPUT, 116, 35));

        addPlayerInventory(inv, 8, 84);
    }

    public ElectricFurnaceBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }

    public int cookProgress() {
        return data.get(1);
    }

    public int cookTotal() {
        return data.get(2);
    }
}

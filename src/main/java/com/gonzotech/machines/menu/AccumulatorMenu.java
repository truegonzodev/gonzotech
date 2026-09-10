package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.AccumulatorBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню энергохранилища: предметных слотов нет, только шкала GTU.
 */
public class AccumulatorMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;

    private final AccumulatorBlockEntity be;

    public AccumulatorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, AccumulatorBlockEntity.class), new SimpleContainerData(1));
    }

    public AccumulatorMenu(int id, Inventory inv, AccumulatorBlockEntity be, ContainerData data) {
        super(ModMenus.ACCUMULATOR.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;
        addPlayerInventory(inv, 8, 84);
    }

    public AccumulatorBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }
}

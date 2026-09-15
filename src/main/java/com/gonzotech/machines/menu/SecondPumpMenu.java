package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondPumpBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Level-II pump menu: resource gauges only, with no container slots. */
public final class SecondPumpMenu extends BaseMachineMenu {

    public SecondPumpMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, SecondPumpBlockEntity.class), new SimpleContainerData(2));
    }

    public SecondPumpMenu(int id, Inventory inv, SecondPumpBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_PUMP.get(), id, be, data, 0);
        addPlayerInventory(inv, 8, 84);
    }

    public int gtu() {
        return data.get(0);
    }

    public int water() {
        return data.get(1);
    }
}

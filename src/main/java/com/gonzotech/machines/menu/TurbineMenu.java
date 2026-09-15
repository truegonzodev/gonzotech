package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.TurbineRotorBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import com.gonzotech.machines.turbine.TurbineMath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Единое меню всей сформированной паровой турбины: пока только Steam и GTU. */
public final class TurbineMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;
    private static final int STEAM_SYNC_BASE = 10_000;

    private final TurbineRotorBlockEntity controller;

    public TurbineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, TurbineRotorBlockEntity.class), new SimpleContainerData(6));
    }

    public TurbineMenu(int id, Inventory inv, TurbineRotorBlockEntity controller, ContainerData data) {
        super(ModMenus.TURBINE.get(), id, controller, data, MACHINE_SLOTS);
        this.controller = controller;
        addPlayerInventory(inv, 8, 84);
    }

    public TurbineRotorBlockEntity controller() {
        return controller;
    }

    public int steam() {
        return Math.max(0, data.get(1)) * STEAM_SYNC_BASE + Math.max(0, data.get(0));
    }

    public int gtu() {
        return Math.max(0, data.get(2));
    }

    public int rotors() {
        return Math.max(0, data.get(3));
    }

    public boolean formed() {
        return data.get(4) != 0;
    }

    public int steamConsumed() {
        return Math.max(0, data.get(5));
    }

    public int steamCapacity() {
        return TurbineMath.steamCapacity(rotors());
    }

    public int gtuCapacity() {
        return TurbineMath.gtuCapacityUnits(rotors());
    }
}

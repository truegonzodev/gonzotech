package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SteamGenCoreBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import com.gonzotech.machines.steamgen.SteamGenMath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Единое меню сформированного продвинутого парогенератора: GTH, вода, пар. */
public final class SteamGenMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;
    private static final int FLUID_SYNC_BASE = 10_000;

    private final SteamGenCoreBlockEntity controller;

    public SteamGenMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, SteamGenCoreBlockEntity.class), new SimpleContainerData(13));
    }

    public SteamGenMenu(int id, Inventory inv, SteamGenCoreBlockEntity controller, ContainerData data) {
        super(ModMenus.STEAMGEN.get(), id, controller, data, MACHINE_SLOTS);
        this.controller = controller;
        addPlayerInventory(inv, 8, 84);
    }

    public SteamGenCoreBlockEntity controller() {
        return controller;
    }

    public int water() {
        return Math.max(0, data.get(1)) * FLUID_SYNC_BASE + Math.max(0, data.get(0));
    }

    public int steam() {
        return Math.max(0, data.get(3)) * FLUID_SYNC_BASE + Math.max(0, data.get(2));
    }

    public int gth() {
        return Math.max(0, data.get(4));
    }

    public int cores() {
        return Math.max(0, data.get(5));
    }

    public boolean formed() {
        return data.get(6) != 0;
    }

    public int sumCH() {
        return Math.max(0, data.get(7));
    }

    public int precious() {
        return Math.max(0, data.get(8));
    }

    public int waterIn() {
        return Math.max(0, data.get(9));
    }

    public int gthIn() {
        return Math.max(0, data.get(10));
    }

    public int steamOut() {
        return Math.max(0, data.get(11));
    }

    public int steamMade() {
        return Math.max(0, data.get(12));
    }

    public int waterCapacity() {
        return SteamGenMath.waterCapacity(cores());
    }

    public int steamCapacity() {
        return SteamGenMath.steamCapacity(cores());
    }

    public int gthCapacity() {
        return SteamGenMath.gthCapacityUnits(cores());
    }
}

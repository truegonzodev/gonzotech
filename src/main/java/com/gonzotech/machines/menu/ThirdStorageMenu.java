package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ThirdEnergyStorageBlockEntity;
import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.energy.TickEnergyStorage;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

/** Shared signed-short-safe, milli-exact III storage sync. */
public abstract class ThirdStorageMenu extends BaseMachineMenu {
    private final ThirdStorageDefs.Spec spec;
    protected ThirdStorageMenu(MenuType<?> type, int id, Inventory inv, Container container,
                               ContainerData data, int slots,
                               ThirdStorageDefs.Spec spec) {
        super(type,id,container,data,slots);
        this.spec=spec;
        checkContainerDataCount(data,ThirdStorageDefs.SYNC_PARTS);
        for(int slot=0;slot<slots;slot++) addSlot(new Slot(container,slot,80,17+18*slot));
        addPlayerInventory(inv,8,84);
    }
    protected static Container containerOrEmpty(ThirdEnergyStorageBlockEntity be,int slots) {
        return be==null ? new SimpleContainer(slots) : be;
    }
    public final long storedMilli() { return Math.min(spec.capacity(),TickEnergyStorage.decode(data.get(0),data.get(1),data.get(2))); }
    public final long capacityMilli() { return spec.capacity(); }
    public final int gtuProgress(int pixels) { return (int)(Math.max(0,pixels)*storedMilli()/capacityMilli()); }
    public final double gtuUnits() { return storedMilli()/1000.0; }
    public final double capacityUnits() { return capacityMilli()/1000.0; }
}

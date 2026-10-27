package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ThirdBatteryBlockEntity;
import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public final class ThirdBatteryMenu extends ThirdStorageMenu {
    public ThirdBatteryMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id,inv,MenuHelper.readBlockEntitySafe(inv,buf,ThirdBatteryBlockEntity.class),
            new SimpleContainerData(ThirdStorageDefs.SYNC_PARTS));
    }
    public ThirdBatteryMenu(int id, Inventory inv, ThirdBatteryBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_BATTERY.get(),id,inv,containerOrEmpty(be,0),data,0,ThirdStorageDefs.BATTERY);
    }
}

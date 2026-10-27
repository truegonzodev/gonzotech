package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ThirdAccumulatorBlockEntity;
import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public final class ThirdAccumulatorMenu extends ThirdStorageMenu {
    public ThirdAccumulatorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id,inv,MenuHelper.readBlockEntitySafe(inv,buf,ThirdAccumulatorBlockEntity.class),
            new SimpleContainerData(ThirdStorageDefs.SYNC_PARTS));
    }
    public ThirdAccumulatorMenu(int id, Inventory inv, ThirdAccumulatorBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_ACCUMULATOR.get(),id,inv,containerOrEmpty(be,3),data,3,ThirdStorageDefs.ACCUMULATOR);
    }
}

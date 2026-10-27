package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.item.ThirdBatteryItem;
import com.gonzotech.machines.menu.ThirdBatteryMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Placed: automatic GTU store. Item: passive exact-charge container, zero self-discharge. */
public final class ThirdBatteryBlockEntity extends ThirdEnergyStorageBlockEntity {
    public ThirdBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_BATTERY.get(),pos,state,0,ThirdStorageDefs.BATTERY);
    }
    public void loadFromItem(ItemStack stack) { energy.restore(ThirdBatteryItem.storedMilli(stack)); energyChanged(); }
    public void saveToItem(ItemStack stack) { ThirdBatteryItem.storeMilli(stack,energy.stored()); }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ThirdBatteryMenu(id,inv,this,data());
    }
}

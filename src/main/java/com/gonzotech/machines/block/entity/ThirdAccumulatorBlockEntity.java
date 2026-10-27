package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.ThirdStorageDefs;
import com.gonzotech.machines.menu.ThirdAccumulatorMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Three ordinary storage slots. No charging, carrier unpacking or item conversion. */
public final class ThirdAccumulatorBlockEntity extends ThirdEnergyStorageBlockEntity {
    public ThirdAccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_ACCUMULATOR.get(),pos,state,ThirdStorageDefs.ITEM_SLOTS,ThirdStorageDefs.ACCUMULATOR);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        // Deliberately bypass BaseMachine's carrier unloading: ANY item is stored as-is.
        items.set(slot,stack);
        stack.setCount(Math.min(stack.getCount(),Math.min(getMaxStackSize(),stack.getMaxStackSize())));
        setChanged();
    }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ThirdAccumulatorMenu(id,inv,this,data());
    }
}

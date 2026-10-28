package com.gonzotech.mixin;

import com.gonzotech.machines.inventory.SlotCapacity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HopperBlockEntity.class)
public abstract class HopperSlotCapacityMixin {
    @WrapMethod(method = "tryMoveInItem")
    private static ItemStack gonzo$admit(Container from, Container to, ItemStack stack,
                                       int slot, Direction direction, Operation<ItemStack> original) {
        return SlotCapacity.insert(to, slot, stack,
            offered -> original.call(from, to, offered, slot, direction));
    }
}

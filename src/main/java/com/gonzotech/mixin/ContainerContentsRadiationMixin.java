package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.ContainerContentsAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.*;
import java.util.List;

@Mixin(ItemContainerContents.class)
public abstract class ContainerContentsRadiationMixin implements ContainerContentsAccess {
    @Shadow @Final private NonNullList<ItemStack> items;
    @Override public List<ItemStack> gonzo$radiationItems() { return java.util.Collections.unmodifiableList(items); }
}

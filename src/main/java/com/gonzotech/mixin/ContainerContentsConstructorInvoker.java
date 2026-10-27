package com.gonzotech.mixin;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Preserve exact slot indices, including trailing empty slots, when cloning a value. */
@Mixin(ItemContainerContents.class)
public interface ContainerContentsConstructorInvoker {
    @Invoker("<init>")
    static ItemContainerContents gonzo$create(List<ItemStack> items) {
        throw new AssertionError("Mixin constructor invoker was not applied");
    }
}

package com.gonzotech.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * 0.3.291 (пункт 4 таблицы автора): фазы ПКМ-раскладки — приватные поля
 * ванильного меню для {@code ContainerClickRadiationMixin}. Только чтение.
 */
@Mixin(AbstractContainerMenu.class)
public interface ContainerMenuQuickCraftAccessor {
    @Accessor("quickcraftStatus")
    int gonzotech$quickcraftStatus();

    @Accessor("quickcraftType")
    int gonzotech$quickcraftType();

    @Accessor("quickcraftSlots")
    Set<Slot> gonzotech$quickcraftSlots();
}

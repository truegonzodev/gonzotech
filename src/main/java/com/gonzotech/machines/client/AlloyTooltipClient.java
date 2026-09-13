package com.gonzotech.machines.client;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Client-only cleanup for the advanced-debug footer of procedural equipment. */
public final class AlloyTooltipClient {

    private AlloyTooltipClient() {
    }

    /**
     * F3+H normally appends an item id and a volatile data-component count to
     * every tooltip. They reveal implementation details rather than gameplay
     * stats, so alloy equipment keeps the normal vanilla-style presentation.
     */
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isAlloyEquipment(stack)) return;

        List<Component> tooltip = event.getToolTip();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            if (!tooltip.get(index).getString().equals(itemId)) continue;
            tooltip.remove(index);
            // The component counter is always appended immediately after the id
            // by the same vanilla advanced-tooltip branch.
            if (index < tooltip.size()) tooltip.remove(index);
            return;
        }
    }

    private static boolean isAlloyEquipment(ItemStack stack) {
        return stack.is(ModItems.ALLOY_PICKAXE.get())
            || stack.is(ModItems.ALLOY_SWORD.get())
            || stack.is(ModItems.ALLOY_CHESTPLATE.get());
    }
}

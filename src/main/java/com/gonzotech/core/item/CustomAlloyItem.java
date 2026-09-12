package com.gonzotech.core.item;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.machines.processing.AlloyMaterialCatalog;
import com.gonzotech.machines.processing.AlloyProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** Generic alloy ingot carrying its identity through immutable Data Components. */
public final class CustomAlloyItem extends Item {

    public CustomAlloyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        AlloyComposition composition = stack.get(ModDataComponents.ALLOY_COMPOSITION.get());
        if (composition == null) {
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.blank").withStyle(ChatFormatting.DARK_GRAY));
            super.appendHoverText(stack, context, tooltip, flag);
            return;
        }

        int displayMultiplier = composition.minimumFoundryBatchMultiplier();
        for (var entry : composition.parts().entrySet()) {
            AlloyMaterialCatalog.Material material = AlloyMaterialCatalog.material(entry.getKey());
            Component name = material == null
                ? Component.literal(entry.getKey().toString())
                : material.displayStack().getHoverName();
            long materialUnits = (long) entry.getValue() * displayMultiplier;
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.part", formatPortions(materialUnits), name)
                .withStyle(ChatFormatting.GRAY));
        }

        AlloyProperties.from(composition).ifPresent(properties -> {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.strength", properties.strength()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.brittleness", properties.brittleness()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.inertness", properties.inertness()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.conductivity", properties.conductivity()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.heat", properties.heatResistance()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.plasticity", properties.plasticity()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.weight", properties.weight()));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.tier",
                Component.translatable("tooltip.gonzotech.custom_alloy.tier." + properties.toolTier().name().toLowerCase(Locale.ROOT))));
            AlloyTint tint = stack.get(ModDataComponents.ALLOY_TINT.get());
            int rgb = tint == null ? properties.argbTint() : tint.argb();
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.color", String.format("#%06X", rgb & 0xFFFFFF)));
        });

        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** Formats material units (one tenth of a portion) as an exact portion value. */
    private static String formatPortions(long materialUnits) {
        long whole = materialUnits / 10;
        long tenths = materialUnits % 10;
        return tenths == 0 ? Long.toString(whole) : whole + "." + tenths;
    }
}

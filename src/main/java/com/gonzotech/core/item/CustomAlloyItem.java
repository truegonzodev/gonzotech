package com.gonzotech.core.item;

import com.gonzotech.core.component.AlloyComposition;
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

    private static final int SOFT_RED = 0xFF5555;
    private static final int SOFT_YELLOW = 0xFFFF55;
    private static final int SOFT_GREEN = 0x55FF55;
    private static final int SOFT_BLUE = 0x5555FF;
    private static final int SOFT_AQUA = 0x55FFFF;

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
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.strength", properties.strength(),
                beneficialColor(properties.strength())));
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.brittleness", properties.brittleness(),
                brittlenessColor(properties.brittleness())));
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.inertness", properties.inertness(),
                beneficialColor(properties.inertness())));
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.conductivity", properties.conductivity(),
                conductivityColor(properties.conductivity())));
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.heat", properties.heatResistance(),
                beneficialColor(properties.heatResistance())));
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.plasticity", properties.plasticity(), SOFT_BLUE));
            tooltip.add(stat("tooltip.gonzotech.custom_alloy.weight", properties.weight(),
                weightColor(properties.weight())));
            tooltip.add(Component.translatable("tooltip.gonzotech.custom_alloy.tier",
                Component.translatable("tooltip.gonzotech.custom_alloy.tier." + properties.toolTier().name().toLowerCase(Locale.ROOT))
                    .withColor(tierColor(properties.toolTier()))));
        });

        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** Builds a standard label with only the changing numerical value coloured. */
    public static Component stat(String translationKey, int value, int color) {
        return Component.translatable(translationKey, Component.literal(Integer.toString(value)).withColor(color));
    }

    /** More is better: red at 0, yellow at 50, and green at 100. */
    public static int beneficialColor(int value) {
        int clamped = clamp(value);
        return clamped <= 50
            ? lerpColor(SOFT_RED, SOFT_YELLOW, clamped / 50.0D)
            : lerpColor(SOFT_YELLOW, SOFT_GREEN, (clamped - 50) / 50.0D);
    }

    /** Effective brittleness is best at 30: yellow → green → red. */
    public static int brittlenessColor(int value) {
        int clamped = clamp(value);
        return clamped <= 30
            ? lerpColor(SOFT_YELLOW, SOFT_GREEN, clamped / 30.0D)
            : lerpColor(SOFT_GREEN, SOFT_RED, (clamped - 30) / 70.0D);
    }

    /** Conductivity improves from soft red to soft aqua. */
    public static int conductivityColor(int value) {
        return lerpColor(SOFT_RED, SOFT_AQUA, clamp(value) / 100.0D);
    }

    /** Weight is best at 20: yellow → green → red. */
    public static int weightColor(int value) {
        int clamped = clamp(value);
        return clamped <= 20
            ? lerpColor(SOFT_YELLOW, SOFT_GREEN, clamped / 20.0D)
            : lerpColor(SOFT_GREEN, SOFT_RED, (clamped - 20) / 80.0D);
    }

    public static int tierColor(AlloyMaterialCatalog.ToolTier tier) {
        return switch (tier) {
            case STONE -> 0x787878;
            case IRON -> 0xDBD3D3;
            case DIAMOND -> 0x29F2E1;
            case NETHERITE_PLUS -> 0x453437;
        };
    }

    private static int lerpColor(int from, int to, double progress) {
        double amount = Math.max(0.0D, Math.min(1.0D, progress));
        int red = lerpChannel(from >>> 16, to >>> 16, amount);
        int green = lerpChannel(from >>> 8, to >>> 8, amount);
        int blue = lerpChannel(from, to, amount);
        return (red << 16) | (green << 8) | blue;
    }

    private static int lerpChannel(int from, int to, double progress) {
        return (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** Formats material units (one tenth of a portion) as an exact portion value. */
    private static String formatPortions(long materialUnits) {
        long whole = materialUnits / 10;
        long tenths = materialUnits % 10;
        return tenths == 0 ? Long.toString(whole) : whole + "." + tenths;
    }
}

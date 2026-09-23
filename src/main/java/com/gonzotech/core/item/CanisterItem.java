package com.gonzotech.core.item;

import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/**
 * Предмет канистры: хранит до 8000 mB жидкости.
 * Не может разливать жидкость ПКМ в воздух/мир как ведро.
 */
public class CanisterItem extends BlockItem {

    public CanisterItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static String getStoredFluid(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            return tag.getString("fluid");
        }
        return "empty";
    }

    public static int getStoredAmount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            return tag.getInt("amount");
        }
        return 0;
    }

    public static int getStoredSaltMb(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            return tag.getInt("salt_mb");
        }
        return 0;
    }

    public static void setFluidContent(ItemStack stack, String fluid, int amount, int saltMb) {
        if (amount <= 0 || fluid == null || "empty".equals(fluid)) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putString("fluid", fluid);
            tag.putInt("amount", Math.min(8000, amount));
            tag.putInt("salt_mb", "water".equals(fluid) ? Math.min(amount, saltMb) : 0);
        }));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String fluid = getStoredFluid(stack);
        int amount = getStoredAmount(stack);
        int saltMb = getStoredSaltMb(stack);

        if (amount <= 0 || "empty".equals(fluid) || fluid.isEmpty()) {
            tooltip.add(Component.translatable("gui.gonzotech.canister.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        int color = getFluidColor(fluid);
        String nameKey = getFluidLangKey(fluid);
        tooltip.add(GtUnits.fluidTitle(nameKey, amount, 8000, color));

        if ("water".equals(fluid) && saltMb > 0) {
            double saltPercent = (double) saltMb * 100.0 / Math.max(1, amount);
            String formatted = saltPercent >= 10.0
                ? String.format(Locale.ROOT, "%.0f%%", saltPercent)
                : String.format(Locale.ROOT, "%.1f%%", saltPercent);
            tooltip.add(Component.empty()
                .append(Component.translatable("gui.gonzotech.lore.salt_prefix").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(Component.literal(formatted).withStyle(ChatFormatting.WHITE)));
        }

        if ("formaldehyde".equals(fluid)) {
            tooltip.add(Component.translatable("gui.gonzotech.lore.toxicity_rate", "12 mTx/s")
                .withStyle(ChatFormatting.RED));
        }
    }

    public static int getFluidColor(String fluid) {
        return switch (fluid) {
            case "water" -> GtUnits.WATER;
            case "rectificate", "ethanol" -> GtUnits.RECTIFICATE;
            case "sulfuric_acid" -> GtUnits.SULFURIC_ACID;
            case "ethylene" -> GtUnits.ETHYLENE;
            case "aminoblazeethanol" -> GtUnits.AMINOBLAZEETHANOL;
            case "formaldehyde" -> GtUnits.FORMALDEHYDE;
            case "mash" -> GtUnits.MASH;
            case "wort" -> GtUnits.WORT;
            case "distillate" -> GtUnits.DISTILLATE;
            case "hot_water" -> GtUnits.BOILING_WATER;
            case "poison_potion" -> GtUnits.POISON_POTION;
            default -> 0xFFFFFF;
        };
    }

    public static String getFluidLangKey(String fluid) {
        return switch (fluid) {
            case "water" -> "resource.gonzotech.water";
            case "rectificate", "ethanol" -> "resource.gonzotech.rectificate";
            case "sulfuric_acid" -> "resource.gonzotech.sulfuric_acid";
            case "ethylene" -> "resource.gonzotech.ethylene";
            case "aminoblazeethanol" -> "resource.gonzotech.aminoblazeethanol";
            case "formaldehyde" -> "resource.gonzotech.formaldehyde";
            case "mash" -> "resource.gonzotech.mash";
            case "wort" -> "resource.gonzotech.wort";
            case "distillate" -> "resource.gonzotech.distillate";
            case "hot_water" -> "resource.gonzotech.hot_water";
            case "poison_potion" -> "resource.gonzotech.poison_potion";
            default -> "resource.gonzotech." + fluid;
        };
    }
}

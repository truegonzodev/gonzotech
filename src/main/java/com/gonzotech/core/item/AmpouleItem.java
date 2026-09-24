package com.gonzotech.core.item;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Ампула с жидкостью (128 mB).
 * Стакается по 64 штуки с одинаковым NBT содержимым.
 * Не может разливать жидкость ПКМ на пол.
 * При опустошении в Наполнителе исчезает (одноразовая).
 */
public class AmpouleItem extends Item {

    public static final int CAPACITY = 128;

    public AmpouleItem(Properties properties) {
        super(properties.stacksTo(64));
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

    public static boolean isDurable(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            return tag.getBoolean("durable");
        }
        return false;
    }

    public static boolean isAmpoule(ItemStack stack) {
        return stack.is(ModItems.AMPOULE.get())
                || stack.is(ModItems.EMPTY_AMPOULE.get())
                || stack.is(ModItems.DURABLE_AMPOULE.get());
    }

    public static ItemStack createFilled(String fluid, int amount, boolean durable) {
        ItemStack stack = new ItemStack(ModItems.AMPOULE.get());
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putString("fluid", fluid);
            tag.putInt("amount", Math.min(CAPACITY, amount));
            if (durable) {
                tag.putBoolean("durable", true);
            }
        }));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        String fluid = getStoredFluid(stack);
        boolean durable = isDurable(stack);
        if ("empty".equals(fluid) || fluid.isEmpty()) {
            return durable
                    ? Component.translatable("item.gonzotech.durable_ampoule")
                    : Component.translatable("item.gonzotech.empty_ampoule");
        }
        String langKey = "item.gonzotech.ampoule." + fluid;
        if (durable) {
            return Component.translatable("item.gonzotech.durable_ampoule_filled", Component.translatable(langKey));
        }
        return Component.translatable(langKey);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String fluid = getStoredFluid(stack);
        int amount = getStoredAmount(stack);
        boolean durable = isDurable(stack);

        if (durable) {
            tooltip.add(Component.translatable("tooltip.gonzotech.durable_ampoule").withStyle(ChatFormatting.DARK_AQUA));
        }

        if (amount <= 0 || "empty".equals(fluid) || fluid.isEmpty()) {
            tooltip.add(Component.translatable("gui.gonzotech.canister.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        int color = CanisterItem.getFluidColor(fluid);
        String nameKey = CanisterItem.getFluidLangKey(fluid);
        tooltip.add(GtUnits.fluidTitle(nameKey, amount, CAPACITY, color));
    }
}

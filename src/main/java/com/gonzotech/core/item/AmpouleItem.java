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
 * Ампула с жидкостью (128 mB) — чисто предмет (на пол не ставится, ПКМ не льёт).
 * Два наполненных типа:
 * <ul>
 *   <li>{@link ModItems#AMPOULE} — «Наполненная ампула» (из пустой стеклянной);</li>
 *   <li>{@link ModItems#FILLED_DURABLE_AMPOULE} — «Наполненная стойкая ампула»
 *       (из стойкой, борное стекло) — в будущем только она сможет держать
 *       агрессивные жидкости (цезий, натрий и т.п.).</li>
 * </ul>
 * Стакается по 64 штуки с одинаковым NBT содержимым.
 * Наполняются в Наполнителе; при опустошении в Наполнителе ампула ПРОПАДАЕТ
 * (одноразовая — оба типа, автор 24.09.2026).
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

    /** Стойкая ли ампула: по типу предмета (+legacy-флаг NBT старых стаков). */
    public static boolean isDurable(ItemStack stack) {
        if (stack.is(ModItems.FILLED_DURABLE_AMPOULE.get())) return true;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("durable");
    }

    /** Любая ампула (пустая, наполненная, стойкая). */
    public static boolean isAmpoule(ItemStack stack) {
        return stack.is(ModItems.AMPOULE.get())
                || stack.is(ModItems.FILLED_DURABLE_AMPOULE.get())
                || stack.is(ModItems.EMPTY_AMPOULE.get())
                || stack.is(ModItems.DURABLE_AMPOULE.get());
    }

    /** Наполненная ампула (обычная или стойкая) с жидкостью внутри. */
    public static boolean isFilledAmpoule(ItemStack stack) {
        return stack.is(ModItems.AMPOULE.get())
                || stack.is(ModItems.FILLED_DURABLE_AMPOULE.get());
    }

    public static ItemStack createFilled(String fluid, int amount, boolean durable) {
        ItemStack stack = new ItemStack(durable
                ? ModItems.FILLED_DURABLE_AMPOULE.get()
                : ModItems.AMPOULE.get());
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putString("fluid", fluid);
            tag.putInt("amount", Math.min(CAPACITY, amount));
        }));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        String fluid = getStoredFluid(stack);
        boolean durable = isDurable(stack);
        if ("empty".equals(fluid) || fluid.isEmpty()) {
            return Component.translatable(durable
                    ? "item.gonzotech.durable_ampoule"
                    : "item.gonzotech.empty_ampoule");
        }
        // Короткое имя жидкости по ГОСТу (resource.gonzotech.*) — один формат-ключ
        // на все текущие и будущие жидкости.
        String shortName = CanisterItem.getFluidLangKey(fluid);
        return Component.translatable(durable
                ? "item.gonzotech.filled_durable_ampoule.named"
                : "item.gonzotech.ampoule.named", Component.translatable(shortName));
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

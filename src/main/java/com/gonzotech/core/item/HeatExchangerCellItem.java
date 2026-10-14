package com.gonzotech.core.item;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.processing.AlloyProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Heat-exchanger component that retains the exact custom-alloy material identity. */
public final class HeatExchangerCellItem extends Item {

    public HeatExchangerCellItem(Properties properties) {
        super(properties);
    }

    /** Create a cell with the same canonical composition and derived tint as its alloy. */
    public static ItemStack fromComposition(AlloyComposition composition) {
        if (!AlloyEquipmentStats.isSupported(composition)) return ItemStack.EMPTY;
        AlloyProperties properties = AlloyProperties.from(composition).orElseThrow();
        ItemStack result = new ItemStack(ModItems.HEAT_EXCHANGER_CELL.get());
        result.set(ModDataComponents.ALLOY_COMPOSITION.get(), composition);
        result.set(ModDataComponents.ALLOY_TINT.get(), new AlloyTint(properties.argbTint()));
        return result;
    }

    /** The cell shows the full alloy composition and the same material-stat vector as its ingot. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!CustomAlloyItem.appendCompositionTooltip(stack, tooltip)) {
            tooltip.add(Component.translatable("tooltip.gonzotech.heat_exchanger_cell.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}

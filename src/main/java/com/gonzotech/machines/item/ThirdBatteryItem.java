package com.gonzotech.machines.item;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.ThirdStorageDefs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Passive portable milli-GTU. Never a ticking charger and never stacked. */
public final class ThirdBatteryItem extends BlockItem {
    public static final String ENERGY_KEY="GonzoBatteryGtuMilli";
    public ThirdBatteryItem(Block block, Properties properties) { super(block,properties.stacksTo(1)); }
    public static long storedMilli(ItemStack stack) {
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        return data==null ? 0 : Math.clamp(data.copyTag().getLong(ENERGY_KEY),0,ThirdStorageDefs.BATTERY.capacity());
    }
    public static void storeMilli(ItemStack stack, long amount) {
        long value=Math.clamp(amount,0,ThirdStorageDefs.BATTERY.capacity());
        stack.update(DataComponents.CUSTOM_DATA,CustomData.EMPTY,data->data.update(tag->tag.putLong(ENERGY_KEY,value)));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(GtUnits.gtuPair(GtUnits.x1(storedMilli(stack)/1000.0),GtUnits.x1(ThirdStorageDefs.BATTERY.capacity()/1000.0)));
        super.appendHoverText(stack,context,tooltip,flag);
    }
}

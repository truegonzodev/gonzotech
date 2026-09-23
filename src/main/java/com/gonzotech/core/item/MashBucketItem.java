package com.gonzotech.core.item;

import com.gonzotech.core.text.GtUnits;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ведро браги. Сохраняет и накапливает гниль (+0.4%/мин) при хранении в инвентаре.
 */
public class MashBucketItem extends BucketItem {

    public MashBucketItem(Supplier<? extends Fluid> supplier, Properties properties) {
        super(supplier.get(), properties.stacksTo(1).craftRemainder(Items.BUCKET));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            double rot = tag.getDouble("mash_rot");
            if (rot < 98.0) {
                double alc = tag.getDouble("mash_alc");
                rot = Math.min(98.0, rot + 0.40 / 1200.0);
                alc = Math.max(0.0, alc - 0.10 / 1200.0);
                tag.putDouble("mash_rot", rot);
                tag.putDouble("mash_alc", alc);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        double rot = (data != null) ? data.copyTag().getDouble("mash_rot") : 0.0;
        tooltip.add(GtUnits.rotLore(String.format(Locale.ROOT, "%.1f", rot)));
    }
}

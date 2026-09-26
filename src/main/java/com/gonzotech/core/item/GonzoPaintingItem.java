package com.gonzotech.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** Vanilla hanging/placement/renderer with a fixed, data-driven 4x5 painting variant. */
public final class GonzoPaintingItem extends HangingEntityItem {
    public static final ResourceKey<PaintingVariant> VARIANT = ResourceKey.create(Registries.PAINTING_VARIANT,
            ResourceLocation.fromNamespaceAndPath("gonzotech", "gonzo"));

    public GonzoPaintingItem(Properties properties) {
        super(EntityType.PAINTING, properties.rarity(Rarity.UNCOMMON)
                .component(DataComponents.ENTITY_DATA, variantData()));
    }

    private static CustomData variantData() {
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:painting");
        data.putString("variant", VARIANT.location().toString());
        return CustomData.of(data);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("painting.gonzotech.gonzo.title").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("painting.gonzotech.gonzo.author").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("painting.dimensions", 4, 5).withStyle(ChatFormatting.WHITE));
    }
}

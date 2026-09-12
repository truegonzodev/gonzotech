package com.gonzotech.machines.client;

import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Item-model tint source reading the server-authored {@code alloy_tint} component. */
public record AlloyTintSource(int defaultColor) implements ItemTintSource {

    public AlloyTintSource {
        defaultColor = 0xFF000000 | (defaultColor & 0x00FFFFFF);
    }

    public static final MapCodec<AlloyTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.INT.optionalFieldOf("default", 0xFF69737C).forGetter(AlloyTintSource::defaultColor)
    ).apply(instance, AlloyTintSource::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        AlloyTint tint = stack.get(ModDataComponents.ALLOY_TINT.get());
        return tint == null ? defaultColor : tint.argb();
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}

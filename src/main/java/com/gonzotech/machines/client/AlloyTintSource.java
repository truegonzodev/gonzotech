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
        return colorFilter(tint == null ? defaultColor : tint.argb());
    }

    /**
     * Generated-item tinting multiplies the texture RGB by this result. The
     * custom-alloy PNG is intentionally neutral grayscale, so its luminance
     * should define the final light/dark metal shading while a composition only
     * supplies hue and saturation. Scaling the brightest palette channel to
     * white is the small, shader-free equivalent of a "Color" blend mode: it
     * preserves the palette's HSV hue/saturation instead of multiplying its
     * brightness by the already gray source texture a second time.
     *
     * <p>The stored {@link AlloyTint} remains the exact weighted material
     * palette shown in lore; this conversion is render-only.</p>
     */
    private static int colorFilter(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum == 0) return 0xFF000000;

        int filteredRed = (red * 255 + maximum / 2) / maximum;
        int filteredGreen = (green * 255 + maximum / 2) / maximum;
        int filteredBlue = (blue * 255 + maximum / 2) / maximum;
        return 0xFF000000 | (filteredRed << 16) | (filteredGreen << 8) | filteredBlue;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}

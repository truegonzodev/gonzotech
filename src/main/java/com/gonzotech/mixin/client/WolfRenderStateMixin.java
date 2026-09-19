package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Поле «жирность» в {@link WolfRenderState} (двойник {@link CatRenderStateMixin}).
 */
@Mixin(WolfRenderState.class)
public abstract class WolfRenderStateMixin implements FatnessState {

    @Unique
    private float gonzo$fatness = 1.0F;

    @Override
    public float gonzotech$fatness() {
        return this.gonzo$fatness;
    }

    @Override
    public void gonzotech$setFatness(float value) {
        this.gonzo$fatness = value;
    }
}

package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Поле «жирность» в {@link CatRenderState}: модель в 1.21.4 не видит сущность,
 * только render state. Заполняется в {@link CatRendererMixin}.
 */
@Mixin(CatRenderState.class)
public abstract class CatRenderStateMixin implements FatnessState {

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

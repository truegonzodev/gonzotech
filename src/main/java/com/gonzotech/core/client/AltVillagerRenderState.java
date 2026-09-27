package com.gonzotech.core.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * Состояние рендера «альт-жителя». Имена меша фиксированы
 * ({@code ModelLayers.VILLAGER}), голова/шаг заполняются базовым
 * {@code LivingEntityRenderer.extractRenderState}; текстура скина приезжает из
 * {@code AltVillagerEntity.getVariant()} в {@link #texture} (0.3.36).
 */
public class AltVillagerRenderState extends LivingEntityRenderState {

    /** Скин конкретной сущности: textures/entity/alt/<имя>.png. */
    public ResourceLocation texture;
}

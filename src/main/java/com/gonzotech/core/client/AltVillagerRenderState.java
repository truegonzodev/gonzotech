package com.gonzotech.core.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Состояние рендера «альт-жителя». Никаких собственных полей не нужно: имена
 * меша фиксированы ({@code ModelLayers.VILLAGER}), а голова/шаг заполняются
 * базовым {@code LivingEntityRenderer.extractRenderState} — ровно как у вилладжера.
 */
public class AltVillagerRenderState extends LivingEntityRenderState {
}

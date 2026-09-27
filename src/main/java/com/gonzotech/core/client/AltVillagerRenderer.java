package com.gonzotech.core.client;

import com.gonzotech.core.entity.AltVillagerEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Рендерер «альт-жителя»: ванильный меш {@code ModelLayers.VILLAGER}, та же
 * тень 0.5, что у {@code VillagerRenderer}, и скин по варианту сущности
 * (0.3.36): четыре текстуры в {@code textures/entity/alt/}, выбор —
 * взвешенная рулетка 70/15/11/4 в самой сущности. Слоёв профессий/шляп нет:
 * наш житель один на все случаи.
 */
public class AltVillagerRenderer extends MobRenderer<AltVillagerEntity, AltVillagerRenderState, AltVillagerModel> {

    public AltVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new AltVillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public void extractRenderState(AltVillagerEntity entity, AltVillagerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.texture = entity.getVariant().texture();
    }

    @Override
    public ResourceLocation getTextureLocation(AltVillagerRenderState state) {
        return state.texture;
    }

    @Override
    public AltVillagerRenderState createRenderState() {
        return new AltVillagerRenderState();
    }
}

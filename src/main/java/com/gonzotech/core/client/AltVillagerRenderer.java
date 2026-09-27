package com.gonzotech.core.client;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Рендерер «альт-жителя»: ванильный меш {@code ModelLayers.VILLAGER}, та же
 * тень 0.5, что у {@code VillagerRenderer}, и своя текстура (63 % копия не
 * требуется — «любая текстура», автор 27.09.2026). Слоёв профессий/шляп нет:
 * наш житель один на все случаи.
 */
public class AltVillagerRenderer extends MobRenderer<AltVillagerEntity, AltVillagerRenderState, AltVillagerModel> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/entity/alt_villager.png");

    public AltVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new AltVillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AltVillagerRenderState state) {
        return TEXTURE;
    }

    @Override
    public AltVillagerRenderState createRenderState() {
        return new AltVillagerRenderState();
    }
}

package com.gonzotech.machines.client;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a synthesized custom-alloy sprite rather than applying Minecraft's
 * one-colour item-tint multiplier. The special-model base supplies the normal
 * generated-item transforms while this renderer supplies composition pixels.
 */
public final class AlloySpecialRenderer implements SpecialModelRenderer<AlloySpecialRenderer.RenderData> {

    public static final AlloySpecialRenderer INSTANCE = new AlloySpecialRenderer();
    private static final int DEFAULT_MIX_ARGB = 0xFF69737C;

    private AlloySpecialRenderer() {
    }

    @Override
    public RenderData extractArgument(ItemStack stack) {
        AlloyComposition composition = stack.get(ModDataComponents.ALLOY_COMPOSITION.get());
        AlloyTint tint = stack.get(ModDataComponents.ALLOY_TINT.get());
        return new RenderData(composition, tint == null ? DEFAULT_MIX_ARGB : tint.argb());
    }

    @Override
    public void render(
        RenderData data,
        ItemDisplayContext displayContext,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        boolean foil
    ) {
        ResourceLocation texture = AlloyDynamicTextureCache.textureFor(data);
        VertexConsumer consumer = ItemRenderer.getFoilBuffer(
            bufferSource,
            RenderType.entityCutoutNoCull(texture, false),
            false,
            foil
        );
        PoseStack.Pose pose = poseStack.last();

        // A generated item's front face: UV V is inverted because y=0 is the
        // bottom in model space while y=0 is the top row of a PNG.
        addVertex(consumer, pose, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, packedLight, packedOverlay);
        addVertex(consumer, pose, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        addVertex(consumer, pose, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F, packedLight, packedOverlay);
        addVertex(consumer, pose, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, packedLight, packedOverlay);
    }

    private static void addVertex(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float x,
        float y,
        float z,
        float u,
        float v,
        int packedLight,
        int packedOverlay
    ) {
        consumer.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /**
     * Immutable client render argument. The null composition fallback lets
     * malformed/legacy stacks retain a neutral dynamic texture instead of
     * crashing the item renderer; ordinary foundry output always has both
     * server-authored components.
     */
    public record RenderData(@Nullable AlloyComposition composition, int mixArgb) {
    }

    /** JSON-facing, stateless special-model definition. */
    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
            return INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}

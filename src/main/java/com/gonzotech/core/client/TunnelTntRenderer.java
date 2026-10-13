package com.gonzotech.core.client;

import com.gonzotech.core.block.TunnelPrimedTnt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.util.Mth;

/** Vanilla TNT renderer specialized for the persistent tunnel-TNT entity type. */
public final class TunnelTntRenderer extends EntityRenderer<TunnelPrimedTnt, TntRenderState> {

    private final BlockRenderDispatcher blockRenderer;

    public TunnelTntRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
        blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(TntRenderState state, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F);
        float fuse = state.fuseRemainingInTicks;
        if (fuse < 10.0F) {
            float flash = Mth.clamp(1.0F - fuse / 10.0F, 0.0F, 1.0F);
            flash *= flash;
            flash *= flash;
            float scale = 1.0F + flash * 0.3F;
            poseStack.scale(scale, scale, scale);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        if (state.blockState != null) {
            TntMinecartRenderer.renderWhiteSolidBlock(blockRenderer, state.blockState,
                poseStack, buffers, packedLight, (int) fuse / 5 % 2 == 0);
        }
        poseStack.popPose();
        super.render(state, poseStack, buffers, packedLight);
    }

    @Override
    public TntRenderState createRenderState() {
        return new TntRenderState();
    }

    @Override
    public void extractRenderState(TunnelPrimedTnt entity, TntRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.fuseRemainingInTicks = (float) entity.getFuse() - partialTick + 1.0F;
        state.blockState = entity.getBlockState();
    }
}

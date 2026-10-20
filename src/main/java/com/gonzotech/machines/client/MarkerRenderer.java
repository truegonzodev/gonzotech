package com.gonzotech.machines.client;

import com.gonzotech.machines.miner.MinerMarker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Рендер меток-вымпелов автошахтера (0.3.256, спека автора):
 * <ul>
 *   <li>квад метки 1,0×1,75 блока под текстуру 20×35, билборд по yaw;</li>
 *   <li>квад метки — блендинг 80 % непрозрачности;</li>
 *   <li>«слот» дисплея 14×14 на канве в пикселе (3,3) — 100 % непрозрачности:
 *       marker_flag.png (красный флажок) либо изометрия блока руды, как в GUI;</li>
 *   <li>несколько руд — листание по 3 секунды на руду.</li>
 * </ul>
 */
public final class MarkerRenderer {

    private MarkerRenderer() {
    }

    /**
     * Канва текстуры 20×35 px отображается без искажения пропорций на квад
     * 1,0×1,75 блока: 20 пикселей на блок по обеим осям.
     */
    public static final float QUAD_HALF_W = 0.5F;
    public static final float QUAD_H = 1.75F;
    /** Слот дисплея: пиксель (3,3) канвы 20×35, размер иконки 14×14 px. */
    public static final float SLOT_OFFSET_X = (3F / 20F) * (QUAD_HALF_W * 2F);
    public static final float SLOT_OFFSET_Y = (3F / 35F) * QUAD_H;
    public static final float SLOT_SIZE_X = (14F / 20F) * (QUAD_HALF_W * 2F);
    public static final float SLOT_SIZE_Y = (14F / 35F) * QUAD_H;
    /** Прозрачность квада метки — 80 % (автор). */
    public static final float QUAD_ALPHA = 0.8F;
    /** Листание руд — 3 секунды на руду (автор). */
    public static final long ORE_CYCLE_TICKS = 60L;

    private static final ResourceLocation MARKER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/waypoint_marker.png");
    private static final ResourceLocation FLAG_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/marker_flag.png");

    /** Масштаб изометрии руды под физический слот 0,7×0,7 блока. */
    private static final float ORE_SCALE = 0.65F;
    /** Дистанция отсечения — 32 чанка = 512 блоков (маркер не виден на любой дистанции). */
    private static final double MAX_DISTANCE_SQR = 512.0 * 512.0;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // AFTER_PARTICLES: для полупрозрачных «частицеподобных» квадов это надёжнее,
        // чем AFTER_TRANSLUCENT_BLOCKS (там сортировка/таргеты прозрачной геометрии
        // могут давать визуальные артефакты, особенно на Fabulous-графике).
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        List<MinerMarker> markers = MarkerClient.markers();
        if (markers.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        long gameTime = level.getGameTime();

        for (MinerMarker marker : markers) {
            double mx = marker.pos().getX() + 0.5;
            double my = marker.pos().getY() + 1.0;
            double mz = marker.pos().getZ() + 0.5;
            double dx = mx - camPos.x;
            double dy = my - camPos.y;
            double dz = mz - camPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQR) continue;

            float yawDeg = (float) Math.toDegrees(Mth.atan2(-dx, -dz));

            // 1) Квад метки (1,0×1,75 блока) — 80 % непрозрачности.
            // Блендинг оставляем включённым и для флажка: в обеих текстурах есть
            // прозрачные пиксели, иначе они рисуются чёрными полями.
            RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, MARKER_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            pose.pushPose();
            pose.translate(dx, dy, dz);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yawDeg));
            BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            addQuad(buf, pose, -QUAD_HALF_W, 0F, QUAD_HALF_W, QUAD_H, 0F, QUAD_ALPHA);
            BufferUploader.drawWithShader(buf.buildOrThrow());

            // 2) Дисплей в слоте (пиксель 3,3 канвы, иконка 14×14) — 100 % непрозрачности.
            float slotLeft = -QUAD_HALF_W + SLOT_OFFSET_X;
            float slotTop = QUAD_H - SLOT_OFFSET_Y;
            float slotBottom = slotTop - SLOT_SIZE_Y;
            float slotRight = slotLeft + SLOT_SIZE_X;
            if (!marker.ores().isEmpty()) {
                int index = (int) ((gameTime / ORE_CYCLE_TICKS) % marker.ores().size());
                Item ore = marker.ores().get(index);
                float cx = (slotLeft + slotRight) / 2F;
                float cy = (slotTop + slotBottom) / 2F;
                pose.pushPose();
                pose.translate(cx, cy, 0.02F);
                pose.scale(ORE_SCALE, ORE_SCALE, ORE_SCALE);
                MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
                mc.getItemRenderer().renderStatic(new ItemStack(ore, 1), ItemDisplayContext.GUI,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
                buffers.endBatch();
                pose.popPose();
            } else {
                RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
                RenderSystem.setShaderTexture(0, FLAG_TEXTURE);
                BufferBuilder flag = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                addQuad(flag, pose, slotLeft, slotBottom, slotRight, slotTop, 0.02F, 1F);
                BufferUploader.drawWithShader(flag.buildOrThrow());
            }
            pose.popPose();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    /** Прямоугольник в плоскости XY (нормаль +Z), v=0 сверху. */
    private static void addQuad(BufferBuilder buf, PoseStack pose, float x0, float y0,
                                float x1, float y1, float z, float alpha) {
        com.mojang.blaze3d.vertex.PoseStack.Pose last = pose.last();
        buf.addVertex(last.pose(), x0, y0, z).setUv(0F, 1F).setColor(1F, 1F, 1F, alpha);
        buf.addVertex(last.pose(), x1, y0, z).setUv(1F, 1F).setColor(1F, 1F, 1F, alpha);
        buf.addVertex(last.pose(), x1, y1, z).setUv(1F, 0F).setColor(1F, 1F, 1F, alpha);
        buf.addVertex(last.pose(), x0, y1, z).setUv(0F, 0F).setColor(1F, 1F, 1F, alpha);
    }
}

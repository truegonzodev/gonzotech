package com.gonzotech.machines.client;

import com.gonzotech.machines.miner.MinerMarker;
import com.gonzotech.machines.registry.ModMachines;
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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

/**
 * Рендер меток-вымпелов автошахтера (0.3.256, спека автора):
 * <ul>
 *   <li>квад метки 1,0×1,75 блока под текстуру 20×35, билборд по yaw;</li>
 *   <li>квад метки — блендинг 80 % непрозрачности;</li>
 *   <li>«слот» дисплея 14×14 на канве в пикселе (3,3) — 100 % непрозрачности:
 *       плоская 2D-изометрия блока (руда, источник колебаний или геофон),
 *       собранная из трёх спрайтов граней прямо в плоскости вымпела;
 *       красный флажок остался только запасным/тестовым изображением;</li>
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

    /** Затенение плоских граней 2D-изометрии: верх / юг / запад, как у GUI-куба. */
    private static final float ORE_TOP_SHADE = 1.0F;
    private static final float ORE_RIGHT_SHADE = 0.8F;
    private static final float ORE_LEFT_SHADE = 0.6F;
    /** Размер изометрии внутри слота: 65% (на 35% меньше полной рамки). */
    private static final float ORE_ICON_SCALE = 0.65F;
    /** Все три грани иконки лежат в одной плоскости дисплея, без толщины 3D-модели. */
    private static final float DISPLAY_Z = 0.02F;
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
            Item displayItem = null;
            if (!marker.ores().isEmpty()) {
                int index = (int) ((gameTime / ORE_CYCLE_TICKS) % marker.ores().size());
                displayItem = marker.ores().get(index);
            } else if (marker.kind() == MinerMarker.Kind.STATION) {
                // Существовавшие до этого пакета метки Плотника не хранят иконку:
                // по смыслу STATION-метка всегда зовёт поставить источник.
                displayItem = ModMachines.SEISMIC_SOURCE_ITEM.get();
            }
            boolean drewDisplay = displayItem != null && drawFlatOreIcon(
                mc, pose, displayItem, slotLeft, slotBottom, slotRight, slotTop);
            if (!drewDisplay) {
                drawFlag(pose, slotLeft, slotBottom, slotRight, slotTop);
            }
            pose.popPose();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    private static void drawFlag(PoseStack pose, float slotLeft, float slotBottom,
                                 float slotRight, float slotTop) {
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, FLAG_TEXTURE);
        BufferBuilder flag = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        addQuad(flag, pose, slotLeft, slotBottom, slotRight, slotTop, DISPLAY_Z, 1F);
        BufferUploader.drawWithShader(flag.buildOrThrow());
    }

    /**
     * Плоская 2D-изометрия блока в слоте вымпела.
     *
     * <p>Намеренно не {@code ItemRenderer.renderStatic}: настоящая 3D-модель в
     * мире поворачивается вместе с камерой/вымпелом, торчит из плоскости
     * спрайта и при отключённом depth-write показывает задние грани. Здесь
     * верх, западная и южная грани — три текстурированных параллелограмма с
     * одной и той же Z-координатой {@link #DISPLAY_Z}; это картинка на метке,
     * а не объект в мире.</p>
     */
    private static boolean drawFlatOreIcon(Minecraft mc, PoseStack pose, Item displayItem,
                                           float left, float bottom, float right, float top) {
        if (!(displayItem instanceof BlockItem blockItem)) return false;

        BlockState state = blockItem.getBlock().defaultBlockState();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        TextureAtlasSprite fallback = model.getParticleIcon(ModelData.EMPTY);
        if (fallback == null) return false;

        TextureAtlasSprite topSprite = faceSprite(model, state, Direction.UP, fallback);
        TextureAtlasSprite leftSprite = faceSprite(model, state, Direction.WEST, fallback);
        TextureAtlasSprite rightSprite = faceSprite(model, state, Direction.SOUTH, fallback);

        float centerX = (left + right) / 2F;
        float centerY = (bottom + top) / 2F;
        float halfWidth = (right - left) * ORE_ICON_SCALE / 2F;
        float halfHeight = (top - bottom) * ORE_ICON_SCALE / 2F;
        left = centerX - halfWidth;
        right = centerX + halfWidth;
        bottom = centerY - halfHeight;
        top = centerY + halfHeight;
        float sideTopY = centerY + (top - bottom) / 4F;
        float sideBottomY = centerY - (top - bottom) / 4F;

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, topSprite.atlasLocation());
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Верх: ромб (запад -> север -> восток -> юг).
        addFlatFace(buf, pose, topSprite, ORE_TOP_SHADE,
            new float[]{left, sideTopY, centerX, top, right, sideTopY, centerX, centerY},
            new float[]{0F, 0F, 1F, 0F, 1F, 1F, 0F, 1F});
        // Левая грань: параллелограмм под западной половиной верха.
        addFlatFace(buf, pose, leftSprite, ORE_LEFT_SHADE,
            new float[]{left, sideBottomY, centerX, bottom, centerX, centerY, left, sideTopY},
            new float[]{0F, 1F, 1F, 1F, 1F, 0F, 0F, 0F});
        // Правая грань: параллелограмм под восточной половиной верха.
        addFlatFace(buf, pose, rightSprite, ORE_RIGHT_SHADE,
            new float[]{centerX, bottom, right, sideBottomY, right, sideTopY, centerX, centerY},
            new float[]{0F, 1F, 1F, 1F, 1F, 0F, 0F, 0F});

        BufferUploader.drawWithShader(buf.buildOrThrow());
        return true;
    }

    private static TextureAtlasSprite faceSprite(BakedModel model, BlockState state,
                                                 Direction face, TextureAtlasSprite fallback) {
        List<BakedQuad> quads = model.getQuads(
            state, face, RandomSource.create(42L), ModelData.EMPTY, null);
        if (quads == null || quads.isEmpty() || quads.getFirst().getSprite() == null) {
            return fallback;
        }
        return quads.getFirst().getSprite();
    }

    /** Один плоский четырёхугольник иконки; все вершины имеют одинаковую Z. */
    private static void addFlatFace(BufferBuilder buf, PoseStack pose, TextureAtlasSprite sprite,
                                    float shade, float[] points, float[] uvs) {
        com.mojang.blaze3d.vertex.PoseStack.Pose last = pose.last();
        for (int i = 0; i < 4; i++) {
            buf.addVertex(last.pose(), points[i * 2], points[i * 2 + 1], DISPLAY_Z)
                .setUv(sprite.getU(uvs[i * 2]), sprite.getV(uvs[i * 2 + 1]))
                .setColor(shade, shade, shade, 1F);
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

package com.gonzotech.sunevent.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Суневеты фаза 3 — клиентский снег вместо дождя (только Оверворлд).
 *
 * <p>В окне E−1..E+1 при дожде {@code SpaceSkyEffects.renderSnowAndRain}
 * рисует эти снежинки и возвращает true — ванильный дождь не рисуется,
 * {@code tickRain} тоже true (ванильные частицы дождя не тикают).
 *
 * <p><b>Модель (ТЗ автора, 2026-09-18):</b> снежинки живут в КООРДИНАТАХ
 * ОТНОСИТЕЛЬНО ИГРОКА — «купол» ДВИЖЕТСЯ ВМЕСТЕ с игроком по горизонтали
 * (а не дублируется заново при перемещении — было: мировые координаты,
 * микрофризы и «повторный посев» купола при ходьбе). Спавн — по ВСЕЙ
 * высоте коридора (от земли до верха полосы, а не только на высоте 16–32):
 * частицы есть и у земли, и над головой. Умершая у земли снежинка
 * переспавнивается на случайной высоте коридора.
 *
 * <p><b>Смерть снежинки:</b> дно коридора = {@code Heightmap.MOTION_BLOCKING}
 * (верхний блок, куда падает небесный свет — тот же, что у ванильной
 * погоды и у серверной укладки слоёв). Кулл одной int-выборкой хэджмапа
 * на кадр на снежинку (БЕЗ getBlockState/getCollisionShape на кадр —
 * причина микрофризов прошлой версии).
 *
 * <p><b>Геометрия (1.21.4):</b> вершины — камерно-относительные (для
 * купола, движущегося с игроком, это РОВНО сохранённые rel-координаты);
 * вращение применяет шейдер ({@code u_position_matrix} = R_view в
 * weather-pass; CPU-матрица = identity, иначе двойное вращение — V²).
 *
 * <p><b>Вид:</b> крестик 3×3 из ванильной {@code textures/environment/snow.png}
 * (та же текстура, что у ванильных колонн снега; тайл колонка 2 ряд 8).
 */
public final class SunEventSnowRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SNOW_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

    /** 220 × 6 (автор: снежинок мало — «в раз 5–8 больше»). */
    private static final int FLAKES = 1320;
    private static final float AREA = 26.0F;   // горизонтальный радиус купола
    private static final float TOP = 32.0F;    // верх коридора над игроком
    private static final float FALL = 0.03F;   // падение, блоков/кадр (автор: /3 от ванильной 0.09)
    private static final float SWAY = 0.02F;   // боковой дрейф
    private static final float SIZE = 0.28F;   // базовая полуширина квада (автор: ×3–4 от 0.08)

    // UV чистого крестика в snow.png: колонка 2 из 4, ряд 8 из 16 (тайлы 16×16).
    private static final float U0 = 2.0F / 4.0F, U1 = 3.0F / 4.0F;
    private static final float V0 = 8.0F / 16.0F, V1 = 9.0F / 16.0F;

    // Купол живёт в ОТНОСИТЕЛЬНЫХ к камере координатах (купол едет с игроком).
    private static final double[] RX = new double[FLAKES];
    private static final double[] RY = new double[FLAKES];
    private static final double[] RZ = new double[FLAKES];
    private static final float[] PHASE = new float[FLAKES];
    private static final float[] SPEED = new float[FLAKES];

    private static boolean seeded = false;
    private static long frame;
    private static boolean loggedMatrix = false;

    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private SunEventSnowRenderer() {
    }

    /** Дно коридора для данной rel-позиции (heightmap), rel-к камере. */
    private static double groundRel(ClientLevel level, double camX, double camY, double camZ, double rx, double rz) {
        int wx = (int) Math.floor(camX + rx);
        int wz = (int) Math.floor(camZ + rz);
        if (level.hasChunk(wx, wz)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - camY;
        }
        return 0.0;
    }

    private static void reseed(ClientLevel level, int i, double camX, double camY, double camZ) {
        RX[i] = (Math.random() * 2.0 - 1.0) * AREA;
        RZ[i] = (Math.random() * 2.0 - 1.0) * AREA;
        double ground = groundRel(level, camX, camY, camZ, RX[i], RZ[i]);
        // Спавн по ВСЕЙ высоте коридора (автор: и у земли, и над головой),
        // под крышей — выше её (над навесом).
        double min = ground + 0.1;
        double max = Math.max(TOP, min + 0.5);
        RY[i] = min + Math.random() * (max - min);
        PHASE[i] = (float) Math.random();
        SPEED[i] = (float) Math.random() * 0.017F;
    }

    /** Кадр снега: обновить купол и нарисовать его. */
    public static void render(float partialTick, double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (!seeded) {
            for (int i = 0; i < FLAKES; i++) {
                reseed(level, i, camX, camY, camZ);
            }
            seeded = true;
        }
        frame++;

        for (int i = 0; i < FLAKES; i++) {
            double t = frame * 0.01 + PHASE[i] * 12.566;
            RX[i] += Math.sin(t) * SWAY;
            RZ[i] += Math.cos(t * 1.13) * SWAY;
            RY[i] -= FALL + SPEED[i];
            if (RY[i] < groundRel(level, camX, camY, camZ, RX[i], RZ[i]) + 0.05) {
                reseed(level, i, camX, camY, camZ);
            }
        }

        // Разовая диагностика пайплайна.
        if (!loggedMatrix) {
            loggedMatrix = true;
            LOGGER.info("[Gonzo Tech] SnowRenderer: modelView в weather-pass = {}",
                RenderSystem.getModelViewMatrix());
        }

        // Биллборд-базис из направления взгляда игрока (1.21.4: Vec3.directionFromRotation).
        Vec3 look = mc.player != null
            ? Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot())
            : new Vec3(0.0, 0.0, 1.0);
        Vec3 right = look.cross(UP);
        if (right.length() < 1.0E-3) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SNOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < FLAKES; i++) {
            double s = SIZE * (0.7 + PHASE[i] * 0.6); // разброс размеров
            double rx = right.x * s, ry = right.y * s, rz = right.z * s;
            double ux = up.x * s, uy = up.y * s, uz = up.z * s;
            double x = RX[i], y = RY[i], z = RZ[i]; // уже камерно-относительные
            buf.addVertex((float) (x - rx - ux), (float) (y - ry - uy), (float) (z - rz - uz)).setColor(255, 255, 255, 255).setUv(U0, V1);
            buf.addVertex((float) (x + rx - ux), (float) (y + ry - uy), (float) (z + rz - uz)).setColor(255, 255, 255, 255).setUv(U1, V1);
            buf.addVertex((float) (x + rx + ux), (float) (y + ry + uy), (float) (z + rz + uz)).setColor(255, 255, 255, 255).setUv(U1, V0);
            buf.addVertex((float) (x - rx + ux), (float) (y - ry + uy), (float) (z - rz + uz)).setColor(255, 255, 255, 255).setUv(U0, V0);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}

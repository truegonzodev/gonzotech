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
import net.minecraft.core.BlockPos;
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
 * <p><b>Поведение (ТЗ автора, 2026-09-18):</b> невидимая горизонтальная зона
 * вокруг игрока; внутри зоны ищется верхний блок, куда падает небесный свет
 * ({@code Heightmap.MOTION_BLOCKING} — тот же, что у ванильной погоды и у
 * серверной укладки слоёв); снежинки появляются на случайной высоте чуть
 * выше уровня глаз (или над крышей, если игрок под навесом) и падают
 * вертикально; исчезают, коснувшись верхнего блока или жидкости.
 *
 * <p><b>Вид:</b> не белый квад, а настоящая снежинка 3×3-крестиком —
 * UV-тайл из ванильной {@code textures/environment/snow.png} (та же
 * текстура, которой ванильный снегопад рисует колонны). В snow.png
 * (64×256) чистый одиночный крестик сидит в тайле (колонка 2, ряд 8),
 * разметка 4×16 тайлов по 16×16 пикселей.
 *
 * <p><b>Геометрия (1.21.4, frame-graph пайплайн):</b> вершины только
 * камерно-относительные (flake − cam, как колонны ванильного дождя);
 * вращающее view-преобразование применяет САМ шейдер —
 * {@code u_position_matrix} = вершина стека model-view = R_view в
 * weather-pass. CPU-матрицу применять НЕЛЬЗЯ: после фикса 6710aad
 * матрица из renderSky умножалась на стековую R_view и давала двойное
 * вращение — «сектор», гоняющийся за камерой, вертикальный поток,
 * переходящий в диагональный/горизонтальный (автор, скрин).
 */
public final class SunEventSnowRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SNOW_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

    private static final int FLAKES = 220;
    private static final float AREA = 26.0F;        // горизонтальная зона вокруг камеры
    private static final float SPAWN_ABOVE = 2.5F;  // спавн чуть выше глаз/крыши
    private static final float FALL = 0.09F;        // падение, блоков/кадр
    private static final float SWAY = 0.02F;        // боковой дрейф
    private static final float SIZE = 0.08F;        // базовая полуширина квада

    // UV чистого крестика в snow.png: колонка 2 из 4, ряд 8 из 16 (тайлы 16×16).
    private static final float U0 = 2.0F / 4.0F, U1 = 3.0F / 4.0F;
    private static final float V0 = 8.0F / 16.0F, V1 = 9.0F / 16.0F;

    private static final double[] X = new double[FLAKES];
    private static final double[] Y = new double[FLAKES];
    private static final double[] Z = new double[FLAKES];
    private static final double[] DIEY = new double[FLAKES];
    private static final float[] PHASE = new float[FLAKES];
    private static final float[] SPEED = new float[FLAKES];

    private static boolean seeded = false;
    private static long frame;
    private static boolean loggedMatrix = false;
    private static final BlockPos.MutableBlockPos MUTABLE = new BlockPos.MutableBlockPos();

    private SunEventSnowRenderer() {
    }

    private static void reseed(ClientLevel level, int i, double camX, double camY, double camZ) {
        X[i] = camX + (Math.random() * 2.0 - 1.0) * AREA;
        Z[i] = camZ + (Math.random() * 2.0 - 1.0) * AREA;
        int x = (int) Math.floor(X[i]);
        int z = (int) Math.floor(Z[i]);
        double top = camY;
        if (level.hasChunk(x, z)) {
            top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
        DIEY[i] = top + 0.05;
        // Спавним чуть выше уровня глаз либо над крышей — что выше.
        Y[i] = Math.max(camY, DIEY[i]) + Math.random() * SPAWN_ABOVE;
        PHASE[i] = (float) Math.random();
        SPEED[i] = (float) Math.random() * 0.05F;
    }

    /** Кадр снега: обновить облако и нарисовать его. */
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
            X[i] += Math.sin(t) * SWAY;
            Z[i] += Math.cos(t * 1.13) * SWAY;
            Y[i] -= FALL + SPEED[i];
            double dx = X[i] - camX;
            double dz = Z[i] - camZ;
            boolean die = Y[i] < DIEY[i] || dx * dx + dz * dz > AREA * AREA;
            if (!die) {
                // Коснулся блока с коллизией или жидкости — исчезаем.
                MUTABLE.set(X[i], Y[i], Z[i]);
                var state = level.getBlockState(MUTABLE);
                if (!state.isAir()
                    && (!state.getFluidState().isEmpty()
                        || !state.getCollisionShape(level, MUTABLE).isEmpty())) {
                    die = true;
                }
            }
            if (die) {
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
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
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
            double x = X[i] - camX, y = Y[i] - camY, z = Z[i] - camZ;
            buf.addVertex((float) (x - rx - ux), (float) (y - ry - uy), (float) (z - rz - uz)).setColor(255, 255, 255, 255).setUv(U0, V1);
            buf.addVertex((float) (x + rx - ux), (float) (y + ry - uy), (float) (z + rz - uz)).setColor(255, 255, 255, 255).setUv(U1, V1);
            buf.addVertex((float) (x + rx + ux), (float) (y + ry + uy), (float) (z + rz + uz)).setColor(255, 255, 255, 255).setUv(U1, V0);
            buf.addVertex((float) (x - rx + ux), (float) (y - ry + uy), (float) (z - rz + uz)).setColor(255, 255, 255, 255).setUv(U0, V0);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}

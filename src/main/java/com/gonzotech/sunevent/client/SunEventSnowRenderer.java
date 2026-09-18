package com.gonzotech.sunevent.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Суневеты фаза 3 — клиентский снег вместо дождя (только Оверворлд).
 *
 * <p>В окне E−1..E+1 при дожде {@code SpaceSkyEffects.renderSnowAndRain}
 * рисует эти снежинки и возвращает true — ванильный дождь не рисуется,
 * {@code tickRain} тоже true (ванильные частицы дождя не тикают).
 *
 * <p>Снежинки = мелкие биллборд-квады, всегда повёрнутые к камере; та же
 * безсветовая POSITION_COLOR-техника, что у звёзд скайбокса. Облако живёт
 * вокруг камеры (26×26×24 блоков), улетевшие/выпавшие снежинки пересеиваются.
 *
 * <p><b>Геометрия (1.21.4, frame-graph пайплайн):</b> шейдер строит
 * {@code gl_Position = ProjMat * ModelViewMat * pos}. Мир рисуется с
 * камерно-относительными координатами: ванильный дождь
 * ({@code WeatherEffectRenderer.renderInstances}) передаёт {@code column − camPos}
 * без своей матрицы, world border — {@code border − cam} через GPU-transform.
 * Мы делаем то же самое: вершины = {@code flake − cam}, а view-матрицей служит
 * <b>матрица вида, которую игра сама передаёт в {@code renderSky}</b>
 * (та, которой рисуются купол и звёзды — их правильность подтверждена).
 * Свою view-матрицу из углов игрока строить НЕЛЬЗЯ: ручная матрица
 * складывается с матрицей пайплайна и даёт «стену», крутящуюся вокруг
 * прицела (автор, 2026-09-18, скрин).
 */
public final class SunEventSnowRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int FLAKES = 220;
    private static final float AREA = 26.0F;   // квадрат вокруг камеры (блоки)
    private static final float HEIGHT = 12.0F; // вертикальный коридор вокруг камеры
    private static final float FALL = 0.09F;   // падение, блоков/кадр
    private static final float SWAY = 0.02F;   // боковой дрейф
    private static final float SIZE = 0.045F;  // размер снежинки (полуширина квада)

    private static final double[] X = new double[FLAKES];
    private static final double[] Y = new double[FLAKES];
    private static final double[] Z = new double[FLAKES];
    private static final float[] PHASE = new float[FLAKES];
    private static final float[] SPEED = new float[FLAKES];

    private static boolean seeded = false;
    private static long frame;
    private static boolean loggedMatrix = false;

    private SunEventSnowRenderer() {
    }

    private static void reseed(int i, double camX, double camY, double camZ) {
        X[i] = camX + (Math.random() * 2.0 - 1.0) * AREA;
        Y[i] = camY + (Math.random() * 2.0 - 1.0) * HEIGHT;
        Z[i] = camZ + (Math.random() * 2.0 - 1.0) * AREA;
        PHASE[i] = (float) Math.random();
        SPEED[i] = (float) Math.random() * 0.05F; // разброс скоростей падения
    }

    /**
     * Кадр снега: обновить облако и нарисовать его.
     *
     * @param viewMatrix матрица вида текущего кадра (параметр
     *                   {@code DimensionSpecialEffects.renderSky}, та же, что
     *                   рисует купол/звёзды); null — только что стартовал кадр,
     *                   рисуем без матрицы (вершины уже камерно-относительные).
     */
    public static void render(float partialTick, double camX, double camY, double camZ,
                              Matrix4f viewMatrix) {
        Minecraft mc = Minecraft.getInstance();
        if (!seeded) {
            for (int i = 0; i < FLAKES; i++) {
                reseed(i, camX, camY, camZ);
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
            double dy = Y[i] - camY;
            double dz = Z[i] - camZ;
            if (dx * dx + dz * dz > AREA * AREA || dy < -HEIGHT || dy > HEIGHT) {
                reseed(i, camX, camY, camZ);
            }
        }

        // Разовая диагностика пайплайна: что именно игра считает «view» в этот момент.
        if (!loggedMatrix) {
            loggedMatrix = true;
            LOGGER.info("[Gonzo Tech] SnowRenderer: viewMatrix из renderSky = {}", viewMatrix);
            LOGGER.info("[Gonzo Tech] SnowRenderer: RenderSystem.getModelViewMatrix() = {}",
                RenderSystem.getModelViewMatrix());
        }

        // Биллборд-базис из направления взгляда игрока (1.21.4: Vec3.directionFromRotation).
        Vec3 look = mc.player != null
            ? Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot())
            : new Vec3(0.0, 0.0, 1.0);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.length() < 1.0E-3) {
            right = new Vec3(1.0, 0.0, 0.0); // взгляд ровно вверх/вниз — дегенерация
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();
        double rx = right.x * SIZE, ry = right.y * SIZE, rz = right.z * SIZE;
        double ux = up.x * SIZE, uy = up.y * SIZE, uz = up.z * SIZE;

        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderSystem.enableDepthTest();

        // Матрица — только та, что предоставила игра (как для купола/звёзд).
        // Вершины камерно-относительные (flake − cam) — ровно как ванильный дождь.
        Matrix4f m = viewMatrix != null ? viewMatrix : new Matrix4f().identity();

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < FLAKES; i++) {
            double x = X[i] - camX, y = Y[i] - camY, z = Z[i] - camZ;
            buf.addVertex(m, (float) (x - rx - ux), (float) (y - ry - uy), (float) (z - rz - uz)).setColor(255, 255, 255, 255);
            buf.addVertex(m, (float) (x + rx - ux), (float) (y + ry - uy), (float) (z + rz - uz)).setColor(255, 255, 255, 255);
            buf.addVertex(m, (float) (x + rx + ux), (float) (y + ry + uy), (float) (z + rz + uz)).setColor(255, 255, 255, 255);
            buf.addVertex(m, (float) (x - rx + ux), (float) (y - ry + uy), (float) (z - rz + uz)).setColor(255, 255, 255, 255);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}

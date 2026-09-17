package com.gonzotech.sunevent.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
 */
public final class SunEventSnowRenderer {

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

    private SunEventSnowRenderer() {
    }

    private static void reseed(int i, double camX, double camY, double camZ) {
        X[i] = camX + (Math.random() * 2.0 - 1.0) * AREA;
        Y[i] = camY + (Math.random() * 2.0 - 1.0) * HEIGHT;
        Z[i] = camZ + (Math.random() * 2.0 - 1.0) * AREA;
        PHASE[i] = (float) Math.random();
        SPEED[i] = (float) Math.random() * 0.05F; // разброс скоростей падения
    }

    /** Кадр снега: обновить облако и нарисовать его в мировых координатах. */
    public static void render(float partialTick, double camX, double camY, double camZ) {
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
        // joml: Matrix4fStack НАСЛЕДУЕТ Matrix4f — сам стек и есть текущая (верхняя) матрица.
        Matrix4f m = RenderSystem.getModelViewStack();

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < FLAKES; i++) {
            double x = X[i], y = Y[i], z = Z[i];
            buf.addVertex(m, (float) (x - rx - ux), (float) (y - ry - uy), (float) (z - rz - uz)).setColor(255, 255, 255, 255);
            buf.addVertex(m, (float) (x + rx - ux), (float) (y + ry - uy), (float) (z + rz - uz)).setColor(255, 255, 255, 255);
            buf.addVertex(m, (float) (x + rx + ux), (float) (y + ry + uy), (float) (z + rz + uz)).setColor(255, 255, 255, 255);
            buf.addVertex(m, (float) (x - rx + ux), (float) (y - ry + uy), (float) (z - rz + uz)).setColor(255, 255, 255, 255);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}

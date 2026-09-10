package com.gonzotech.space.client;

import com.gonzotech.space.SpaceDimensions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Рендерер горизонта событий, гравитационного линзирования и аккреционного диска Чёрных Дыр.
 *
 * <p>Рисует релятивистскую Чёрную Дыру в координатах (0, 160, 0):
 * <ul>
 *   <li><b>Yx989-k2</b>: радиус Шварцшильда 120 блоков</li>
 *   <li><b>Zangler-11</b>: радиус Шварцшильда 200 блоков</li>
 * </ul>
 *
 * <p>Особенности:
 * <ul>
 *   <li>Высокопроизводительный GPU-шейдер ({@link BlackHoleShader}) с белым фотонным кольцом,
 *       компактным аккреционным диском (180–400 блоков) и плавными арками Интерстеллара (160+ FPS).</li>
 * </ul>
 */
public final class BlackHoleRenderer {

    private BlackHoleRenderer() {
    }

    /** Координаты центра чёрной дыры в мире. */
    public static final double CENTER_X = 0.0;
    public static final double CENTER_Y = 160.0;
    public static final double CENTER_Z = 0.0;

    /** Радиусы горизонта событий по измерениям. */
    public static final float RADIUS_YX989_K2 = 120.0F;
    public static final float RADIUS_ZANGLER_11 = 200.0F;

    /** Число секторов и колец запасной сферы. */
    private static final int SPHERE_STACKS = 64;
    private static final int SPHERE_SECTORS = 64;

    /** Предрассчитанный массив вершин единичной сферы (fallback). */
    private static float[] sphereVertices;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        ResourceKey<Level> dim = level.dimension();
        float radius;
        if (dim == SpaceDimensions.BLACKHOLE_YX989_K2) {
            radius = RADIUS_YX989_K2;
        } else if (dim == SpaceDimensions.BLACKHOLE_ZANGLER_11) {
            radius = RADIUS_ZANGLER_11;
        } else {
            return; // Не измерение с чёрной дырой
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        // Основной рендер через шейдер гравитационного линзирования
        if (BlackHoleShader.init()) {
            BlackHoleShader.render(camPos, event.getModelViewMatrix(), event.getProjectionMatrix(), radius);
            return;
        }

        // Запасной путь (fallback) — сплошная чёрная полая сфера
        float rx = (float) (CENTER_X - camPos.x);
        float ry = (float) (CENTER_Y - camPos.y);
        float rz = (float) (CENTER_Z - camPos.z);

        Matrix4f mv = new Matrix4f(event.getModelViewMatrix());
        mv.translate(rx, ry, rz);
        mv.scale(radius);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();

        renderFallbackBlackSphere(mv);

        mvStack.popMatrix();
    }

    /** Запасная отрисовка непроглядной черной полой сферы при ошибке шейдера. */
    private static void renderFallbackBlackSphere(Matrix4f matrix) {
        if (sphereVertices == null) {
            sphereVertices = buildUnitSphere(SPHERE_STACKS, SPHERE_SECTORS);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < sphereVertices.length; i += 3) {
            buf.addVertex(matrix, sphereVertices[i], sphereVertices[i + 1], sphereVertices[i + 2])
               .setColor(0xFF000000);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.enableBlend();
    }

    /**
     * Генерация массива вершин гладкой единичной сферы.
     */
    private static float[] buildUnitSphere(int stacks, int sectors) {
        float[] vertices = new float[stacks * sectors * 4 * 3];
        int idx = 0;

        for (int i = 0; i < stacks; i++) {
            float phi1 = (float) (Math.PI * (double) i / stacks - Math.PI / 2.0);
            float phi2 = (float) (Math.PI * (double) (i + 1) / stacks - Math.PI / 2.0);

            float y1 = (float) Math.sin(phi1);
            float r1 = (float) Math.cos(phi1);
            float y2 = (float) Math.sin(phi2);
            float r2 = (float) Math.cos(phi2);

            for (int j = 0; j < sectors; j++) {
                float theta1 = (float) (2.0 * Math.PI * (double) j / sectors);
                float theta2 = (float) (2.0 * Math.PI * (double) (j + 1) / sectors);

                float x11 = r1 * (float) Math.cos(theta1);
                float z11 = r1 * (float) Math.sin(theta1);

                float x12 = r1 * (float) Math.cos(theta2);
                float z12 = r1 * (float) Math.sin(theta2);

                float x21 = r2 * (float) Math.cos(theta1);
                float z21 = r2 * (float) Math.sin(theta1);

                float x22 = r2 * (float) Math.cos(theta2);
                float z22 = r2 * (float) Math.sin(theta2);

                // Quad v1, v2, v3, v4
                vertices[idx++] = x11;
                vertices[idx++] = y1;
                vertices[idx++] = z11;

                vertices[idx++] = x12;
                vertices[idx++] = y1;
                vertices[idx++] = z12;

                vertices[idx++] = x22;
                vertices[idx++] = y2;
                vertices[idx++] = z22;

                vertices[idx++] = x21;
                vertices[idx++] = y2;
                vertices[idx++] = z21;
            }
        }
        return vertices;
    }
}

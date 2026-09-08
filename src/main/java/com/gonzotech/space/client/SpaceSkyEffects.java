package com.gonzotech.space.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Скайбокс космических измерений (Фаза 4, Часть 2): собственный фон и небесные
 * тела для каждого мира (Луна / Марс / Европа).
 *
 * <p>Полностью заменяет ванильный рендер неба ({@link #renderSky} возвращает
 * {@code true}) и рисует всё сами:
 * <ol>
 *   <li>однотонный «купол-куб» цвета {@link #backgroundArgb} — фон вакуума;</li>
 *   <li>список {@link CelestialBody} — у каждого тела СВОЯ текстура (сохраняем
 *       ванильную пиксельную плотность, ничего не масштабируем из одной
 *       картинки) и своя траектория ({@link CelestialBody.Motion}).</li>
 * </ol>
 *
 * <p>Рендер использует immediate-mode API MC 1.21.4:
 * {@code Tesselator.begin(Mode, format)} → {@code addVertex/setUv/setColor} →
 * {@code BufferUploader.drawWithShader(buffer.buildOrThrow())}. Матрица вида
 * приходит параметром {@code modelViewMatrix} (в 1.20.5+ PoseStack из сигнатуры
 * убран). Небо рисуется без записи в буфер глубины, порядок отрисовки задаёт
 * перекрытие (сначала фон, затем тела).
 */
public class SpaceSkyEffects extends DimensionSpecialEffects {

    /** Расстояние до плоскости небесного тела (как ванильные y=100). */
    private static final float SKY_DISTANCE = 100.0F;
    /** Полуразмер фонового куба вокруг камеры. */
    private static final float BG = 16.0F;

    private final float fogFactor;
    private final int backgroundArgb;
    private final List<CelestialBody> bodies;

    /**
     * @param fogFactor      множитель яркости тумана биома (0..1) — чем меньше,
     *                       тем ближе горизонт к цвету фона.
     * @param backgroundArgb сплошной цвет неба (ARGB) этого мира.
     * @param bodies         небесные тела мира в порядке отрисовки.
     */
    public SpaceSkyEffects(float fogFactor, int backgroundArgb, List<CelestialBody> bodies) {
        // cloudLevel=NaN (нет облаков), hasGround=false, SkyType.NONE — ванильное
        // небо не нужно, рисуем сами; constantAmbientLight=true (свет не зависит
        // от времени суток в вакууме).
        super(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, true);
        this.fogFactor = fogFactor;
        this.backgroundArgb = backgroundArgb;
        this.bodies = List.copyOf(bodies);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        return biomeFogColor.scale(fogFactor);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                             Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        RenderSystem.depthMask(false);
        RenderSystem.disableCull(); // купол-куб смотрим изнутри — грани не отбрасываем
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        renderBackground(modelViewMatrix);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (CelestialBody body : bodies) {
            renderBody(body, level, partialTick, modelViewMatrix);
        }
        RenderSystem.disableBlend();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true; // ванильное небо отменяем целиком
    }

    /** Сплошной цвет неба: куб {@link #BG} вокруг камеры (матрица вида без сдвига). */
    private void renderBackground(Matrix4f mv) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float s = BG;
        // 6 граней куба, все — цвет фона.
        quadColor(buf, mv, -s,  s, -s,  s,  s, -s,  s,  s,  s, -s,  s,  s); // top
        quadColor(buf, mv, -s, -s, -s, -s, -s,  s,  s, -s,  s,  s, -s, -s); // bottom
        quadColor(buf, mv, -s, -s, -s,  s, -s, -s,  s,  s, -s, -s,  s, -s); // north
        quadColor(buf, mv,  s, -s,  s, -s, -s,  s, -s,  s,  s,  s,  s,  s); // south
        quadColor(buf, mv, -s, -s,  s, -s, -s, -s, -s,  s, -s, -s,  s,  s); // west
        quadColor(buf, mv,  s, -s, -s,  s, -s,  s,  s,  s,  s,  s,  s, -s); // east
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void quadColor(BufferBuilder buf, Matrix4f m,
                           float x1, float y1, float z1, float x2, float y2, float z2,
                           float x3, float y3, float z3, float x4, float y4, float z4) {
        buf.addVertex(m, x1, y1, z1).setColor(backgroundArgb);
        buf.addVertex(m, x2, y2, z2).setColor(backgroundArgb);
        buf.addVertex(m, x3, y3, z3).setColor(backgroundArgb);
        buf.addVertex(m, x4, y4, z4).setColor(backgroundArgb);
    }

    /** Одно небесное тело: текстурированный квад по траектории {@link CelestialBody.Motion}. */
    private void renderBody(CelestialBody body, ClientLevel level, float partialTick, Matrix4f mv) {
        float xDeg;
        switch (body.motion()) {
            case FIXED -> xDeg = body.phaseDeg();               // неподвижная высота
            case SUN, ORBIT -> {
                double cycleTicks = 24000.0 * Math.max(0.001, body.cycleDays());
                double time = level.getDayTime() + partialTick;
                double frac = (time / cycleTicks) % 1.0;
                xDeg = (float) (frac * 360.0) + body.phaseDeg();
            }
            default -> xDeg = 0.0F;
        }

        // Копируем матрицу вида и накручиваем повороты через JOML (Axis.*
        // .rotationDegrees() отдаёт Quaternionf, Matrix4f.rotate его принимает).
        Matrix4f m = new Matrix4f(mv);
        m.rotate(Axis.YP.rotationDegrees(body.axisYaw()));   // компасное направление / плоскость
        m.rotate(Axis.ZP.rotationDegrees(body.axisTilt()));  // наклон плоскости → касательная траектория
        m.rotate(Axis.XP.rotationDegrees(xDeg));             // ход по орбите

        float sz = body.size();
        int argb = body.argb();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, body.texture());
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.addVertex(m, -sz, SKY_DISTANCE, -sz).setUv(0.0F, 0.0F).setColor(argb);
        buf.addVertex(m,  sz, SKY_DISTANCE, -sz).setUv(1.0F, 0.0F).setColor(argb);
        buf.addVertex(m,  sz, SKY_DISTANCE,  sz).setUv(1.0F, 1.0F).setColor(argb);
        buf.addVertex(m, -sz, SKY_DISTANCE,  sz).setUv(0.0F, 1.0F).setColor(argb);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}

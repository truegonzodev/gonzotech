package com.gonzotech.space.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;

/**
 * Скайбокс космических измерений (Фаза 4, Часть 2 — переработка по фидбэку).
 *
 * <p>Полностью заменяет ванильный рендер неба ({@link #renderSky} возвращает
 * {@code true}) и рисует всё сам, слоями (снизу вверх):
 * <ol>
 *   <li>ГРАДИЕНТ-КУПОЛ вокруг камеры: верхнее полушарие ({@code zenithArgb}) и
 *       нижнее ({@code horizonArgb}) — как ванильное небо, разделённое на верх и
 *       низ. Цвета интерполируются между «дневными» и «ночными» по времени суток
 *       и получают закатный оттенок у горизонта.</li>
 *   <li>Небесные тела ({@link CelestialBody}) — через ванильный
 *       {@link RenderType#celestial} и общий {@link MultiBufferSource.BufferSource},
 *       что гарантирует корректный шейдер/текстуру/блендинг (солнце «светится»
 *       поверх неба, как ванильное). Раньше ручной {@code BufferUploader} путь
 *       падал в GL и тела были невидимы.</li>
 * </ol>
 *
 * <p>КЛЮЧЕВОЙ ФИКС тумана: в MC 1.21.2+ туман — шейдерный юниформ; на время неба
 * ставим {@link FogParameters#NO_FOG}, иначе плотный туман биома «съедает» и
 * купол, и тела.
 *
 * <p>Осадки отменяются ({@link #renderSnowAndRain} возвращает {@code true}) — в
 * вакууме дождя/снега нет.
 */
public class SpaceSkyEffects extends DimensionSpecialEffects {

    /** Расстояние до плоскости небесного тела (как ванильные y=100). */
    private static final float SKY_DISTANCE = 100.0F;
    /** Радиус купола вокруг камеры. */
    private static final float DOME = 16.0F;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final float fogFactor;
    /** Цвет неба в зените (день/ночь) — ARGB. */
    private final int zenithDayArgb;
    private final int zenithNightArgb;
    /** Цвет неба у горизонта (день/ночь) — ARGB. */
    private final int horizonDayArgb;
    private final int horizonNightArgb;
    /** Закатный оттенок у горизонта — ARGB (наложение на восходе/закате). */
    private final int sunsetArgb;
    private final List<CelestialBody> bodies;

    /**
     * @param fogFactor        множитель яркости тумана биома (0..1).
     * @param zenithDayArgb    цвет зенита днём.
     * @param zenithNightArgb  цвет зенита ночью.
     * @param horizonDayArgb   цвет горизонта днём.
     * @param horizonNightArgb цвет горизонта ночью.
     * @param sunsetArgb       закатный оттенок у горизонта (ARGB с альфой).
     * @param bodies           небесные тела мира в порядке отрисовки.
     */
    public SpaceSkyEffects(float fogFactor,
                           int zenithDayArgb, int zenithNightArgb,
                           int horizonDayArgb, int horizonNightArgb,
                           int sunsetArgb,
                           List<CelestialBody> bodies) {
        // cloudLevel=NaN (нет облаков), hasGround=false, constantAmbientLight=true.
        //
        // КРИТИЧНО (подтверждено: renderSky НИ РАЗУ не логировался): в 1.21.4
        // ванильный addSkyPass создаёт проход неба (внутри которого NeoForge
        // вызывает наш renderSky) ТОЛЬКО если skyType != NONE. С NONE проход не
        // создаётся → renderSky не вызывается, виден лишь туман. Нужен NORMAL.
        // Имя константы NORMAL в маппингах Parchment ломало сборку, поэтому
        // выбираем «нормальный» тип неба через values() без ссылки на имя.
        super(Float.NaN, false, normalSkyType(), false, true);
        this.fogFactor = fogFactor;
        this.zenithDayArgb = zenithDayArgb;
        this.zenithNightArgb = zenithNightArgb;
        this.horizonDayArgb = horizonDayArgb;
        this.horizonNightArgb = horizonNightArgb;
        this.sunsetArgb = sunsetArgb;
        this.bodies = List.copyOf(bodies);
    }

    /**
     * Возвращает «нормальный» тип неба (эквивалент {@code SkyType.NORMAL}), но БЕЗ
     * ссылки на имя константы, которое в маппингах Parchment 1.21.4 ломало сборку.
     *
     * <p>Порядок объявления enum фиксирован: {@code NONE(0), NORMAL(1), END(2)} —
     * поэтому {@code values()[1]} гарантированно даёт NORMAL при любых маппингах
     * имён. Это единственный тип, при котором ванильный {@code addSkyPass}
     * создаёт проход неба и, как следствие, вызывает наш {@link #renderSky}.
     */
    private static DimensionSpecialEffects.SkyType normalSkyType() {
        DimensionSpecialEffects.SkyType[] all = DimensionSpecialEffects.SkyType.values();
        // Ищем константу по имени (устойчиво к смене порядка), c запасным
        // вариантом values()[1], если имя не совпало (напр. чужой маппинг).
        for (DimensionSpecialEffects.SkyType t : all) {
            if ("NORMAL".equals(t.name())) {
                return t;
            }
        }
        return all.length > 1 ? all[1] : all[0];
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        return biomeFogColor.scale(fogFactor);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    /** В вакууме нет дождя/снега — полностью отменяем ванильные осадки. */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                                     double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true; // не тикаем дождь — осадков нет
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                             Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, Runnable setupFog) {
        // На время рендера неба выключаем шейдерный туман (иначе он всё перекроет).
        FogParameters savedFog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(FogParameters.NO_FOG);

        // Насколько сейчас «день» (0=ночь, 1=день) и «закат» (пик у горизонта на
        // рассвете/закате). getTimeOfDay: 0=полдень... используем getSunAngle-эквивалент
        // через дневной коэффициент яркости неба.
        float dayFrac = daylightFactor(level, partialTick);
        float sunsetFrac = sunsetFactor(dayFrac);

        int zenith = lerpArgb(zenithNightArgb, zenithDayArgb, dayFrac);
        int horizon = lerpArgb(horizonNightArgb, horizonDayArgb, dayFrac);
        // Закатный оттенок подмешиваем к горизонту.
        horizon = overlayArgb(horizon, sunsetArgb, sunsetFrac);

        if (!logged) {
            logged = true;
            LOGGER.info("[Gonzo Tech] SpaceSkyEffects.renderSky ВЫЗВАН (тела={}, day={}), небо подменяется",
                bodies.size(), dayFrac);
        }

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // ДЕТЕРМИНИРОВАННЫЙ КОНТРОЛЬ МАТРИЦЫ (устраняет неоднозначность двойной
        // трансформации): в 1.21.4 core-шейдеры берут модельвью из
        // RenderSystem.getModelViewStack() на момент отрисовки. Мы ВРЕМЕННО
        // выставляем его в ЕДИНИЦУ, а поворот камеры (modelViewMatrix, переданный
        // в renderSky) «запекаем» в вершины сами. Тогда трансформация применяется
        // РОВНО ОДИН РАЗ, независимо от того, лежала ли уже матрица вида на стеке.
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();

        renderDome(modelViewMatrix, zenith, horizon);

        // Небесные тела — ванильный celestial-слой через общий буфер.
        renderBodies(level, partialTick, modelViewMatrix);

        mvStack.popMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        RenderSystem.setShaderFog(savedFog);
        return true;
    }

    /** Одноразовый флаг для диагностического лога (подтверждение вызова). */
    private boolean logged = false;

    /**
     * Дневной коэффициент 0..1 из угла солнца. {@code level.getTimeOfDay} даёт
     * фазу суток; {@link Mth#cos} угла солнца → яркость. Совпадает с логикой
     * ванильного неба (полдень≈1, полночь≈0).
     */
    private float daylightFactor(ClientLevel level, float partialTick) {
        float angle = level.getTimeOfDay(partialTick);              // 0..1
        float cos = Mth.cos(angle * ((float) Math.PI * 2.0F));      // 1 в полдень, -1 в полночь
        return Mth.clamp((cos + 0.35F) / 1.35F, 0.0F, 1.0F);
    }

    /** Закатный пик: максимум когда день≈0.5 (переход), 0 в полдень/полночь. */
    private float sunsetFactor(float dayFrac) {
        // Треугольник с пиком на dayFrac=0.5.
        return Mth.clamp(1.0F - Math.abs(dayFrac - 0.5F) * 2.0F, 0.0F, 1.0F);
    }

    /**
     * Градиент-купол вокруг камеры: верхнее полушарие → {@code zenith}, нижнее →
     * {@code horizon}, кольцо на уровне глаз (y=0) — {@code horizon}. Даёт видимое
     * разделение неба на «верх» и «низ».
     */
    private void renderDome(Matrix4f mv, int zenith, int horizon) {
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float s = DOME;
        int nadir = scaleArgb(horizon, 0.6F); // низ темнее горизонта

        // Куб вокруг камеры; горизонт-кольцо на уровне глаз (y=0). Каждая боковая
        // грань = 2 квада: верхний (y 0..s) градиент горизонт→зенит, нижний
        // (y -s..0) градиент надир→горизонт. Смотрим изнутри (cull выключен).

        // --- North (z=-s) ---
        band(buf, mv, -s, 0, -s,  s, 0, -s,  s, s, -s, -s, s, -s, horizon, horizon, zenith, zenith);
        band(buf, mv, -s, -s, -s,  s, -s, -s,  s, 0, -s, -s, 0, -s, nadir, nadir, horizon, horizon);
        // --- South (z=+s) ---
        band(buf, mv,  s, 0,  s, -s, 0,  s, -s, s,  s,  s, s,  s, horizon, horizon, zenith, zenith);
        band(buf, mv,  s, -s,  s, -s, -s,  s, -s, 0,  s,  s, 0,  s, nadir, nadir, horizon, horizon);
        // --- East (x=+s) ---
        band(buf, mv,  s, 0, -s,  s, 0,  s,  s, s,  s,  s, s, -s, horizon, horizon, zenith, zenith);
        band(buf, mv,  s, -s, -s,  s, -s,  s,  s, 0,  s,  s, 0, -s, nadir, nadir, horizon, horizon);
        // --- West (x=-s) ---
        band(buf, mv, -s, 0,  s, -s, 0, -s, -s, s, -s, -s, s,  s, horizon, horizon, zenith, zenith);
        band(buf, mv, -s, -s,  s, -s, -s, -s, -s, 0, -s, -s, 0,  s, nadir, nadir, horizon, horizon);

        // Крышка зенита и дно надира.
        quad(buf, mv, -s, s, -s,  s, s, -s,  s, s,  s, -s, s,  s, zenith);
        quad(buf, mv, -s, -s, -s, -s, -s,  s,  s, -s,  s,  s, -s, -s, nadir);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    /** Один квад одного цвета. */
    private void quad(BufferBuilder buf, Matrix4f m,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4, int argb) {
        buf.addVertex(m, x1, y1, z1).setColor(argb);
        buf.addVertex(m, x2, y2, z2).setColor(argb);
        buf.addVertex(m, x3, y3, z3).setColor(argb);
        buf.addVertex(m, x4, y4, z4).setColor(argb);
    }

    /** Квад с индивидуальным цветом на каждую вершину (для градиента). */
    private void band(BufferBuilder buf, Matrix4f m,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4,
                      int c1, int c2, int c3, int c4) {
        buf.addVertex(m, x1, y1, z1).setColor(c1);
        buf.addVertex(m, x2, y2, z2).setColor(c2);
        buf.addVertex(m, x3, y3, z3).setColor(c3);
        buf.addVertex(m, x4, y4, z4).setColor(c4);
    }

    /**
     * Все небесные тела: каждое — текстурированный квад, рисуемый ТЕМ ЖЕ путём,
     * что и купол ({@link BufferUploader#drawWithShader} + {@link CoreShaders#POSITION_TEX}).
     * Раньше тела шли через {@code RenderType.celestial}+bufferSource и НЕ рисовались;
     * теперь используем проверенный ручной путь (купол-то виден).
     */
    private void renderBodies(ClientLevel level, float partialTick, Matrix4f mv) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(CoreShaders.POSITION_TEX);
        for (CelestialBody body : bodies) {
            renderBody(body, level, partialTick, mv);
        }
        RenderSystem.defaultBlendFunc();
    }

    private void renderBody(CelestialBody body, ClientLevel level, float partialTick,
                            Matrix4f mv) {
        float xDeg;
        switch (body.motion()) {
            case FIXED -> xDeg = body.phaseDeg();
            case SUN, ORBIT -> {
                double cycleTicks = 24000.0 * Math.max(0.001, body.cycleDays());
                double time = level.getDayTime() + partialTick;
                double frac = (time / cycleTicks) % 1.0;
                xDeg = (float) (frac * 360.0) + body.phaseDeg();
            }
            default -> xDeg = 0.0F;
        }

        // Запекаем матрицу вида камеры (mv) + локальные орбитальные повороты.
        Matrix4f m = new Matrix4f(mv);
        m.rotate(Axis.YP.rotationDegrees(body.axisYaw()));
        m.rotate(Axis.ZP.rotationDegrees(body.axisTilt()));
        m.rotate(Axis.XP.rotationDegrees(xDeg));

        // Блендинг: солнца — аддитивно (светятся), планеты — обычная альфа.
        if (body.blend() == CelestialBody.Blend.ADDITIVE) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        int a = (body.argb() >>> 24) & 0xFF;
        int r = (body.argb() >>> 16) & 0xFF;
        int g = (body.argb() >>> 8) & 0xFF;
        int b = body.argb() & 0xFF;
        RenderSystem.setShaderColor(r / 255F, g / 255F, b / 255F, a / 255F);
        RenderSystem.setShaderTexture(0, body.texture());

        float sz = body.size();
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(m, -sz, SKY_DISTANCE, -sz).setUv(0.0F, 0.0F);
        buf.addVertex(m,  sz, SKY_DISTANCE, -sz).setUv(1.0F, 0.0F);
        buf.addVertex(m,  sz, SKY_DISTANCE,  sz).setUv(1.0F, 1.0F);
        buf.addVertex(m, -sz, SKY_DISTANCE,  sz).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ---- ARGB утилиты ----

    private static int lerpArgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Наложить {@code over} (с его альфой × {@code amount}) поверх {@code base}. */
    private static int overlayArgb(int base, int over, float amount) {
        float a = ((over >>> 24) & 0xFF) / 255.0F * Mth.clamp(amount, 0f, 1f);
        int br = (base >>> 16) & 0xFF, bg = (base >>> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >>> 16) & 0xFF, og = (over >>> 8) & 0xFF, ob = over & 0xFF;
        int rr = (int) (br + (or - br) * a);
        int rg = (int) (bg + (og - bg) * a);
        int rb = (int) (bb + (ob - bb) * a);
        return (base & 0xFF000000) | (rr << 16) | (rg << 8) | rb;
    }

    private static int scaleArgb(int c, float f) {
        int a = (c >>> 24) & 0xFF;
        int r = (int) (((c >>> 16) & 0xFF) * f);
        int g = (int) (((c >>> 8) & 0xFF) * f);
        int b = (int) ((c & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

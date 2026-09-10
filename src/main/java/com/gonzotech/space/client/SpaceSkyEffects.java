package com.gonzotech.space.client;

import com.gonzotech.space.SunState;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
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
 */
public class SpaceSkyEffects extends DimensionSpecialEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Радиус купола неба и дистанция отрисовки небесных тел в блоках.
     *
     * <p>Ванильный скайбокс рисуется на 100 блоках от камеры. Слишком большое
     * значение (напр. 512) обрезается дальней плоскостью отсечения (far plane
     * проекции = renderDistance * 16), из-за чего при дистанции прорисовки 8–12
     * чанков (128–192 блока) небо полностью пропадало. 100 блоков гарантированно
     * попадает в frustum при любых настройках дальности (даже 2 чанка = 64/96).
     */
    private static final float SKY_DISTANCE = 100.0F;

    /** Цвета зенита: дневной и ночной (0xAARRGGBB). */
    private final int zenithDayArgb;
    private final int zenithNightArgb;

    /** Цвета горизонта: дневной и ночной (0xAARRGGBB). */
    private final int horizonDayArgb;
    private final int horizonNightArgb;

    /**
     * Цвет заката/рассвета у горизонта (0xAARRGGBB). Накладывается на горизонт в
     * сумеречное время. Если прозрачный (alpha=0), закат не окрашивает небо.
     */
    private final int sunsetArgb;

    /** Небесные тела мира: солнца, луны, планеты со своими траекториями. */
    private final List<CelestialBody> bodies;

    /**
     * Главное светило мира (первое с типом SUN), определяющее суточный цикл
     * освещения и цвета купола. Если тел типа SUN нет — берётся ванильный цикл.
     */
    private final CelestialBody primarySun;

    /**
     * Множитель дневного освещения (0..1), применяемый к «дневному» добавочному
     * свету лайтмапа.
     *
     * <p>0.30 для Луны (день темнее на 70%), 0.12 для Европы (день темнее на
     * 88%), 1.0 для Марса (ванильное освещение не трогаем). Ночной пол (0.2)
     * остаётся неизменным.
     */
    private final float daylightScale;

    /**
     * Яркость звёзд ночью (0..1). 0.60 на Марсе, 0.80 на Луне, 0.88 на Европе,
     * 0.90 в открытом космосе.
     */
    private final float starNightBrightness;

    /**
     * Яркость звёзд днём (0..1). 0.0 на Марсе (атмосфера засвечивает днём), 0.70
     * на Луне (вакуум), 0.85 на Европе, 0.90 в открытом космосе.
     */
    private final float starDayBrightness;

    /**
     * Фиксированный дневной коэффициент (0..1) для пустых орбит (нет смены дня/ночи).
     * {@code -1.0} означает обычный динамический цикл по положению солнца.
     */
    private final float fixedDaylight;

    /** Множитель плотности тумана для мира. */
    private final float fogFactor;

    /**
     * @param fogFactor            множитель плотности тумана (0 = кристально чисто).
     * @param zenithDayArgb        зенит день (0xAARRGGBB).
     * @param zenithNightArgb      зенит ночь (0xAARRGGBB).
     * @param horizonDayArgb       горизонт день (0xAARRGGBB).
     * @param horizonNightArgb     горизонт ночь (0xAARRGGBB).
     * @param sunsetArgb           оттенок заката/рассвета на горизонте.
     * @param bodies               список небесных тел.
     * @param daylightScale        множитель дневной яркости (0..1); {@code 1.0} —
     *                             не трогать освещение (ванильное поведение).
     * @param starNightBrightness  яркость звёзд ночью (0..1).
     * @param starDayBrightness    яркость звёзд днём (0..1); {@code 0} — днём не видны.
     */
    public SpaceSkyEffects(float fogFactor,
                           int zenithDayArgb, int zenithNightArgb,
                           int horizonDayArgb, int horizonNightArgb,
                           int sunsetArgb,
                           List<CelestialBody> bodies,
                           float daylightScale,
                           float starNightBrightness,
                           float starDayBrightness) {
        this(fogFactor, zenithDayArgb, zenithNightArgb, horizonDayArgb,
            horizonNightArgb, sunsetArgb, bodies, daylightScale,
            starNightBrightness, starDayBrightness, -1.0F);
    }

    /**
     * @param fixedDaylight фиксированный дневной коэффициент (0..1) — нет смены
     *                      дня/ночи (пустые орбиты); {@code -1} = обычный цикл.
     */
    public SpaceSkyEffects(float fogFactor,
                           int zenithDayArgb, int zenithNightArgb,
                           int horizonDayArgb, int horizonNightArgb,
                           int sunsetArgb,
                           List<CelestialBody> bodies,
                           float daylightScale,
                           float starNightBrightness,
                           float starDayBrightness,
                           float fixedDaylight) {
        super(Float.NaN, false, normalSkyType(), false, true);
        this.fogFactor = fogFactor;
        this.zenithDayArgb = zenithDayArgb;
        this.zenithNightArgb = zenithNightArgb;
        this.horizonDayArgb = horizonDayArgb;
        this.horizonNightArgb = horizonNightArgb;
        this.sunsetArgb = sunsetArgb;
        this.daylightScale = Mth.clamp(daylightScale, 0.0F, 1.0F);
        this.starNightBrightness = Mth.clamp(starNightBrightness, 0.0F, 1.0F);
        this.starDayBrightness = Mth.clamp(starDayBrightness, 0.0F, 1.0F);
        this.fixedDaylight = fixedDaylight;
        this.bodies = List.copyOf(bodies);
        CelestialBody sun = null;
        for (CelestialBody b : this.bodies) {
            if (b.motion() == CelestialBody.Motion.SUN) {
                sun = b;
                break;
            }
        }
        this.primarySun = sun;
    }

    private static DimensionSpecialEffects.SkyType normalSkyType() {
        DimensionSpecialEffects.SkyType[] all = DimensionSpecialEffects.SkyType.values();
        for (DimensionSpecialEffects.SkyType t : all) {
            if ("NORMAL".equals(t.name())) {
                return t;
            }
        }
        return all.length > 1 ? all[1] : all[0];
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        int horizon = lerpArgb(horizonNightArgb, horizonDayArgb, Mth.clamp(daylight, 0f, 1f));
        double r = ((horizon >>> 16) & 0xFF) / 255.0;
        double g = ((horizon >>> 8) & 0xFF) / 255.0;
        double b = (horizon & 0xFF) / 255.0;
        return new Vec3(r, g, b);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                                     double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                             Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, Runnable setupFog) {
        FogType fog = camera.getFluidInCamera();
        if (fog != FogType.NONE) {
            return true;
        }

        FogParameters savedFog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(FogParameters.NO_FOG);

        float dayFrac = daylightFactor(level, partialTick);
        float sunsetFrac = sunsetFactor(dayFrac);

        int zenith = lerpArgb(zenithNightArgb, zenithDayArgb, dayFrac);
        int horizon = lerpArgb(horizonNightArgb, horizonDayArgb, dayFrac);
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
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();

        renderDome(modelViewMatrix, zenith, horizon);

        float starBrightness = Mth.lerp(dayFrac, starNightBrightness, starDayBrightness);
        if (starBrightness > 0.001F) {
            renderStars(modelViewMatrix, starBrightness);
        }

        renderBodies(level, partialTick, modelViewMatrix);

        mvStack.popMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        RenderSystem.setShaderFog(savedFog);
        return true;
    }

    private boolean logged = false;

    private float daylightFactor(ClientLevel level, float partialTick) {
        if (SpaceSkyState.sunState == SunState.GONE) {
            return 0.0F;
        }
        if (fixedDaylight >= 0.0F) {
            return Mth.clamp(fixedDaylight, 0.0F, 1.0F);
        }
        if (primarySun == null) {
            float angle = level.getTimeOfDay(partialTick);
            float cos = Mth.cos(angle * ((float) Math.PI * 2.0F));
            return Mth.clamp((cos + 0.35F) / 1.35F, 0.0F, 1.0F);
        }
        double cycleTicks = 24000.0 * Math.max(0.001, primarySun.cycleDays());
        double time = level.getDayTime() + partialTick;
        double frac = Mth.frac((float) ((time - 6000.0) / cycleTicks));
        float cos = Mth.cos((float) (frac * 2.0 * Math.PI));
        return Mth.clamp((cos + 0.35F) / 1.35F, 0.0F, 1.0F);
    }

    public boolean overridesSkyLight() {
        if (SpaceSkyState.sunState == SunState.GONE) {
            return true;
        }
        return daylightScale < 0.999F || fixedDaylight >= 0.0F;
    }

    public float computeSkyDarken(ClientLevel level, float partialTick) {
        if (SpaceSkyState.sunState == SunState.GONE) {
            return 0.2F;
        }
        if (fixedDaylight >= 0.0F) {
            return Mth.clamp(0.2F + 0.8F * fixedDaylight, 0.0F, 1.0F);
        }
        float dayFrac = daylightFactor(level, partialTick);
        return 0.2F + 0.8F * dayFrac * daylightScale;
    }

    private float[] computeBodyTint(float dayFrac) {
        float redFactor;
        if (dayFrac >= 0.60F) {
            redFactor = 0.0F;
        } else if (dayFrac >= 0.30F) {
            redFactor = (0.60F - dayFrac) / 0.30F;
        } else {
            redFactor = dayFrac / 0.30F;
        }

        float blueFactor = dayFrac < 0.30F ? (0.30F - dayFrac) / 0.30F : 0.0F;

        float r = 1.0F - blueFactor * 0.10F;
        float g = 1.0F - redFactor * 0.10F - blueFactor * 0.10F;
        float b = 1.0F - redFactor * 0.10F;
        float brightness = 1.0F - blueFactor * 0.05F;

        return new float[] { r * brightness, g * brightness, b * brightness };
    }

    private float sunsetFactor(float dayFrac) {
        if (dayFrac < 0.05F || dayFrac > 0.55F) {
            return 0.0F;
        }
        if (dayFrac <= 0.30F) {
            return (dayFrac - 0.05F) / 0.25F;
        } else {
            return (0.55F - dayFrac) / 0.25F;
        }
    }

    private void renderDome(Matrix4f m, int zenith, int horizon) {
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int zR = (zenith >>> 16) & 0xFF, zG = (zenith >>> 8) & 0xFF, zB = zenith & 0xFF;
        int hR = (horizon >>> 16) & 0xFF, hG = (horizon >>> 8) & 0xFF, hB = horizon & 0xFF;

        int segments = 24;
        float step = (float) (Math.PI * 2.0 / segments);

        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = (i + 1) * step;
            float cos0 = Mth.cos(a0), sin0 = Mth.sin(a0);
            float cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);

            buf.addVertex(m, 0.0F, SKY_DISTANCE, 0.0F).setColor(zR, zG, zB, 255);
            buf.addVertex(m, 0.0F, SKY_DISTANCE, 0.0F).setColor(zR, zG, zB, 255);
            buf.addVertex(m, cos1 * SKY_DISTANCE, 0.0F, sin1 * SKY_DISTANCE).setColor(hR, hG, hB, 255);
            buf.addVertex(m, cos0 * SKY_DISTANCE, 0.0F, sin0 * SKY_DISTANCE).setColor(hR, hG, hB, 255);

            buf.addVertex(m, cos0 * SKY_DISTANCE, 0.0F, sin0 * SKY_DISTANCE).setColor(hR, hG, hB, 255);
            buf.addVertex(m, cos1 * SKY_DISTANCE, 0.0F, sin1 * SKY_DISTANCE).setColor(hR, hG, hB, 255);
            buf.addVertex(m, 0.0F, -SKY_DISTANCE, 0.0F).setColor(zR, zG, zB, 255);
            buf.addVertex(m, 0.0F, -SKY_DISTANCE, 0.0F).setColor(zR, zG, zB, 255);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private static float[] starPositions = null;

    private static void ensureStarsBuilt() {
        if (starPositions != null) {
            return;
        }
        int count = 1500;
        starPositions = new float[count * 3];
        RandomSource rand = RandomSource.create(10842L);
        for (int i = 0; i < count; i++) {
            float x = rand.nextFloat() * 2.0F - 1.0F;
            float y = rand.nextFloat() * 2.0F - 1.0F;
            float z = rand.nextFloat() * 2.0F - 1.0F;
            float lenSq = x * x + y * y + z * z;
            if (lenSq > 0.01F && lenSq <= 1.0F) {
                float inv = (SKY_DISTANCE - 2.0F) / Mth.sqrt(lenSq);
                starPositions[i * 3]     = x * inv;
                starPositions[i * 3 + 1] = y * inv;
                starPositions[i * 3 + 2] = z * inv;
            } else {
                i--;
            }
        }
    }

    private void renderStars(Matrix4f m, float brightness) {
        ensureStarsBuilt();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int a = (int) (Mth.clamp(brightness, 0.0F, 1.0F) * 255.0F);
        if (a <= 0) return;

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float starSize = 0.35F;
        int count = starPositions.length / 3;
        for (int i = 0; i < count; i++) {
            float x = starPositions[i * 3];
            float y = starPositions[i * 3 + 1];
            float z = starPositions[i * 3 + 2];

            buf.addVertex(m, x - starSize, y - starSize, z).setColor(255, 255, 255, a);
            buf.addVertex(m, x + starSize, y - starSize, z).setColor(255, 255, 255, a);
            buf.addVertex(m, x + starSize, y + starSize, z).setColor(255, 255, 255, a);
            buf.addVertex(m, x - starSize, y + starSize, z).setColor(255, 255, 255, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void renderBodies(ClientLevel level, float partialTick, Matrix4f baseMatrix) {
        float dayFrac = daylightFactor(level, partialTick);
        float[] tint = computeBodyTint(dayFrac);

        for (CelestialBody body : bodies) {
            renderBody(level, partialTick, baseMatrix, body, tint);
        }
    }

    private void renderBody(ClientLevel level, float partialTick, Matrix4f baseMatrix,
                            CelestialBody body, float[] tint) {
        Matrix4f m = new Matrix4f(baseMatrix);

        float posAngle;
        if (body.motion() == CelestialBody.Motion.FIXED) {
            posAngle = body.phaseDeg();
        } else if (body.motion() == CelestialBody.Motion.HORIZON_ORBIT) {
            double cycleTicks = 24000.0 * Math.max(0.001, body.cycleDays());
            double time = level.getDayTime() + partialTick;
            float frac = Mth.frac((float) (time / cycleTicks));
            posAngle = body.phaseDeg() + frac * 360.0F;
        } else {
            double cycleTicks = 24000.0 * Math.max(0.001, body.cycleDays());
            double time = level.getDayTime() + partialTick;
            float frac = Mth.frac((float) ((time - 6000.0) / cycleTicks));
            posAngle = body.phaseDeg() + frac * 360.0F;
        }

        if (body.motion() == CelestialBody.Motion.HORIZON_ORBIT) {
            m.rotate(Axis.YP.rotationDegrees(posAngle));
            m.rotate(Axis.XP.rotationDegrees(90.0F - body.axisTilt()));
        } else {
            m.rotate(Axis.YP.rotationDegrees(body.axisYaw()));
            m.rotate(Axis.XP.rotationDegrees(body.axisTilt()));
            m.rotate(Axis.ZP.rotationDegrees(posAngle));
        }

        if (body.blend() == CelestialBody.Blend.ADDITIVE) {
            RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.setShader(CoreShaders.POSITION_TEX);

        int a = (body.argb() >>> 24) & 0xFF;
        int r = (body.argb() >>> 16) & 0xFF;
        int g = (body.argb() >>> 8) & 0xFF;
        int b = body.argb() & 0xFF;
        RenderSystem.setShaderColor(
            r / 255F * tint[0], g / 255F * tint[1], b / 255F * tint[2], a / 255F);

        ResourceLocation tex = resolveTexture(body.texture());

        RenderSystem.setShaderTexture(0, tex);

        float[] v = animationV(tex);
        float v0 = v[0], v1 = v[1];

        float sz = body.size();
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(m, -sz, SKY_DISTANCE, -sz).setUv(0.0F, v0);
        buf.addVertex(m,  sz, SKY_DISTANCE, -sz).setUv(1.0F, v0);
        buf.addVertex(m,  sz, SKY_DISTANCE,  sz).setUv(1.0F, v1);
        buf.addVertex(m, -sz, SKY_DISTANCE,  sz).setUv(0.0F, v1);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static final java.util.Map<ResourceLocation, int[]> ANIM_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<String, ResourceLocation> VARIANT_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private ResourceLocation resolveTexture(ResourceLocation base) {
        String path = base.getPath();
        if (!path.endsWith(".png")) {
            return base;
        }

        if (path.contains("alpha_centauri")) {
            boolean isAlphaDyson = SpaceSkyState.alphaCentauriDyson || SpaceSkyState.dysonSphere;
            if (isAlphaDyson) {
                return resolveVariant(base, "_dyson");
            }
            return base;
        }

        if (path.endsWith("/sun.png") || path.equals("textures/environment/sun.png")) {
            SunState state = SpaceSkyState.sunState;
            if (state == SunState.DEFAULT && (SpaceSkyState.sunDyson || SpaceSkyState.dysonSphere)) {
                state = SunState.DYSON;
            }

            switch (state) {
                case DYSON -> {
                    return resolveVariant(base, "_dyson");
                }
                case GONE -> {
                    return resolveVariant(base, "_gone");
                }
                case BLACKHOLE -> {
                    return resolveVariant(base, "_blackhole");
                }
                case BLACKHOLE_DYSON -> {
                    return resolveVariant(base, "_blackhole_dyson");
                }
                case DEFAULT -> {
                    return base;
                }
            }
        }

        if (SpaceSkyState.dysonSphere) {
            return resolveVariant(base, "_dyson");
        }

        return base;
    }

    private ResourceLocation resolveVariant(ResourceLocation base, String suffix) {
        String cacheKey = base.toString() + suffix;
        return VARIANT_CACHE.computeIfAbsent(cacheKey, k -> {
            String p = base.getPath();
            String variantPath = p.substring(0, p.length() - 4) + suffix + ".png";
            ResourceLocation variant = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), variantPath);
            try {
                if (Minecraft.getInstance().getResourceManager().getResource(variant).isPresent()) {
                    return variant;
                }
            } catch (Exception ignored) {
                // fallthrough
            }
            return base;
        });
    }

    private float[] animationV(ResourceLocation tex) {
        int[] meta = ANIM_CACHE.computeIfAbsent(tex, this::readAnimation);
        int frames = meta[0];
        int frametime = meta[1];
        if (frames <= 1) {
            return new float[] { 0.0F, 1.0F };
        }
        long ms = System.currentTimeMillis();
        long frameDurationMs = (long) frametime * 50L;
        if (frameDurationMs <= 0) frameDurationMs = 50L;
        long totalLoopMs = (long) frames * frameDurationMs;
        int currentFrame = (int) ((ms % totalLoopMs) / frameDurationMs);
        currentFrame = Mth.clamp(currentFrame, 0, frames - 1);
        float vHeight = 1.0F / (float) frames;
        float v0 = currentFrame * vHeight;
        float v1 = v0 + vHeight;
        return new float[] { v0, v1 };
    }

    private int[] readAnimation(ResourceLocation tex) {
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            ResourceLocation metaLoc = ResourceLocation.fromNamespaceAndPath(
                tex.getNamespace(), tex.getPath() + ".mcmeta");
            var metaRes = rm.getResource(metaLoc);
            if (metaRes.isEmpty()) {
                return new int[] { 1, 1 };
            }
            String json;
            try (var in = metaRes.get().open()) {
                json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (!json.contains("animation")) {
                return new int[] { 1, 1 };
            }
            int frametime = 1;
            var fm = java.util.regex.Pattern.compile("\"frametime\"\\s*:\\s*(\\d+)").matcher(json);
            if (fm.find()) {
                frametime = Math.max(1, Integer.parseInt(fm.group(1)));
            }
            int frames = 1;
            var texRes = rm.getResource(tex);
            if (texRes.isPresent()) {
                try (var in = texRes.get().open()) {
                    com.mojang.blaze3d.platform.NativeImage img =
                        com.mojang.blaze3d.platform.NativeImage.read(in);
                    int w = img.getWidth();
                    int hgt = img.getHeight();
                    img.close();
                    if (w > 0 && hgt > w && (hgt % w == 0)) {
                        frames = hgt / w;
                    }
                }
            }
            return new int[] { Math.max(1, frames), frametime };
        } catch (Exception e) {
            return new int[] { 1, 1 };
        }
    }

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

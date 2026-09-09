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
 *
 * <p>Осадки отменяются ({@link #renderSnowAndRain} возвращает {@code true}) — в
 * вакууме дождя/снега нет.
 */
public class SpaceSkyEffects extends DimensionSpecialEffects {

    /** Расстояние до плоскости небесного тела (как ванильные y=100). */
    private static final float SKY_DISTANCE = 100.0F;
    /** Радиус купола вокруг камеры. */
    private static final float DOME = 16.0F;

    /** Кол-во звёзд (как в ванильном небе). */
    private static final int STAR_COUNT = 1500;
    /** Сид генерации звёзд — фиксирован, чтобы поле было стабильным между кадрами. */
    private static final long STAR_SEED = 10842L;
    /**
     * Предрассчитанные вершины звёздных билбордов: {@code STAR_COUNT × 4 угла × 3
     * координаты}. Считаются один раз (лениво) тем же алгоритмом, что и ванильные
     * звёзды, и переиспользуются каждый кадр (перекрашиваются под текущую яркость).
     */
    private static float[] starCorners;

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
    /**
     * Множитель ДНЕВНОЙ яркости мира (не неба!) — 0..1. Развязывает фактическое
     * освещение блоков от ванильного времени и привязывает его к ПОЛОЖЕНИЮ
     * СОЛНЦА (той же фазе {@code cycleDays}, что и диск на небе), после чего
     * домножает «дневную добавку» к свету на это число:
     * <ul>
     *   <li>Луна {@code 0.30} — день −70% (сумрачно даже в зените);</li>
     *   <li>Европа {@code 0.12} — день −88% (почти всегда полумрак);</li>
     *   <li>Марс {@code 1.0} — НЕ трогаем (см. {@link #overridesSkyLight()}).</li>
     * </ul>
     * Ночной «пол» яркости не меняется (как в оверворлде). Благодаря привязке к
     * положению солнца Луна получает длинный цикл освещения (~30 суток свет /
     * ~30 тьма), а не обычные сутки.
     */
    private final float daylightScale;
    /**
     * Яркость звёзд НОЧЬЮ и ДНЁМ (0..1). Между ними интерполируется по
     * {@link #daylightFactor} (0=ночь, 1=день):
     * <ul>
     *   <li>Луна/Европа — {@code night=1.0}, {@code day≈0.8} (днём −20% как в
     *       оверворлде: звёзды видны всегда, чуть тусклее);</li>
     *   <li>Марс — {@code night≈0.3}, {@code day=0.0} (звёзды только ночью и
     *       своей пониженной яркостью — атмосфера засвечивает).</li>
     * </ul>
     */
    private final float starNightBrightness;
    private final float starDayBrightness;
    /**
     * ФИКСИРОВАННЫЙ дневной коэффициент (0..1) или {@code -1} = обычный цикл.
     * Для пустых орбит нет смены дня/ночи: освещение «заморожено» на закатном
     * уровне (~0.4). При {@code >=0} {@link #daylightFactor} всегда возвращает
     * это число → небо/звёзды/тон/свет статичны.
     */
    private final float fixedDaylight;
    private final List<CelestialBody> bodies;
    /**
     * Главное солнце мира (первое тело с {@link CelestialBody.Motion#SUN}) — от
     * его положения зависит цвет неба день/ночь. Так «день» длится ровно столько,
     * сколько солнце над горизонтом: Марс {@code cycleDays=1} = как ваниль, Европа
     * {@code =3} = втрое длиннее, Луна {@code =60} = ~30 суток свет / ~30 тьма.
     * null, если у мира нет солнца (падаем на ванильное время).
     */
    private final CelestialBody primarySun;

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
        // Совместимость со старыми вызовами: без затемнения дня (Марс),
        // звёзды ночью в полную силу, днём приглушены на 20%.
        this(fogFactor, zenithDayArgb, zenithNightArgb, horizonDayArgb,
            horizonNightArgb, sunsetArgb, bodies, 1.0F, 1.0F, 0.8F);
    }

    /**
     * @param daylightScale        множитель дневной яркости мира (0..1). {@code 1.0} —
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
        // ФИКС «чёрного тумана»: туман = цвет НЕБА У ГОРИЗОНТА (день/ночь), чтобы
        // дальние/туманные чанки плавно РАСТВОРЯЛИСЬ в небе, а не тонули в чёрной
        // тени. Раньше возвращали затемнённый цвет тумана биома → у горизонта
        // возникала чёрная кайма между землёй и оранжевым/цветным небом.
        // daylight: 0=ночь … 1=день.
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
        // ПОД ВОДОЙ/ЛАВОЙ небо рисовать НЕЛЬЗЯ. Раньше возвращали false → ваниль
        // рисовала СВОЁ небо (голубой скайбокс с солнцем) поверх воды. Теперь
        // возвращаем true (ваниль подавлена) и НИЧЕГО не рисуем: остаётся буфер,
        // залитый цветом водного тумана на шаге clear → под водой видно синюю
        // дымку без солнца, а туман на блоках уже даёт ваниль-шейдер.
        FogType fog = camera.getFluidInCamera();
        if (fog != FogType.NONE) {
            return true;
        }

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

        // ЗВЁЗДЫ — между куполом и телами: тела гарантированно рисуются ПОВЕРХ
        // звёзд (порядок отрисовки = порядок наложения, depthTest выключен).
        float starBrightness = Mth.lerp(dayFrac, starNightBrightness, starDayBrightness);
        if (starBrightness > 0.001F) {
            renderStars(modelViewMatrix, starBrightness);
        }

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
     * Дневной коэффициент 0..1 — из ВЫСОТЫ ГЛАВНОГО СОЛНЦА над горизонтом, а не из
     * ванильного времени. Благодаря этому цвет неба и «день/ночь» идут В ТОМ ЖЕ
     * ТЕМПЕ, что и движение солнца по небосклону:
     * <ul>
     *   <li>Марс ({@code cycleDays=1}) — как ваниль (полдень светло, полночь темно);</li>
     *   <li>Европа ({@code cycleDays=3}) — сутки визуально втрое длиннее;</li>
     *   <li>Луна ({@code cycleDays=60}) — ~30 суток непрерывного света, затем ~30
     *       тьмы (солнце реально ползёт полкруга за 30 суток).</li>
     * </ul>
     * Раньше брали ванильный {@code getTimeOfDay} → небо мигало день/ночь каждые
     * пол-суток, пока солнце едва двигалось. Теперь фаза = та же, что у диска солнца.
     */
    private float daylightFactor(ClientLevel level, float partialTick) {
        // Пустые орбиты: освещение «заморожено» (нет смены дня/ночи).
        if (fixedDaylight >= 0.0F) {
            return Mth.clamp(fixedDaylight, 0.0F, 1.0F);
        }
        if (primarySun == null) {
            float angle = level.getTimeOfDay(partialTick);
            float cos = Mth.cos(angle * ((float) Math.PI * 2.0F));
            return Mth.clamp((cos + 0.35F) / 1.35F, 0.0F, 1.0F);
        }
        // Та же фаза, что и у диска солнца в renderBody: 0=зенит(полдень),
        // 0.5=надир(полночь). Высота над горизонтом = cos(фаза*2π): +1 зенит, −1 надир.
        double cycleTicks = 24000.0 * Math.max(0.001, primarySun.cycleDays());
        double time = level.getDayTime() + partialTick;
        double frac = Mth.frac((float) ((time - 6000.0) / cycleTicks));
        float cos = Mth.cos((float) (frac * 2.0 * Math.PI)); // 1=зенит, -1=надир
        return Mth.clamp((cos + 0.35F) / 1.35F, 0.0F, 1.0F);
    }

    /**
     * Нужно ли этому миру ПОДМЕНЯТЬ фактическое освещение (яркость лайтмапа).
     * {@code true} только когда задан множитель затемнения {@code < 1} —
     * т.е. для Луны/Европы. Марс ({@code daylightScale=1}) остаётся на ванильном
     * освещении и НЕ трогается.
     */
    public boolean overridesSkyLight() {
        // Затемняем свет для Луны/Европы (daylightScale<1) ИЛИ когда освещение
        // «заморожено» на пустых орбитах (fixedDaylight>=0, нет смены дня/ночи).
        return daylightScale < 0.999F || fixedDaylight >= 0.0F;
    }

    /**
     * Значение «яркости неба» для лайтмапа, РАЗВЯЗАННОЕ от ванильного времени и
     * привязанное к ПОЛОЖЕНИЮ СОЛНЦА, плюс домноженное на {@link #daylightScale}.
     *
     * <p>Ванильный {@code ClientLevel.getSkyDarken(float)} возвращает диапазон
     * {@code [0.2, 1.0]}: {@code 0.2} — ночной «пол» яркости, {@code 1.0} —
     * полдень. Мы сохраняем ночной пол {@code 0.2} (пещеры/ночь как в оверворлде),
     * но «дневную добавку» {@code (value − 0.2)} берём из дневного коэффициента
     * {@link #daylightFactor} (0..1 по высоте солнца) и умножаем на
     * {@code daylightScale}:
     * <pre>result = 0.2 + 0.8 · dayFrac · daylightScale</pre>
     * Итог: (1) свет день/ночь идёт в темпе движения солнца — Луна получает
     * длинный цикл (~30 суток свет / ~30 тьма), Европа — цикл 3.5 суток;
     * (2) пик дня затемнён (Луна −70%, Европа −88%), ночь не тронута.
     *
     * <p>Вызывается из клиентского миксина на {@code getSkyDarken(F)F} только
     * когда {@link #overridesSkyLight()} == {@code true}.
     */
    public float computeSkyDarken(ClientLevel level, float partialTick) {
        // Пустые орбиты: свет заморожен на fixedDaylight (напр. 0.4 «закат»),
        // ночной пол не добавляем — уровень строго фиксирован.
        if (fixedDaylight >= 0.0F) {
            return Mth.clamp(0.2F + 0.8F * fixedDaylight, 0.0F, 1.0F);
        }
        float dayFrac = daylightFactor(level, partialTick);
        return 0.2F + 0.8F * dayFrac * daylightScale;
    }

    /**
     * Тон небесных тел по времени суток (мультипликаторы R,G,B для
     * {@code setShaderColor}), плавно по {@link #daylightFactor} (1=полдень,
     * ≈0.26=горизонт/закат-рассвет, 0=полночь):
     * <ul>
     *   <li><b>День</b> (dayFrac ≥ 0.6): тон отсутствует — {@code (1,1,1)}.</li>
     *   <li><b>Закат/рассвет</b> (пик у горизонта): +10% в красный —
     *       гасим G и B на 10% (солнце и тела краснеют).</li>
     *   <li><b>Ночь</b> (dayFrac → 0): +10% в синий — гасим R и G на 10%,
     *       плюс общая яркость −5%.</li>
     * </ul>
     * Переход красный→синий на спуске в ночь и синий→красный на восходе выходит
     * автоматически (redFactor и blueFactor перекрываются в сумеречной полосе).
     *
     * @return массив {@code {rMul, gMul, bMul}} 0..1.
     */
    private float[] computeBodyTint(float dayFrac) {
        // redFactor: треугольник с пиком у горизонта (dayFrac≈0.30), 0 днём и
        // 0 в глубокой ночи. blueFactor: нарастает от 0 (dayFrac=0.30) до 1
        // (dayFrac=0, полночь).
        float redFactor;
        if (dayFrac >= 0.6F || dayFrac <= 0.0F) {
            redFactor = 0.0F;
        } else if (dayFrac >= 0.3F) {
            redFactor = (0.6F - dayFrac) / 0.3F;   // 0.6→0 … 0.3→1
        } else {
            redFactor = dayFrac / 0.3F;            // 0.3→1 … 0→0
        }
        float blueFactor = Mth.clamp((0.3F - dayFrac) / 0.3F, 0.0F, 1.0F);

        float redTint = 0.10F * redFactor;   // насколько «в красный»
        float blueTint = 0.10F * blueFactor; // насколько «в синий»
        float brightness = 1.0F - 0.05F * blueFactor; // ночью −5%

        float rMul = (1.0F - blueTint) * brightness;
        float gMul = (1.0F - redTint) * (1.0F - blueTint) * brightness;
        float bMul = (1.0F - redTint) * brightness;
        return new float[] { rMul, gMul, bMul };
    }

    /** Закатный пик: максимум когда день≈0.5 (переход), 0 в полдень/полночь. */
    private float sunsetFactor(float dayFrac) {
        // Треугольник с пиком на dayFrac=0.5.
        return Mth.clamp(1.0F - Math.abs(dayFrac - 0.5F) * 2.0F, 0.0F, 1.0F);
    }

    /**
     * Лениво считает геометрию звёздного поля тем же алгоритмом, что и ванильное
     * небо ({@code LevelRenderer.drawStars}): {@link #STAR_COUNT} звёзд в случайных
     * точках сферы, каждая — крошечный квад-билборд, развёрнутый к центру и
     * случайно повёрнутый вокруг своей оси. Считается ОДИН раз (детерминированно,
     * общий для всех миров) и кэшируется в {@link #starCorners}.
     */
    private static float[] buildStars() {
        RandomSource random = RandomSource.create(STAR_SEED);
        float[] corners = new float[STAR_COUNT * 4 * 3];
        int idx = 0;
        for (int i = 0; i < STAR_COUNT; i++) {
            // Случайная точка внутри куба [-1,1]^3, отбрасываем далёкие/близкие —
            // получаем равномерную «скорлупу» звёзд.
            double x = random.nextFloat() * 2.0F - 1.0F;
            double y = random.nextFloat() * 2.0F - 1.0F;
            double z = random.nextFloat() * 2.0F - 1.0F;
            double scale = 0.15F + random.nextFloat() * 0.1F; // размер звезды
            double d = x * x + y * y + z * z;
            if (d <= 0.010000000474974513 || d >= 1.0) {
                i--;
                continue;
            }
            // Нормируем на радиус небесной сферы (звёзды дальше тел, ближе купола
            // не важно — depthTest выключен, важен лишь порядок отрисовки).
            double inv = 1.0 / Math.sqrt(d);
            x *= inv;
            y *= inv;
            z *= inv;
            double cx = x * 100.0;
            double cy = y * 100.0;
            double cz = z * 100.0;
            // Углы направления на звезду (для ориентации билборда к центру).
            double aTheta = Math.atan2(x, z);
            double sinT = Math.sin(aTheta), cosT = Math.cos(aTheta);
            double aPhi = Math.atan2(Math.sqrt(x * x + z * z), y);
            double sinP = Math.sin(aPhi), cosP = Math.cos(aPhi);
            double roll = random.nextDouble() * Math.PI * 2.0; // случайный поворот
            double sinR = Math.sin(roll), cosR = Math.cos(roll);
            for (int c = 0; c < 4; c++) {
                double ox = (double) ((c & 2) - 1) * scale;
                double oy = (double) ((c + 1 & 2) - 1) * scale;
                // Поворот угла квада вокруг оси взгляда (roll), затем ориентация
                // билборда к ЦЕНТРУ сферы — ТОЧНО по ванильному LevelRenderer:
                // квад нормалью смотрит на камеру, поэтому не «встаёт на ребро»
                // (иначе звёзды вырождались в линии вдоль оси движения солнца).
                double rox = ox * cosR - oy * sinR;
                double roy = oy * cosR + ox * sinR;
                double yOff = rox * sinP;          // d23 в ванили
                double k = -rox * cosP;            // d24 в ванили
                double xOff = k * sinT - roy * cosT;   // d25
                double zOff = roy * sinT + k * cosT;   // d27
                corners[idx++] = (float) (cx + xOff);
                corners[idx++] = (float) (cy + yOff);
                corners[idx++] = (float) (cz + zOff);
            }
        }
        return corners;
    }

    /**
     * Рисует звёзды поверх купола (под телами). Цвет — белый с альфой
     * {@code brightness}; аддитивный блендинг, чтобы звёзды «светились» на фоне
     * неба, как ванильные ночью.
     */
    private void renderStars(Matrix4f mv, float brightness) {
        if (starCorners == null) {
            starCorners = buildStars();
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(CoreShaders.POSITION);
        RenderSystem.setShaderColor(brightness, brightness, brightness, brightness);
        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < starCorners.length; i += 3) {
            buf.addVertex(mv, starCorners[i], starCorners[i + 1], starCorners[i + 2]);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
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
        // Тон тел по времени суток (день без тона / закат в красный / ночь в
        // синий), считается один раз на кадр и общий для всех тел.
        float[] tint = computeBodyTint(daylightFactor(level, partialTick));
        for (CelestialBody body : bodies) {
            renderBody(body, level, partialTick, mv, tint);
        }
        RenderSystem.defaultBlendFunc();
    }

    private void renderBody(CelestialBody body, ClientLevel level, float partialTick,
                            Matrix4f mv, float[] tint) {
        // Запекаем матрицу вида камеры (mv) + локальные повороты тела.
        Matrix4f m = new Matrix4f(mv);

        if (body.motion() == CelestialBody.Motion.HORIZON_ORBIT) {
            // ГОРИЗОНТАЛЬНЫЙ круг по азимуту (Земля на орбите Солнца): тело
            // обходит горизонт С→З→Ю→В на фиксированной высоте axisTilt.
            // JOML применяет ПОСЛЕДНИЙ rotate к вершине ПЕРВЫМ: сначала опускаем
            // квад из зенита к нужной высоте (X на 90−altitude), затем крутим
            // вокруг вертикали (Y) по времени → тело едет по кольцу горизонта.
            double cycleTicks = 24000.0 * Math.max(0.001, body.cycleDays());
            double time = level.getDayTime() + partialTick;
            double frac = Mth.frac((float) (time / cycleTicks));
            float azimuth = (float) (frac * 360.0) + body.phaseDeg();
            float altitude = body.axisTilt(); // высота над горизонтом, 0 = горизонт
            m.rotate(Axis.YP.rotationDegrees(azimuth));
            m.rotate(Axis.XP.rotationDegrees(90.0F - altitude));
        } else {
            float xDeg;
            switch (body.motion()) {
                case FIXED -> xDeg = body.phaseDeg();
                case SUN, ORBIT -> {
                    // ВЫРАВНИВАНИЕ С ВАНИЛЬНЫМ СВЕТОМ (фикс: раньше при dayTime=0 солнце
                    // оказывалось в ЗЕНИТЕ, хотя это утро/горизонт → диск не совпадал со
                    // светом, ночью торчал вверху). Квад тела до поворота лежит в зените
                    // (y=+SKY_DISTANCE), поворот вокруг X ведёт его по вертикальному
                    // кругу: 0°=зенит, 90°=горизонт(закат), 180°=надир, 270°=горизонт(рассвет).
                    // Ванильный полдень = dayTime 6000. Сдвигаем фазу на −6000, чтобы
                    // зенит приходился на полдень; тогда для cycleDays=1 диск совпадает
                    // со светом ТОЧНО (закат dayTime 12000 → 90°, полночь 18000 → 180°).
                    // Для cycleDays>1 солнце дрейфует медленно (Луна/Европа): один
                    // оборот за cycleDays суток, зенит на «полдень» первого дня.
                    double cycleTicks = 24000.0 * Math.max(0.001, body.cycleDays());
                    double time = level.getDayTime() + partialTick;
                    double frac = Mth.frac((float) ((time - 6000.0) / cycleTicks));
                    xDeg = (float) (frac * 360.0) + body.phaseDeg();
                }
                default -> xDeg = 0.0F;
            }
            m.rotate(Axis.YP.rotationDegrees(body.axisYaw()));
            m.rotate(Axis.ZP.rotationDegrees(body.axisTilt()));
            m.rotate(Axis.XP.rotationDegrees(xDeg));
        }

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
        // База тела × тон времени суток (tint[]): днём (1,1,1) — как раньше.
        RenderSystem.setShaderColor(
            r / 255F * tint[0], g / 255F * tint[1], b / 255F * tint[2], a / 255F);
        RenderSystem.setShaderTexture(0, body.texture());

        // .mcmeta-АНИМАЦИЯ: если рядом с текстурой лежит .mcmeta с секцией
        // animation, текстура считается вертикальным стрипом кадров (как у
        // ванильных блоков). Берём диапазон V текущего кадра; иначе весь [0,1].
        float[] v = animationV(body.texture());
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

    /** Кэш метаданных анимации по текстуре (чтобы не читать .mcmeta каждый кадр). */
    private static final java.util.Map<ResourceLocation, int[]> ANIM_CACHE =
        new java.util.HashMap<>();

    /**
     * Возвращает диапазон {@code {v0, v1}} по вертикали для текущего кадра
     * анимации тела. Если у текстуры нет {@code .mcmeta} с секцией
     * {@code animation} — весь диапазон {@code {0,1}} (статичная картинка).
     *
     * <p>Логика повторяет ванильную животину блоков: текстура-стрип из N кадров
     * по вертикали (высота = N × ширина), каждый кадр показывается
     * {@code frametime} тиков, кадры листаются по кругу от игрового времени.
     * Всё в try/catch — при любой ошибке чтения откатываемся на статичный кадр,
     * так что краша быть не может (вынос «ебли» на картинки автора).
     *
     * <p>Формат {@code .mcmeta} — ванильный, например:
     * <pre>{ "animation": { "frametime": 2 } }</pre>
     */
    private float[] animationV(ResourceLocation tex) {
        int[] meta = ANIM_CACHE.computeIfAbsent(tex, this::readAnimation);
        int frames = meta[0];
        int frametime = meta[1];
        if (frames <= 1) {
            return new float[] { 0.0F, 1.0F };
        }
        long ticks = 0L;
        if (Minecraft.getInstance().level != null) {
            ticks = Minecraft.getInstance().level.getGameTime();
        }
        int frame = (int) ((ticks / Math.max(1, frametime)) % frames);
        float h = 1.0F / frames;
        float v0 = frame * h;
        return new float[] { v0, v0 + h };
    }

    /**
     * Читает {@code .mcmeta} рядом с текстурой и определяет число кадров +
     * frametime. Число кадров = высота/ширина PNG (вертикальный стрип). Возвращает
     * {@code {frames, frametime}}; {@code {1,1}} — нет анимации/ошибка.
     */
    private int[] readAnimation(ResourceLocation tex) {
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            ResourceLocation metaLoc = ResourceLocation.fromNamespaceAndPath(
                tex.getNamespace(), tex.getPath() + ".mcmeta");
            var metaRes = rm.getResource(metaLoc);
            if (metaRes.isEmpty()) {
                return new int[] { 1, 1 };
            }
            // Разбор frametime из json (без строгой схемы — простое чтение).
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
            // Число кадров из размеров PNG (высота / ширина).
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

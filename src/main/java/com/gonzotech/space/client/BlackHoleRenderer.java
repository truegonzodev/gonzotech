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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Рендерер горизонта событий, аккреционного диска, орбитальных частиц и Колец Дайсона Чёрных Дыр.
 *
 * <p>Финальные точные параметры измерений:
 * <ul>
 *   <li><b>yx989_k2:</b>
 *     <ul>
 *       <li>Горизонт событий: {@code 120} блоков</li>
 *       <li>Фотонное кольцо: {@code 126 - 134} блоков</li>
 *       <li>ISCO-зазор: {@code 120 - 135} блоков</li>
 *       <li>Аккреционный диск: {@code 138 - 500} блоков</li>
 *       <li>Кольцо Дайсона: радиус {@code 520} блоков (граница диска + 20)</li>
 *     </ul>
 *   </li>
 *   <li><b>zangler_11:</b>
 *     <ul>
 *       <li>Горизонт событий: {@code 200} блоков</li>
 *       <li>Фотонное кольцо: {@code 214 - 224} блоков</li>
 *       <li>ISCO-зазор: {@code 200 - 225} блоков</li>
 *       <li>Аккреционный диск: {@code 228 - 1300} блоков</li>
 *       <li>Кольцо Дайсона: радиус {@code 1320} блоков (граница диска + 20)</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class BlackHoleRenderer {

    private BlackHoleRenderer() {
    }

    /** Координаты центра чёрной дыры в мире. */
    public static final double CENTER_X = 0.0;
    public static final double CENTER_Y = 160.0;
    public static final double CENTER_Z = 0.0;

    // === ФИНАЛЬНЫЕ ПАРАМЕТРЫ ДЛЯ BLACKHOLE_YX989_K2 ===
    public static final float RADIUS_YX989_K2 = 120.0F;
    public static final float RING_MIN_YX989_K2 = 126.0F;
    public static final float RING_MAX_YX989_K2 = 134.0F;
    public static final float DISK_IN_YX989_K2 = 138.0F;
    public static final float DISK_OUT_YX989_K2 = 500.0F;
    public static final float DYSON_RING_YX989_K2 = 520.0F; // 500 + 20 блоков

    // === ФИНАЛЬНЫЕ ПАРАМЕТРЫ ДЛЯ BLACKHOLE_ZANGLER_11 ===
    public static final float RADIUS_ZANGLER_11 = 200.0F;
    public static final float RING_MIN_ZANGLER_11 = 214.0F;
    public static final float RING_MAX_ZANGLER_11 = 224.0F;
    public static final float DISK_IN_ZANGLER_11 = 228.0F;
    public static final float DISK_OUT_ZANGLER_11 = 1300.0F;
    public static final float DYSON_RING_ZANGLER_11 = 1320.0F; // 1300 + 20 блоков

    /** Текстура ванильной пиксельной пылинки 8x8 px. */
    public static final ResourceLocation DUST_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/particle/dust.png");

    /** Целевое среднее число активных частиц на орбите (~150-170). */
    private static final int TARGET_PARTICLE_COUNT = 160;

    private static final RandomSource RANDOM = RandomSource.create();

    /** Список активных орбитальных частиц аккреционного диска. */
    private static final List<AccretionParticle> PARTICLES = new ArrayList<>();

    /** Число секторов и колец запасной сферы. */
    private static final int SPHERE_STACKS = 64;
    private static final int SPHERE_SECTORS = 64;

    /** Предрассчитанный массив вершин единичной сферы (fallback). */
    private static float[] sphereVertices;

    /**
     * Класс динамической орбитальной частицы аккреционного диска.
     */
    private static final class AccretionParticle {
        float radius;          // Расстояние от центра ЧД в плоскости диска (блоки)
        float angle;           // Текущий азимутальный угол (радианы)
        float heightOffset;    // Смещение по нормали плоскости диска (толщина диска)
        float orbitalSpeed;    // Угловая скорость (радианы/тик)
        float initialSize;     // Начальный размер при спавне (12..38.4 блока)
        int age;               // Текущий возраст (тики)
        int maxAge;            // Полное время жизни (10..20 сек = 200..400 тиков)

        AccretionParticle(float radius, float angle, float heightOffset,
                          float orbitalSpeed, float initialSize, int age, int maxAge) {
            this.radius = radius;
            this.angle = angle;
            this.heightOffset = heightOffset;
            this.orbitalSpeed = orbitalSpeed;
            this.initialSize = initialSize;
            this.age = age;
            this.maxAge = maxAge;
        }
    }

    /**
     * Спавн одной новой орбитальной частицы по точным границам диска.
     */
    private static AccretionParticle createParticle(float diskIn, float diskOut, boolean randomAge) {
        float u;
        while (true) {
            u = RANDOM.nextFloat();
            if (RANDOM.nextFloat() <= (1.0F - 0.40F * u)) {
                break;
            }
        }
        float r = diskIn + u * (diskOut - diskIn);

        // Скорость: вблизи максимальная 20.0 блоков/сек, на краю диска 0.5 блоков/сек
        float speedBlocksPerSec = Mth.lerp(u, 20.0F, 0.5F);
        float speedBlocksPerTick = speedBlocksPerSec / 20.0F;
        float orbitalSpeed = speedBlocksPerTick / r;

        // Время жизни: 10–20 секунд (200–400 клиентских тиков)
        int maxAge = 200 + RANDOM.nextInt(201);
        int age = randomAge ? RANDOM.nextInt(maxAge) : 0;

        // Начальный размер: от 12.0 до 38.4 блоков.
        float minSize = Mth.lerp(u, 21.6F, 12.0F);
        float maxSize = Mth.lerp(u, 38.4F, 19.2F);
        float initialSize = minSize + RANDOM.nextFloat() * (maxSize - minSize);

        // Начальный угол и высота в диске (толщина ±6 блоков)
        float angle = RANDOM.nextFloat() * (float) (2.0 * Math.PI);
        float heightOffset = (RANDOM.nextFloat() - 0.5F) * 12.0F;

        return new AccretionParticle(r, angle, heightOffset, orbitalSpeed, initialSize, age, maxAge);
    }

    /**
     * Обновление динамики частиц каждый клиентский тик.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }

        ResourceKey<Level> dim = level.dimension();
        float diskIn, diskOut;
        if (dim == SpaceDimensions.BLACKHOLE_YX989_K2) {
            diskIn = DISK_IN_YX989_K2;
            diskOut = DISK_OUT_YX989_K2;
        } else if (dim == SpaceDimensions.BLACKHOLE_ZANGLER_11) {
            diskIn = DISK_IN_ZANGLER_11;
            diskOut = DISK_OUT_ZANGLER_11;
        } else {
            PARTICLES.clear();
            return;
        }

        if (PARTICLES.isEmpty()) {
            for (int i = 0; i < TARGET_PARTICLE_COUNT; i++) {
                PARTICLES.add(createParticle(diskIn, diskOut, true));
            }
        }

        Iterator<AccretionParticle> it = PARTICLES.iterator();
        while (it.hasNext()) {
            AccretionParticle p = it.next();
            p.age++;
            p.angle += p.orbitalSpeed;
            if (p.angle >= (float) (2.0 * Math.PI)) {
                p.angle -= (float) (2.0 * Math.PI);
            }

            if (p.age >= p.maxAge) {
                it.remove();
            }
        }

        while (PARTICLES.size() < TARGET_PARTICLE_COUNT) {
            PARTICLES.add(createParticle(diskIn, diskOut, false));
        }
    }

    /**
     * Рендеринг Чёрной Дыры, аккреционных частиц и Кольца Дайсона.
     */
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
        float radius, ringMin, ringMax, diskIn, diskOut, dysonRadius;
        boolean isDysonActive;

        if (dim == SpaceDimensions.BLACKHOLE_YX989_K2) {
            radius = RADIUS_YX989_K2;
            ringMin = RING_MIN_YX989_K2;
            ringMax = RING_MAX_YX989_K2;
            diskIn = DISK_IN_YX989_K2;
            diskOut = DISK_OUT_YX989_K2;
            dysonRadius = DYSON_RING_YX989_K2;
            isDysonActive = SpaceSkyState.yx989Dyson;
        } else if (dim == SpaceDimensions.BLACKHOLE_ZANGLER_11) {
            radius = RADIUS_ZANGLER_11;
            ringMin = RING_MIN_ZANGLER_11;
            ringMax = RING_MAX_ZANGLER_11;
            diskIn = DISK_IN_ZANGLER_11;
            diskOut = DISK_OUT_ZANGLER_11;
            dysonRadius = DYSON_RING_ZANGLER_11;
            isDysonActive = SpaceSkyState.zanglerDyson;
        } else {
            return; // Не измерение с чёрной дырой
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        // 1. Основной рендер релятивистской Чёрной Дыры через GPU-шейдер
        if (BlackHoleShader.init()) {
            BlackHoleShader.render(camPos, event.getModelViewMatrix(), event.getProjectionMatrix(),
                                   radius, ringMin, ringMax, diskIn, diskOut);
        } else {
            // Запасной путь (fallback)
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

        // 2. Рендеринг гигантских vanilla-like билборд-частиц (12–38.4 блоков)
        if (!PARTICLES.isEmpty()) {
            renderAccretionParticles(event, camera, camPos, radius);
        }

        // 3. Рендеринг 3D Кольца Дайсона вокруг Чёрной Дыры (если активировано)
        if (isDysonActive) {
            renderDysonRing(event, camera, camPos, dysonRadius);
        }
    }

    /**
     * Отрисовка высокотехнологичного 3D-кольца Дайсона (мегаструктуры) вокруг Чёрной Дыры.
     *
     * <p>Параметры геометрии звеньев:
     * <ul>
     *   <li>Высота сегмента: {@code 16} блоков</li>
     *   <li>Радиальная толщина: {@code 12} блоков ({@code R-6 .. R+6})</li>
     *   <li>Длина сегмента: {@code ~16} блоков вдоль окружности</li>
     *   <li>Вращение по Y: 1 оборот за 200 сек</li>
     *   <li>Кульбит (вращение по X): 1 оборот за 1500 сек</li>
     * </ul>
     */
    private static void renderDysonRing(RenderLevelStageEvent event, Camera camera, Vec3 camPos, float ringRadius) {
        Matrix4f mvMatrix = event.getModelViewMatrix();

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SpaceSkyState.TEX_DYSON_RING);

        // Пиксельная чёткость текстуры без мыла
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // Время для плавного непрерывного вращения
        double timeSec = (double) (System.nanoTime() / 1_000_000L) * 0.001;
        float phiYaw = (float) ((timeSec % 200.0) / 200.0 * (2.0 * Math.PI));     // 1 оборот за 200 сек
        float phiPitch = (float) ((timeSec % 1500.0) / 1500.0 * (2.0 * Math.PI)); // 1 кульбит за 1500 сек

        // Составной поворот кольца: наклон к диску + вращение вокруг оси + медленный кульбит
        Quaternionf ringRot = new Quaternionf()
            .rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), BlackHoleShader.DISK_NORMAL)
            .rotateX(phiPitch)
            .rotateY(phiYaw);

        float rIn = ringRadius - 6.0F;
        float rOut = ringRadius + 6.0F;
        float yLow = -8.0F;
        float yHigh = 8.0F;

        // Число сегментов из расчёта ~16 блоков на звено
        int segments = Math.max(32, Math.round((float) (2.0 * Math.PI * ringRadius / 16.0F)));
        float angleStep = (float) (2.0 * Math.PI / segments);

        float ox = (float) (CENTER_X - camPos.x);
        float oy = (float) (CENTER_Y - camPos.y);
        float oz = (float) (CENTER_Z - camPos.z);

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        Vector3f p0InLow = new Vector3f();
        Vector3f p0InHigh = new Vector3f();
        Vector3f p0OutLow = new Vector3f();
        Vector3f p0OutHigh = new Vector3f();

        Vector3f p1InLow = new Vector3f();
        Vector3f p1InHigh = new Vector3f();
        Vector3f p1OutLow = new Vector3f();
        Vector3f p1OutHigh = new Vector3f();

        for (int i = 0; i < segments; i++) {
            float a0 = i * angleStep;
            float a1 = (i + 1) * angleStep;

            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            // Точки при угле a0
            p0InLow.set(rIn * cos0, yLow, rIn * sin0).rotate(ringRot).add(ox, oy, oz);
            p0InHigh.set(rIn * cos0, yHigh, rIn * sin0).rotate(ringRot).add(ox, oy, oz);
            p0OutLow.set(rOut * cos0, yLow, rOut * sin0).rotate(ringRot).add(ox, oy, oz);
            p0OutHigh.set(rOut * cos0, yHigh, rOut * sin0).rotate(ringRot).add(ox, oy, oz);

            // Точки при угле a1
            p1InLow.set(rIn * cos1, yLow, rIn * sin1).rotate(ringRot).add(ox, oy, oz);
            p1InHigh.set(rIn * cos1, yHigh, rIn * sin1).rotate(ringRot).add(ox, oy, oz);
            p1OutLow.set(rOut * cos1, yLow, rOut * sin1).rotate(ringRot).add(ox, oy, oz);
            p1OutHigh.set(rOut * cos1, yHigh, rOut * sin1).rotate(ringRot).add(ox, oy, oz);

            // 1. ВНУТРЕННЯЯ ГРАНЬ (К центру ЧД / плазме) - UV: [0.0..0.5, 0.0..0.5] (256x256 px)
            buf.addVertex(mvMatrix, p0InLow.x, p0InLow.y, p0InLow.z).setUv(0.0F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1InLow.x, p1InLow.y, p1InLow.z).setUv(0.5F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1InHigh.x, p1InHigh.y, p1InHigh.z).setUv(0.5F, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p0InHigh.x, p0InHigh.y, p0InHigh.z).setUv(0.0F, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);

            // 2. ВНЕШНЯЯ ГРАНЬ (В открытый космос / броня) - UV: [0.5..1.0, 0.0..0.5] (256x256 px)
            buf.addVertex(mvMatrix, p1OutLow.x, p1OutLow.y, p1OutLow.z).setUv(0.5F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p0OutLow.x, p0OutLow.y, p0OutLow.z).setUv(1.0F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p0OutHigh.x, p0OutHigh.y, p0OutHigh.z).setUv(1.0F, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1OutHigh.x, p1OutHigh.y, p1OutHigh.z).setUv(0.5F, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);

            // 3. ВЕРХНЯЯ ГРАНЬ (Радиаторы охлаждения) - UV: [0.0..0.375, 0.5..1.0] (192x256 px)
            buf.addVertex(mvMatrix, p0InHigh.x, p0InHigh.y, p0InHigh.z).setUv(0.0F, 1.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1InHigh.x, p1InHigh.y, p1InHigh.z).setUv(0.0F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1OutHigh.x, p1OutHigh.y, p1OutHigh.z).setUv(0.375F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p0OutHigh.x, p0OutHigh.y, p0OutHigh.z).setUv(0.375F, 1.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);

            // 4. НИЖНЯЯ ГРАНЬ (Киль и стабилизаторы) - UV: [0.5..0.875, 0.5..1.0] (192x256 px)
            buf.addVertex(mvMatrix, p0OutLow.x, p0OutLow.y, p0OutLow.z).setUv(0.5F, 1.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1OutLow.x, p1OutLow.y, p1OutLow.z).setUv(0.5F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p1InLow.x, p1InLow.y, p1InLow.z).setUv(0.875F, 0.5F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            buf.addVertex(mvMatrix, p0InLow.x, p0InLow.y, p0InLow.z).setUv(0.875F, 1.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableCull();
        mvStack.popMatrix();
    }

    /**
     * Отрисовка гигантских vanilla-like билборд-частиц (32x32 px спрайт) с динамическим остыванием.
     */
    private static void renderAccretionParticles(RenderLevelStageEvent event, Camera camera, Vec3 camPos, float bhRadius) {
        Vector3f normal = BlackHoleShader.DISK_NORMAL;
        Vector3f ex = new Vector3f(0.0F, 1.0F, 0.0F).cross(normal).normalize();
        Vector3f ez = new Vector3f(normal).cross(ex).normalize();

        Quaternionf camRot = camera.rotation();
        Vector3f camRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camRot);
        Vector3f camUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camRot);

        Matrix4f mvMatrix = event.getModelViewMatrix();

        // Вектор от камеры к центру ЧД
        double toCx = CENTER_X - camPos.x;
        double toCy = CENTER_Y - camPos.y;
        double toCz = CENTER_Z - camPos.z;
        double distCSq = toCx * toCx + toCy * toCy + toCz * toCz;
        double distC = Math.sqrt(distCSq);

        // Критический радиус оптической тени Шварцшильда (с учетом гравитационного линзирования ~2.598 * rs)
        double bc = (double) bhRadius * 2.598 * Math.sqrt(Math.max(0.01, 1.0 - (double) bhRadius / Math.max((double) bhRadius, distC)));
        double shadowRadiusSq = bc * bc;

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, DUST_TEXTURE);

        // Пиксельная чёткость ванильной текстуры 8x8 без смазывания
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        BufferBuilder buf = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (AccretionParticle p : PARTICLES) {
            float cosT = (float) Math.cos(p.angle);
            float sinT = (float) Math.sin(p.angle);

            float px = (float) (CENTER_X + p.radius * (cosT * ex.x() + sinT * ez.x()) + normal.x() * p.heightOffset);
            float py = (float) (CENTER_Y + p.radius * (cosT * ex.y() + sinT * ez.y()) + normal.y() * p.heightOffset);
            float pz = (float) (CENTER_Z + p.radius * (cosT * ex.z() + sinT * ez.z()) + normal.z() * p.heightOffset);

            double toPx = px - camPos.x;
            double toPy = py - camPos.y;
            double toPz = pz - camPos.z;
            double distPSq = toPx * toPx + toPy * toPy + toPz * toPz;
            double distP = Math.sqrt(distPSq);

            // Отсечение частиц, находящихся ЗА гравитационной тенью Чёрной Дыры
            double dirX = toPx / distP;
            double dirY = toPy / distP;
            double dirZ = toPz / distP;

            double tProj = toCx * dirX + toCy * dirY + toCz * dirZ;
            if (tProj > 0.0 && tProj < distP) {
                double perpDistSq = distCSq - (tProj * tProj);
                if (perpDistSq <= shadowRadiusSq) {
                    continue;
                }
            }

            float progress = (float) p.age / (float) p.maxAge;

            // Размер: уменьшается на 90% (становится 1.2..3.84 блоков)
            float currentSize = p.initialSize * (1.0F - 0.90F * progress);
            float hs = currentSize * 0.5F;

            // Цветовой перелив плазмы
            float red, green, blue;
            if (progress < 0.25F) {
                float f = progress / 0.25F;
                red   = Mth.lerp(f, 1.000F, 1.000F);
                green = Mth.lerp(f, 0.988F, 0.816F);
                blue  = Mth.lerp(f, 0.949F, 0.000F);
            } else if (progress < 0.65F) {
                float f = (progress - 0.25F) / 0.40F;
                red   = Mth.lerp(f, 1.000F, 0.600F);
                green = Mth.lerp(f, 0.816F, 0.000F);
                blue  = Mth.lerp(f, 0.000F, 0.067F);
            } else {
                float f = (progress - 0.65F) / 0.35F;
                red   = Mth.lerp(f, 0.600F, 0.227F);
                green = Mth.lerp(f, 0.000F, 0.227F);
                blue  = Mth.lerp(f, 0.067F, 0.227F);
            }

            float alpha;
            if (progress < 0.08F) {
                alpha = progress / 0.08F;
            } else if (progress < 0.65F) {
                alpha = 1.0F;
            } else {
                alpha = (1.0F - progress) / 0.35F;
            }

            float rx = (float) toPx;
            float ry = (float) toPy;
            float rz = (float) toPz;

            float rX = camRight.x() * hs;
            float rY = camRight.y() * hs;
            float rZ = camRight.z() * hs;

            float uX = camUp.x() * hs;
            float uY = camUp.y() * hs;
            float uZ = camUp.z() * hs;

            buf.addVertex(mvMatrix, rx - rX - uX, ry - rY - uY, rz - rZ - uZ)
               .setUv(0.0F, 1.0F)
               .setColor(red, green, blue, alpha);

            buf.addVertex(mvMatrix, rx + rX - uX, ry + rY - uY, rz + rZ - uZ)
               .setUv(1.0F, 1.0F)
               .setColor(red, green, blue, alpha);

            buf.addVertex(mvMatrix, rx + rX + uX, ry + rY + uY, rz + rZ + uZ)
               .setUv(1.0F, 0.0F)
               .setColor(red, green, blue, alpha);

            buf.addVertex(mvMatrix, rx - rX + uX, ry - rY + uY, rz - rZ + uZ)
               .setUv(0.0F, 0.0F)
               .setColor(red, green, blue, alpha);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
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

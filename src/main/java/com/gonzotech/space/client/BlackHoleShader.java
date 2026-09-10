package com.gonzotech.space.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
<<<<<<< HEAD
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
=======
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.nio.FloatBuffer;

/**
<<<<<<< HEAD
 * Шейдерная программа гравитационного линзирования и аккреционного диска Чёрных Дыр.
 *
 * <p>Реализует полный релятивистский пайплайн (модель Гаргантюа / Шварцшильда):
 * <ul>
 *   <li><b>Гравитационное линзирование (Einstein lensing):</b> искривление световых лучей
 *       в сильном гравитационном поле ЧД. Лучи огибают горизонт событий, проецируя
 *       обратную сторону аккреционного диска в виде верхней и нижней светящихся арок (Interstellar halo).</li>
 *   <li><b>Фотонное кольцо (Photon / Einstein Ring):</b> тончайшее сверхъяркое кольцо захваченных
 *       фотонов на критическом прицельном параметре {@code b_c ≈ 2.6 r_s}.</li>
 *   <li><b>Релятивистский аккреционный диск:</b> кеплеровское дифференциальное вращение плазмы
 *       ({@code Ω ~ r^-1.5}), логарифмические спиральные рукава турбулентности, градиент температур
 *       от ISCO ({@code 2.8 r_s}) до внешнего края ({@code 8.2 r_s}).</li>
 *   <li><b>Релятивистский эффект Доплера (Doppler Beaming):</b> сторона диска, вращающаяся навстречу
 *       наблюдателю, усилена по яркости ({@code ~δ^3.4}) и смещена в бело-голубой спектр,
 *       а удаляющаяся — затемнена и смещена в глубокий багрово-красный.</li>
 *   <li><b>Горизонт событий:</b> 100% поглощающая сфера радиуса {@code r_s} с честной записью
 *       в буфер глубины ({@code gl_FragDepth}).</li>
=======
 * Релятивистский GPU-шейдер Чёрных Дыр (модель Гаргантюа / Шварцшильда).
 *
 * <p>Использует честное численное интегрирование геодезических нулевой кривизны
 * (уравнение Эйнштейна для траектории фотонов в метрике Шварцшильда):
 * <ul>
 *   <li><b>Гравитационное линзирование (Interstellar halo):</b> искривление световых лучей
 *       в гравитационной яме ЧД естественным образом проецирует заднюю сторону аккреционного
 *       диска в верхнюю и нижнюю арки без разрывов, паразитных кайм и дублирующих колец.</li>
 *   <li><b>Фотонное кольцо Эйнштейна:</b> фотоны на орбите {@code r ≈ 1.5 r_s} совершают
 *       обороты вокруг горизонта, накапливая свечение и формируя сверхъяркое тонкое кольцо.</li>
 *   <li><b>Релятивистский аккреционный диск:</b> кеплеровское вращение ({@code Ω ~ r^-1.5}),
 *       логарифмические спирали плазмы, спектральный эффект Доплера (набегающая сторона
 *       усилена и смещена в бело-голубой цвет, удаляющаяся — в тёмно-красный).</li>
 *   <li><b>Устранение паразитных клонов:</b> при взгляде от ЧД лучи уходят в бесконечность
 *       без ложных отражений и фантомных горизонтов.</li>
 *   <li><b>Честный буфер глубины ({@code gl_FragDepth}):</b> горизонт событий и передний
 *       план диска корректно перекрывают/перекрываются блоками в мире.</li>
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
 * </ul>
 */
public final class BlackHoleShader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized = false;
    private static boolean initFailed = false;

    private static int programId = 0;
    private static int vaoId = 0;
    private static int vboId = 0;

    // Uniform locations
    private static int uCamPosLoc = -1;
    private static int uInvViewMatLoc = -1;
    private static int uViewMatLoc = -1;
    private static int uProjMatrixLoc = -1;
    private static int uTanFovLoc = -1;
    private static int uRadiusLoc = -1;
    private static int uTimeLoc = -1;
    private static int uDiskNormalLoc = -1;

    // Вспомогательные буферы для загрузки матриц
    private static final float[] MAT_ARRAY = new float[16];
    private static final FloatBuffer MAT_BUFFER = BufferUtils.createFloatBuffer(16);

<<<<<<< HEAD
    // Нормаль плоскости аккреционного диска (слегка наклонена для зрелищной 3D-перспективы)
    private static final Vector3f DISK_NORMAL = new Vector3f(0.12F, 0.98F, 0.15F).normalize();
=======
    /** Нормаль плоскости аккреционного диска (наклон ~12° для фотогеничной 3D-перспективы). */
    public static final Vector3f DISK_NORMAL = new Vector3f(0.12F, 0.98F, 0.15F).normalize();
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)

    private static final String VERTEX_SHADER_SRC = """
        #version 330 core
        layout(location = 0) in vec2 a_pos;
        out vec2 v_uv;
        void main() {
            v_uv = a_pos;
            gl_Position = vec4(a_pos, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT_SHADER_SRC = """
        #version 330 core

        in vec2 v_uv;
        out vec4 fragColor;

        uniform vec3 u_camPos;
        uniform mat4 u_invViewMat;
        uniform mat4 u_viewMat;
        uniform mat4 u_projMatrix;
        uniform vec2 u_tanFov;
        uniform float u_radius;
        uniform float u_time;
        uniform vec3 u_diskNormal;

        // 2D Хэш
        float hash(vec2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

<<<<<<< HEAD
        // 2D Шум значений со сглаживанием
=======
        // 2D Шум
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + vec2(1.0, 0.0));
            float c = hash(i + vec2(0.0, 1.0));
            float d = hash(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

<<<<<<< HEAD
        // 4-октавный шум турбулентности плазмы
=======
        // 4-октавный турбулентный шум плазмы
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
        float fbm(vec2 p) {
            float v = 0.0;
            float amp = 0.5;
            mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
            for (int i = 0; i < 4; i++) {
                v += amp * noise(p);
                p = rot * p * 2.05 + vec2(1.5, 3.2);
                amp *= 0.5;
            }
            return v;
        }

<<<<<<< HEAD
        // Вычисление свечения и цвета аккреционного диска в точке hitPos
        vec4 sampleDisk(vec3 hitPos, float r, vec3 viewRay, float rs, float time, vec3 n, float Rin, float Rout) {
            float u = (r - Rin) / (Rout - Rin);
            if (u < 0.0 || u > 1.0) return vec4(0.0);

            // Радиальный профиль: резкий рост от ISCO, пик на ~3.5 r_s, плавное затухание наружу
            float radial = pow(u, 0.45) * pow(1.0 - u, 1.8) * 3.8;

            // Локальный базис плоскости диска
            vec3 tangentX = normalize(abs(n.y) < 0.99 ? cross(vec3(0.0, 1.0, 0.0), n) : cross(vec3(1.0, 0.0, 0.0), n));
            vec3 tangentZ = cross(n, tangentX);

            float phi = atan(dot(hitPos, tangentZ), dot(hitPos, tangentX));

            // Кеплеровское дифференциальное вращение (внутренний газ вращается быстрее)
            float omega = 1.6 * pow(Rin / r, 1.5);
            float phi_rot = phi - omega * time * 0.5;

            // Логарифмические спиральные струи плазмы
            float spiral = phi_rot + 3.2 * log(r / Rin);
            vec2 noiseUV = vec2(r / rs * 0.4, spiral * 1.2);

            float n1 = fbm(noiseUV);
            float n2 = fbm(noiseUV * 2.2 + vec2(time * 0.1, time * 0.05));
            float plasma = n1 * 0.65 + n2 * 0.35;

            // Релятивистский эффект Доплера (Doppler Beaming)
            vec3 v_orb = normalize(cross(n, hitPos));
            float speed_frac = 0.55 * sqrt(Rin / r);
            float beta_parallel = dot(v_orb, -viewRay) * speed_frac;
            float gamma = 1.0 / sqrt(max(0.01, 1.0 - speed_frac * speed_frac));
            float doppler = 1.0 / (gamma * (1.0 - beta_parallel));
            float beaming = pow(doppler, 3.4);

            // Цветовая температура:
            // Внутренний край + набегающая сторона = раскалённое бело-золотое/голубоватое свечение
            // Средняя зона = яркий янтарно-золотой
            // Внешний край + удаляющаяся сторона = тёмный багрово-красный
            float temp = (1.0 - u * 0.75) * doppler * (0.8 + 0.4 * plasma);

            vec3 colCool = vec3(0.65, 0.12, 0.02);
            vec3 colWarm = vec3(1.0, 0.52, 0.08);
            vec3 colHot  = vec3(1.0, 0.92, 0.75);
            vec3 colBlue = vec3(0.85, 0.92, 1.0);

            vec3 col = mix(colCool, colWarm, clamp(temp * 1.2, 0.0, 1.0));
            col = mix(col, colHot, clamp((temp - 0.7) * 2.0, 0.0, 1.0));
            if (temp > 1.4) {
                col = mix(col, colBlue, clamp((temp - 1.4) * 1.5, 0.0, 1.0));
            }

            float density = radial * (0.4 + 0.6 * plasma) * beaming * 1.8;
            float alpha = clamp(density, 0.0, 1.0);
=======
        // Сэмплирование аккреционного диска в мировой точке pos
        vec4 sampleAccretionDisk(vec3 pos, float r, vec3 viewRay, float rs, float time, vec3 n, float Rin, float Rout) {
            float u = (r - Rin) / (Rout - Rin);
            if (u < 0.0 || u > 1.0) return vec4(0.0);

            // Радиальный профиль: резкий рост от ISCO, пик вблизи 3.2 r_s, плавное затухание наружу
            float radial = pow(u, 0.25) * pow(1.0 - u, 1.5) * 4.2;

            // Базис плоскости диска
            vec3 tangentX = normalize(abs(n.y) < 0.99 ? cross(vec3(0.0, 1.0, 0.0), n) : cross(vec3(1.0, 0.0, 0.0), n));
            vec3 tangentZ = cross(n, tangentX);
            float phi = atan(dot(pos, tangentZ), dot(pos, tangentX));

            // Кеплеровская угловая скорость Omega ~ r^-1.5
            float omega = 1.8 * pow(Rin / r, 1.5);
            float phi_rot = phi - omega * time * 0.4;

            // Логарифмические спирали турбулентности
            float spiral = phi_rot + 3.5 * log(r / Rin);
            vec2 noiseUV = vec2(r / rs * 0.5, spiral * 1.5);

            float n1 = fbm(noiseUV);
            float n2 = fbm(noiseUV * 2.2 + vec2(time * 0.08, time * 0.04));
            float plasma = n1 * 0.65 + n2 * 0.35;

            // Релятивистский эффект Доплера (Doppler Beaming)
            vec3 v_orb = normalize(cross(n, pos));
            float speed_frac = 0.52 * sqrt(Rin / r); // v/c
            float beta_parallel = dot(v_orb, -viewRay) * speed_frac;
            float gamma = 1.0 / sqrt(max(0.01, 1.0 - speed_frac * speed_frac));
            float doppler = 1.0 / (gamma * (1.0 - beta_parallel));
            float beaming = pow(doppler, 3.2);

            // Цветовая температура (палитра Интерстеллара)
            float temp = (1.0 - u * 0.8) * doppler * (0.85 + 0.35 * plasma);

            vec3 colCool = vec3(0.70, 0.16, 0.02); // Тёмный багрово-янтарный
            vec3 colWarm = vec3(1.00, 0.58, 0.12); // Сияющий золотисто-оранжевый
            vec3 colHot  = vec3(1.00, 0.94, 0.80); // Раскалённый бело-золотой
            vec3 colBlue = vec3(0.90, 0.95, 1.00); // Релятивистский синий сдвиг

            vec3 col = mix(colCool, colWarm, clamp(temp * 1.3, 0.0, 1.0));
            col = mix(col, colHot, clamp((temp - 0.75) * 2.2, 0.0, 1.0));
            if (temp > 1.35) {
                col = mix(col, colBlue, clamp((temp - 1.35) * 1.8, 0.0, 1.0));
            }

            float density = radial * (0.45 + 0.55 * plasma) * beaming * 1.6;
            float alpha = clamp(density * 0.85, 0.0, 0.95);
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)

            return vec4(col * density, alpha);
        }

        void main() {
<<<<<<< HEAD
            // Точный мировой луч из камеры для текущего фрагмента
=======
            // Точный мировой луч из камеры
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
            vec3 rayCam = normalize(vec3(v_uv.x * u_tanFov.x, v_uv.y * u_tanFov.y, -1.0));
            vec3 rayDir = normalize((u_invViewMat * vec4(rayCam, 0.0)).xyz);

            float D = length(u_camPos);
<<<<<<< HEAD

            // Обработка нахождения игрока внутри горизонта событий
            if (D < u_radius) {
                float cosExit = sqrt(max(0.0, 1.0 - D / u_radius));
=======
            float rs = u_radius;

            // Нахождение внутри горизонта событий
            if (D < rs) {
                float cosExit = sqrt(max(0.0, 1.0 - D / rs));
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
                vec3 toCenter = -u_camPos / max(0.001, D);
                if (dot(rayDir, toCenter) < cosExit) {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    gl_FragDepth = 0.9999;
                    return;
                }
            }

            vec3 u_bh = -u_camPos / max(0.001, D);
            float cosTheta = dot(rayDir, u_bh);
<<<<<<< HEAD
            float sinThetaSq = max(0.0, 1.0 - cosTheta * cosTheta);
            float sinTheta = sqrt(sinThetaSq);
            float b = D * sinTheta;

            // Критический прицельный параметр фотонной тени ЧД
            float b_c = u_radius * 2.598076 * sqrt(max(0.001, 1.0 - u_radius / max(D, u_radius * 0.99)));
            bool hitHorizon = (cosTheta > 0.0 && b <= b_c);

            // Точная глубина горизонта для буфера глубины
            float horizonDepth = 1.0;
            if (hitHorizon) {
                float B = dot(u_camPos, rayDir);
                float C = dot(u_camPos, u_camPos) - u_radius * u_radius;
                float discr = max(0.0, B * B - C);
                float tSphere = -B - sqrt(discr);
                if (tSphere <= 0.0) tSphere = D * cosTheta;
                vec3 hitCam = tSphere * rayDir;
                vec4 hitClip = u_projMatrix * (u_viewMat * vec4(hitCam, 0.0));
                horizonDepth = (hitClip.z / hitClip.w) * 0.5 + 0.5;
            }

            // Геометрия диска
            vec3 n = normalize(u_diskNormal);
            float Rin = 2.8 * u_radius;
            float Rout = 8.2 * u_radius;

            // 1. Прямой аккреционный диск (передний план)
            float denom1 = dot(rayDir, n);
            vec4 diskColor1 = vec4(0.0);
            float diskDist1 = 1e9;
            if (abs(denom1) > 0.0001) {
                float t1 = -dot(u_camPos, n) / denom1;
                if (t1 > 0.0) {
                    vec3 hit1 = u_camPos + t1 * rayDir;
                    float r1 = length(hit1);
                    if (r1 >= Rin && r1 <= Rout) {
                        bool inFront = (t1 < D * max(0.0, cosTheta)) || !hitHorizon;
                        if (inFront) {
                            diskColor1 = sampleDisk(hit1, r1, rayDir, u_radius, u_time, n, Rin, Rout);
                            diskDist1 = t1;
                        }
                    }
                }
            }

            // 2. Гравитационно-линзированный диск (задние верхние и нижние арки)
            vec4 diskColor2 = vec4(0.0);
            if (!hitHorizon || b > b_c * 0.85) {
                float b_rel = max(0.1, b / u_radius);
                float bc_rel = b_c / u_radius;
                float delta_b = max(0.01, b_rel - bc_rel);
                float alpha = (4.0 / b_rel) + (1.2 / (delta_b * delta_b + 0.08));
                alpha = min(alpha, 3.141592);

                vec3 w = (sinTheta > 0.0001) ? normalize(rayDir - cosTheta * u_bh) : vec3(0.0, 1.0, 0.0);
                float theta_bent = acos(clamp(cosTheta, -1.0, 1.0)) - alpha;
                vec3 bentRayDir = cos(theta_bent) * u_bh + sin(theta_bent) * w;

                vec3 midPoint = u_camPos + (D * max(0.0, cosTheta)) * rayDir;
                float denom2 = dot(bentRayDir, n);
                if (abs(denom2) > 0.0001) {
                    float t2 = -dot(midPoint, n) / denom2;
                    if (t2 > 0.0) {
                        vec3 hit2 = midPoint + t2 * bentRayDir;
                        float r2 = length(hit2);
                        if (r2 >= Rin && r2 <= Rout) {
                            diskColor2 = sampleDisk(hit2, r2, bentRayDir, u_radius, u_time, n, Rin, Rout);
                            diskColor2.rgb *= 1.35;
                        }
                    }
                }
            }

            // 3. Фотонное кольцо Эйнштейна
            vec3 photonRing = vec3(0.0);
            float ringOffset = (b - b_c) / (u_radius * 0.035);
            if (ringOffset > 0.0 && ringOffset < 4.0) {
                float ringIntensity = exp(-ringOffset * ringOffset * 1.5) * 4.0;
                photonRing = vec3(1.0, 0.94, 0.82) * ringIntensity;
            }

            // Итоговая композиция
            if (hitHorizon) {
                vec3 finalRgb = diskColor1.rgb + photonRing;
                fragColor = vec4(finalRgb, 1.0);
                gl_FragDepth = horizonDepth;
            } else {
                vec3 finalRgb = diskColor1.rgb + diskColor2.rgb * (1.0 - diskColor1.a * 0.7) + photonRing;
                float finalAlpha = clamp(diskColor1.a + diskColor2.a + (length(photonRing) > 0.05 ? 1.0 : 0.0), 0.0, 1.0);

                fragColor = vec4(finalRgb, finalAlpha);

                if (diskColor1.a > 0.3) {
                    vec3 hitCam = diskDist1 * rayDir;
=======

            // Параметры диска
            vec3 n = normalize(u_diskNormal);
            float Rin = 2.6 * rs;
            float Rout = 7.8 * rs;

            // Быстрый выход для лучей, направленных строго от Чёрной Дыры (устраняет паразитных клонов)
            if (D > 2.0 * rs && cosTheta < -0.2) {
                float denom = dot(rayDir, n);
                if (abs(denom) > 0.0001) {
                    float t = -dot(u_camPos, n) / denom;
                    if (t > 0.0) {
                        vec3 hit = u_camPos + t * rayDir;
                        float r = length(hit);
                        if (r >= Rin && r <= Rout) {
                            vec4 sample = sampleAccretionDisk(hit, r, rayDir, rs, u_time, n, Rin, Rout);
                            if (sample.a > 0.005) {
                                fragColor = sample;
                                vec3 hitCam = t * rayDir;
                                vec4 hitClip = u_projMatrix * (u_viewMat * vec4(hitCam, 0.0));
                                gl_FragDepth = (hitClip.z / hitClip.w) * 0.5 + 0.5;
                                return;
                            }
                        }
                    }
                }
                fragColor = vec4(0.0);
                gl_FragDepth = 1.0;
                return;
            }

            // Численное интегрирование геодезических луча света в гравитационном поле Шварцшильда
            vec3 x = u_camPos;
            vec3 v = rayDir;

            // Сохраняющийся угловой момент фотона: L = x × v
            vec3 L = cross(x, v);
            float L2 = dot(L, L);

            vec3 accumColor = vec3(0.0);
            float accumAlpha = 0.0;
            float firstDiskDist = -1.0;
            bool hitHorizon = false;
            vec3 horizonHitPos = vec3(0.0);

            const int MAX_STEPS = 40;

            for (int i = 0; i < MAX_STEPS; i++) {
                float r = length(x);

                // 1. Фотон поглощен горизонтом событий
                if (r <= rs * 1.02) {
                    hitHorizon = true;
                    horizonHitPos = x;
                    break;
                }

                // 2. Фотон ушел на бесконечность
                if (r > 9.0 * rs && dot(x, v) > 0.0) {
                    break;
                }

                // Адаптивный шаг интегрирования
                float ds = clamp(r * 0.18, rs * 0.08, rs * 0.35);

                // Релятивистское ускорение фотона: a = -1.5 * rs * L^2 / r^5 * x
                float r5 = r * r * r * r * r;
                vec3 a = (-1.5 * rs * L2 / max(1e-5, r5)) * x;

                // Шаг позиции (Verlet)
                vec3 x_next = x + v * ds + 0.5 * a * ds * ds;
                float r_next = length(x_next);

                // Ускорение в следующей точке
                float r5_next = r_next * r_next * r_next * r_next * r_next;
                vec3 a_next = (-1.5 * rs * L2 / max(1e-5, r5_next)) * x_next;

                // Шаг скорости
                vec3 v_next = normalize(v + 0.5 * (a + a_next) * ds);

                // 3. Проверка пересечения плоскости аккреционного диска между x и x_next
                float h1 = dot(x, n);
                float h2 = dot(x_next, n);

                if (h1 * h2 <= 0.0) {
                    float tau = abs(h1) / (abs(h1) + abs(h2) + 1e-7);
                    vec3 x_cross = mix(x, x_next, tau);
                    float r_cross = length(x_cross);

                    if (r_cross >= Rin && r_cross <= Rout) {
                        vec4 diskSample = sampleAccretionDisk(x_cross, r_cross, v_next, rs, u_time, n, Rin, Rout);
                        if (diskSample.a > 0.005) {
                            accumColor += (1.0 - accumAlpha) * diskSample.rgb;
                            accumAlpha += (1.0 - accumAlpha) * diskSample.a;

                            if (firstDiskDist < 0.0) {
                                firstDiskDist = length(x_cross - u_camPos);
                            }

                            if (accumAlpha > 0.98) {
                                break;
                            }
                        }
                    }
                }

                x = x_next;
                v = v_next;
            }

            // 4. Итоговая композиция цвета и глубины
            if (hitHorizon) {
                // Горизонт событий полностью поглощает фон за собой
                fragColor = vec4(accumColor, 1.0);

                vec3 hitCam = horizonHitPos - u_camPos;
                vec4 hitClip = u_projMatrix * (u_viewMat * vec4(hitCam, 0.0));
                gl_FragDepth = (hitClip.z / hitClip.w) * 0.5 + 0.5;
            } else {
                fragColor = vec4(accumColor, accumAlpha);

                if (firstDiskDist > 0.0 && accumAlpha > 0.3) {
                    vec3 hitCam = firstDiskDist * rayDir;
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
                    vec4 hitClip = u_projMatrix * (u_viewMat * vec4(hitCam, 0.0));
                    gl_FragDepth = (hitClip.z / hitClip.w) * 0.5 + 0.5;
                } else {
                    gl_FragDepth = 1.0;
                }
            }
        }
        """;

    private BlackHoleShader() {
    }

    /**
     * Попытка компиляции шейдерной программы и создания полноэкранного VAO.
     */
    public static boolean init() {
        if (initialized) {
            return true;
        }
        if (initFailed) {
            return false;
        }

        try {
            int vert = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER_SRC);
            int frag = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC);

            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vert);
            GL20.glAttachShader(programId, frag);
            GL20.glLinkProgram(programId);

            int linkStatus = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS);
            if (linkStatus == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(programId);
                LOGGER.error("[Gonzo Tech] Ошибка линковки шейдера Чёрной Дыры: {}", log);
                GL20.glDeleteProgram(programId);
                programId = 0;
                initFailed = true;
                return false;
            }

            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);

            // Получаем локации юниформов
            uCamPosLoc = GL20.glGetUniformLocation(programId, "u_camPos");
            uInvViewMatLoc = GL20.glGetUniformLocation(programId, "u_invViewMat");
            uViewMatLoc = GL20.glGetUniformLocation(programId, "u_viewMat");
            uProjMatrixLoc = GL20.glGetUniformLocation(programId, "u_projMatrix");
            uTanFovLoc = GL20.glGetUniformLocation(programId, "u_tanFov");
            uRadiusLoc = GL20.glGetUniformLocation(programId, "u_radius");
            uTimeLoc = GL20.glGetUniformLocation(programId, "u_time");
            uDiskNormalLoc = GL20.glGetUniformLocation(programId, "u_diskNormal");

            // Создаём VAO и VBO для 2 треугольников (полноэкранный квад)
            float[] quadVertices = {
                -1.0F, -1.0F,
                 1.0F, -1.0F,
                 1.0F,  1.0F,
                -1.0F, -1.0F,
                 1.0F,  1.0F,
                -1.0F,  1.0F
            };

            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();

            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

            FloatBuffer buf = BufferUtils.createFloatBuffer(quadVertices.length);
            buf.put(quadVertices).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);

            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

            initialized = true;
<<<<<<< HEAD
            LOGGER.info("[Gonzo Tech] Шейдер гравитационного линзирования Чёрной Дыры успешно инициализирован!");
=======
            LOGGER.info("[Gonzo Tech] Релятивистский шейдер Чёрной Дыры успешно инициализирован!");
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
            return true;
        } catch (Exception e) {
            LOGGER.error("[Gonzo Tech] Исключение при инициализации шейдера Чёрной Дыры", e);
            initFailed = true;
            return false;
        }
    }

    private static int compileShader(int type, String src) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        int status = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (status == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed: " + log);
        }
        return shader;
    }

    /**
<<<<<<< HEAD
     * Отрисовка Чёрной Дыры с шейдерным линзированием и аккреционным диском.
=======
     * Отрисовка Чёрной Дыры с релятивистским геодезическим линзированием.
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
     */
    public static void render(Vec3 camPos, Matrix4f modelViewMatrix,
                              Matrix4f projectionMatrix, float radius) {
        if (!init()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();

        GL20.glUseProgram(programId);

        // 1. Относительная позиция камеры к центру ЧД (0, 160, 0)
        float rx = (float) (camPos.x - BlackHoleRenderer.CENTER_X);
        float ry = (float) (camPos.y - BlackHoleRenderer.CENTER_Y);
        float rz = (float) (camPos.z - BlackHoleRenderer.CENTER_Z);
        GL20.glUniform3f(uCamPosLoc, rx, ry, rz);

        // 2. Обратная матрица поворота вида
        Matrix4f invView = new Matrix4f(modelViewMatrix).invert();
        uploadMatrix(uInvViewMatLoc, invView);

        // 3. Прямая матрица вида
        uploadMatrix(uViewMatLoc, modelViewMatrix);

        // 4. Матрица проекции
        uploadMatrix(uProjMatrixLoc, projectionMatrix);

        // 5. Половина тангенса FOV по X и Y
        float tanFovX = 1.0F / projectionMatrix.m00();
        float tanFovY = 1.0F / projectionMatrix.m11();
        GL20.glUniform2f(uTanFovLoc, tanFovX, tanFovY);

        // 6. Радиус Шварцшильда
        GL20.glUniform1f(uRadiusLoc, radius);

<<<<<<< HEAD
        // 7. Время анимации плазмы (плавное наносекундное время)
=======
        // 7. Плавное время анимации плазмы
>>>>>>> 20ce60c (Космос: устранение паразитных клонов, релятивистский рендер Гаргантюа (Интерстеллар) без разрывов и двойных кайм + орбитальные партиклы аккреционного диска)
        float time = (float) ((System.nanoTime() / 1_000_000L) % 100_000_000L) * 0.001F;
        GL20.glUniform1f(uTimeLoc, time);

        // 8. Нормаль диска
        GL20.glUniform3f(uDiskNormalLoc, DISK_NORMAL.x(), DISK_NORMAL.y(), DISK_NORMAL.z());

        // Отрисовка полноэкранного квада
        GL30.glBindVertexArray(vaoId);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);

        RenderSystem.enableCull();
    }

    private static void uploadMatrix(int location, Matrix4f mat) {
        if (location == -1) return;
        mat.get(MAT_ARRAY);
        MAT_BUFFER.clear();
        MAT_BUFFER.put(MAT_ARRAY).flip();
        GL20.glUniformMatrix4fv(location, false, MAT_BUFFER);
    }
}

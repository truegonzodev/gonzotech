package com.gonzotech.space.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
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
 * Релятивистский GPU-шейдер Чёрных Дыр (Гаргантюа / Интерстеллар).
 *
 * <p>Точные параметры измерений:
 * <ul>
 *   <li><b>yx989_k2:</b>
 *     <ul>
 *       <li>Горизонт событий: {@code 120} блоков</li>
 *       <li>Фотонное кольцо: {@code 132 - 140} блоков</li>
 *       <li>ISCO-зазор: {@code 120 - 135} блоков</li>
 *       <li>Аккреционный диск: {@code 138 - 500} блоков</li>
 *     </ul>
 *   </li>
 *   <li><b>zangler_11:</b>
 *     <ul>
 *       <li>Горизонт событий: {@code 200} блоков</li>
 *       <li>Фотонное кольцо: {@code 220 - 228} блоков</li>
 *       <li>ISCO-зазор: {@code 200 - 223} блоков</li>
 *       <li>Аккреционный диск: {@code 225 - 1200} блоков</li>
 *     </ul>
 *   </li>
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
    private static int uRingMinLoc = -1;
    private static int uRingMaxLoc = -1;
    private static int uDiskInLoc = -1;
    private static int uDiskOutLoc = -1;
    private static int uTimeLoc = -1;
    private static int uDiskNormalLoc = -1;

    // Вспомогательные буферы для загрузки матриц
    private static final float[] MAT_ARRAY = new float[16];
    private static final FloatBuffer MAT_BUFFER = BufferUtils.createFloatBuffer(16);

    /** Нормаль плоскости аккреционного диска (наклон ~12° для зрелищного 3D-вида). */
    public static final Vector3f DISK_NORMAL = new Vector3f(0.12F, 0.98F, 0.15F).normalize();

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
        uniform float u_ringMin;
        uniform float u_ringMax;
        uniform float u_diskIn;
        uniform float u_diskOut;
        uniform float u_time;
        uniform vec3 u_diskNormal;

        // Быстрый 2D Хэш
        float hash(vec2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        // Быстрый 2D Шум
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

        // Быстрая 2-октавная турбулентность плазмы
        float fbm(vec2 p) {
            float v = noise(p) * 0.65;
            vec2 p2 = mat2(0.8, 0.6, -0.6, 0.8) * p * 2.05 + vec2(1.2, 2.8);
            v += noise(p2) * 0.35;
            return v;
        }

        // Сэмплирование аккреционного диска с эффектом Доплера и спиралями плазмы
        vec4 sampleAccretionDisk(vec3 pos, float r, vec3 viewRay, float rs, float time, vec3 n, float Rin, float Rout) {
            float u = (r - Rin) / (Rout - Rin);
            if (u < 0.0 || u > 1.0) return vec4(0.0);

            // Радиальный профиль свечения
            float radial = pow(u, 0.28) * pow(1.0 - u, 1.5) * 4.2;

            // Базис в плоскости диска
            vec3 tangentX = normalize(abs(n.y) < 0.99 ? cross(vec3(0.0, 1.0, 0.0), n) : cross(vec3(1.0, 0.0, 0.0), n));
            vec3 tangentZ = cross(n, tangentX);
            float phi = atan(dot(pos, tangentZ), dot(pos, tangentX));

            // Кеплеровское дифференциальное вращение Omega ~ r^-1.5
            float omega = 1.6 * pow(Rin / r, 1.5);
            float phi_rot = phi - omega * time * 0.45;

            // Логарифмические спирали плазмы
            float spiral = phi_rot + 3.0 * log(r / Rin);
            vec2 noiseUV = vec2(r / rs * 0.45, spiral * 1.3);
            float plasma = fbm(noiseUV);

            // Релятивистский эффект Доплера (Doppler Beaming)
            vec3 v_orb = normalize(cross(n, pos));
            float speed_frac = 0.50 * sqrt(Rin / r);
            float beta_parallel = dot(v_orb, -viewRay) * speed_frac;
            float gamma = 1.0 / sqrt(max(0.01, 1.0 - speed_frac * speed_frac));
            float doppler = 1.0 / (gamma * (1.0 - beta_parallel));
            float beaming = pow(doppler, 3.2);

            // Температурный спектр (от раскалённого бело-золотого к багрово-янтарному)
            float temp = (1.0 - u * 0.75) * doppler * (0.85 + 0.35 * plasma);

            vec3 colCool = vec3(0.70, 0.16, 0.02); // Багрово-янтарный
            vec3 colWarm = vec3(1.00, 0.58, 0.12); // Золотисто-оранжевый
            vec3 colHot  = vec3(1.00, 0.92, 0.75); // Бело-золотой
            vec3 colBlue = vec3(0.88, 0.94, 1.00); // Релятивистский синий сдвиг

            vec3 col = mix(colCool, colWarm, clamp(temp * 1.3, 0.0, 1.0));
            col = mix(col, colHot, clamp((temp - 0.75) * 2.2, 0.0, 1.0));
            if (temp > 1.35) {
                col = mix(col, colBlue, clamp((temp - 1.35) * 1.8, 0.0, 1.0));
            }

            float density = radial * (0.45 + 0.55 * plasma) * beaming * 1.6;
            float alpha = clamp(density * 0.85, 0.0, 0.95);

            return vec4(col * density, alpha);
        }

        void main() {
            // Точный мировой луч камеры
            vec3 rayCam = normalize(vec3(v_uv.x * u_tanFov.x, v_uv.y * u_tanFov.y, -1.0));
            vec3 rayDir = normalize((u_invViewMat * vec4(rayCam, 0.0)).xyz);

            float D = length(u_camPos);
            float rs = u_radius;

            // Нахождение внутри горизонта событий
            if (D < rs) {
                float cosExit = sqrt(max(0.0, 1.0 - D / rs));
                vec3 toCenter = -u_camPos / max(0.001, D);
                if (dot(rayDir, toCenter) < cosExit) {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    gl_FragDepth = 0.9999;
                    return;
                }
            }

            vec3 u_bh = -u_camPos / max(0.001, D);
            float cosTheta = dot(rayDir, u_bh);
            float sinThetaSq = max(0.0, 1.0 - cosTheta * cosTheta);
            float sinTheta = sqrt(sinThetaSq);
            float b = D * sinTheta; // Прицельный параметр луча

            vec3 n = normalize(u_diskNormal);

            // 1. БЫСТРЫЙ ВЫХОД ДЛЯ ПИКСЕЛЕЙ ВНЕ ЗОНЫ ЧД И ДИСКА (ускорение до 160+ FPS)
            if (D > u_diskOut && (cosTheta < 0.0 || b > u_diskOut * 1.25)) {
                float denom = dot(rayDir, n);
                if (abs(denom) > 0.0001) {
                    float t = -dot(u_camPos, n) / denom;
                    if (t > 0.0) {
                        vec3 hit = u_camPos + t * rayDir;
                        float r = length(hit);
                        if (r >= u_diskIn && r <= u_diskOut) {
                            vec4 sample = sampleAccretionDisk(hit, r, rayDir, rs, u_time, n, u_diskIn, u_diskOut);
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

            // 2. ЧИСЛЕННОЕ РЕЛЯТИВИСТСКОЕ ИНТЕГРИРОВАНИЕ ГЕОДЕЗИЧЕСКИХ (Шварцшильд / Гаргантюа)
            vec3 x = u_camPos;
            vec3 v = rayDir;
            vec3 L = cross(x, v);
            float L2 = dot(L, L);

            vec3 accumColor = vec3(0.0);
            float accumAlpha = 0.0;
            float firstDiskDist = -1.0;
            bool hitHorizon = false;
            vec3 horizonHitPos = vec3(0.0);
            float r_min = 1e9;

            const int MAX_STEPS = 24; // Оптимизировано для высокого FPS

            for (int i = 0; i < MAX_STEPS; i++) {
                float r = length(x);
                if (r < r_min) r_min = r;

                // 1. Фотон поглощен горизонтом событий
                if (r <= rs * 1.005) {
                    hitHorizon = true;
                    horizonHitPos = x;
                    break;
                }

                // 2. Фотон ушел за пределы аккреционного диска
                if (r > u_diskOut * 1.25 && dot(x, v) > 0.0) {
                    break;
                }

                // Адаптивный шаг интегрирования
                float ds = clamp(r * 0.22, rs * 0.08, rs * 0.45);

                // Релятивистское ускорение фотона: a = -1.5 * rs * L^2 / r^5 * x
                float r5 = r * r * r * r * r;
                vec3 a = (-1.5 * rs * L2 / max(1e-5, r5)) * x;

                // Шаг позиции (Verlet)
                vec3 x_next = x + v * ds + 0.5 * a * ds * ds;
                float r_next = length(x_next);
                if (r_next < r_min) r_min = r_next;

                // Ускорение в следующей точке
                float r5_next = r_next * r_next * r_next * r_next * r_next;
                vec3 a_next = (-1.5 * rs * L2 / max(1e-5, r5_next)) * x_next;

                // Шаг скорости
                vec3 v_next = normalize(v + 0.5 * (a + a_next) * ds);

                // 3. Проверка пересечения плоскости аккреционного диска
                float h1 = dot(x, n);
                float h2 = dot(x_next, n);

                if (h1 * h2 <= 0.0) {
                    float tau = abs(h1) / (abs(h1) + abs(h2) + 1e-7);
                    vec3 x_cross = mix(x, x_next, tau);
                    float r_cross = length(x_cross);

                    if (r_cross >= u_diskIn && r_cross <= u_diskOut) {
                        vec4 diskSample = sampleAccretionDisk(x_cross, r_cross, v_next, rs, u_time, n, u_diskIn, u_diskOut);
                        if (diskSample.a > 0.005) {
                            accumColor += (1.0 - accumAlpha) * diskSample.rgb;
                            accumAlpha += (1.0 - accumAlpha) * diskSample.a;

                            if (firstDiskDist < 0.0) {
                                firstDiskDist = length(x_cross - u_camPos);
                            }

                            if (accumAlpha > 0.95) {
                                break;
                            }
                        }
                    }
                }

                x = x_next;
                v = v_next;
            }

            // 3. БЕЛОЕ ФОТОННОЕ КОЛЬЦО (Photon ring) по точным границам u_ringMin .. u_ringMax
            vec3 whitePhotonRing = vec3(0.0);
            if (!hitHorizon && r_min >= rs) {
                float rMid = 0.5 * (u_ringMin + u_ringMax);
                float rHalf = 0.5 * (u_ringMax - u_ringMin) + 2.0;
                float dist = abs(r_min - rMid) / rHalf;
                if (dist < 2.5) {
                    float intensity = exp(-dist * dist * 2.5) * 5.5;
                    whitePhotonRing = vec3(1.0, 0.98, 0.95) * intensity;
                }
            }

            // 4. ИТОГОВАЯ КОМПОЗИЦИЯ
            if (hitHorizon) {
                vec3 finalRgb = accumColor + whitePhotonRing;
                fragColor = vec4(finalRgb, 1.0);

                vec3 hitCam = horizonHitPos - u_camPos;
                vec4 hitClip = u_projMatrix * (u_viewMat * vec4(hitCam, 0.0));
                gl_FragDepth = (hitClip.z / hitClip.w) * 0.5 + 0.5;
            } else {
                vec3 finalRgb = accumColor + whitePhotonRing;
                float finalAlpha = clamp(accumAlpha + (length(whitePhotonRing) > 0.05 ? 1.0 : 0.0), 0.0, 1.0);
                fragColor = vec4(finalRgb, finalAlpha);

                if (firstDiskDist > 0.0 && accumAlpha > 0.25) {
                    vec3 hitCam = firstDiskDist * rayDir;
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
     * Инициализация шейдерной программы и VAO.
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
            uRingMinLoc = GL20.glGetUniformLocation(programId, "u_ringMin");
            uRingMaxLoc = GL20.glGetUniformLocation(programId, "u_ringMax");
            uDiskInLoc = GL20.glGetUniformLocation(programId, "u_diskIn");
            uDiskOutLoc = GL20.glGetUniformLocation(programId, "u_diskOut");
            uTimeLoc = GL20.glGetUniformLocation(programId, "u_time");
            uDiskNormalLoc = GL20.glGetUniformLocation(programId, "u_diskNormal");

            // Создаём VAO и VBO для полноэкранного квада
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
            LOGGER.info("[Gonzo Tech] Релятивистский шейдер Чёрной Дыры успешно инициализирован!");
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
     * Отрисовка Чёрной Дыры с точными числовыми параметрами диска и фотонного кольца.
     */
    public static void render(Vec3 camPos, Matrix4f modelViewMatrix,
                              Matrix4f projectionMatrix, float radius,
                              float ringMin, float ringMax,
                              float diskIn, float diskOut) {
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

        // 1. Позиция камеры относительно центра ЧД (0, 160, 0)
        float rx = (float) (camPos.x - BlackHoleRenderer.CENTER_X);
        float ry = (float) (camPos.y - BlackHoleRenderer.CENTER_Y);
        float rz = (float) (camPos.z - BlackHoleRenderer.CENTER_Z);
        GL20.glUniform3f(uCamPosLoc, rx, ry, rz);

        // 2. Обратная матрица вида камеры
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

        // 6. Точные числовые параметры геометрии ЧД
        GL20.glUniform1f(uRadiusLoc, radius);
        GL20.glUniform1f(uRingMinLoc, ringMin);
        GL20.glUniform1f(uRingMaxLoc, ringMax);
        GL20.glUniform1f(uDiskInLoc, diskIn);
        GL20.glUniform1f(uDiskOutLoc, diskOut);

        // 7. Плавное время анимации плазмы
        float time = (float) ((System.nanoTime() / 1_000_000L) % 100_000_000L) * 0.001F;
        GL20.glUniform1f(uTimeLoc, time);

        // 8. Нормаль плоскости диска
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

package com.gonzotech.sunevent.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Суневеты фаза 3 — клиентский снег вместо дождя (только Оверворлд).
 *
 * <p><b>Честный клон ванильного {@code WeatherEffectRenderer} (ветка SNOW,
 * 1.21.4)</b> — автор 2026-09-18: не делать «свой снег», скопировать ваниль
 * (2D-панели): там и FPS/TPS норм, и плотное равномерное заполнение
 * (отдельные частицы не «мешают» — это панели, не мировые объекты), и
 * правильный тинт (серые ночью, белые днём — через lightmap), и горизонтальная
 * дымка от ванильного тумана. Свои версии (билборд-снежинки) удалены: были
 * слишком белые («светятся»), микрофризы, «пласт» над игроком.
 *
 * <p>Единственное отличие от ванили: гейт биома ({@code getPrecipitationAt})
 * заменён на наше окно E−1..E+1 + идёт дождь — снег во ВСЕХ биомах. Геометрия,
 * формулы uOffset/vOffset/postrait/seed/light/alpha, вертикальная привязка
 * текстуры к миру (v = bottomY*0.25 + vOffset — поэтому вертикальное движение
 * игрока не таскает «пласт»; кокон следует за игроком только по XZ) —
 * ванильные, один в один. Количество verticles/heightmap-лук-apов = ванильное.
 *
 * <p>Гейт рисует в тот же {@code RenderBuffers.bufferSource} через
 * {@code RenderType.weather} — ванильный endBatch weather-pass'а его
 * flush'ит (как ванильные колонны).
 */
public final class SunEventSnowRenderer {

    private static final ResourceLocation SNOW_LOCATION =
        ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

    // Ванильные: радиус кокона (fancy=10 / fast=5), таблица ориентации панели 32×32.
    private static final float[] COLUMN_SIZE_X = new float[1024];
    private static final float[] COLUMN_SIZE_Z = new float[1024];

    static {
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float f = j - 16;
                float f1 = i - 16;
                float len = Mth.length(f, f1);
                COLUMN_SIZE_X[i * 32 + j] = -f1 / len;
                COLUMN_SIZE_Z[i * 32 + j] = f / len;
            }
        }
    }

    private SunEventSnowRenderer() {
    }

    /** Кадр снега: ванильный кокон из колонных панелей, гейт = наше окно. */
    public static void render(ClientLevel level, int ticks, float partialTick,
                              double camX, double camY, double camZ) {
        float rainLevel = level.getRainLevel(partialTick);
        if (rainLevel <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;

        int ix = Mth.floor(camX);
        int iy = Mth.floor(camY);
        int iz = Mth.floor(camZ);

        // Тот же буфер, в который ваниль рисует свою погоду; flush — у ванильного
        // weather-pass'а после world border (мы внутри того же pass'а).
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer =
            buffers.getBuffer(RenderType.weather(SNOW_LOCATION, Minecraft.useShaderTransparency()));

        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float t = ticks + partialTick;
        float vScroll = -((ticks & 511) + partialTick) / 512.0F;

        for (int z = iz - radius; z <= iz + radius; z++) {
            for (int x = ix - radius; x <= ix + radius; x++) {
                if (!level.hasChunk(x, z)) {
                    continue; // ваниль: getPrecipitationAt = NONE для незагруженных
                }
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(iy - radius, ground);
                int top = Math.max(iy + radius, ground);
                if (top - bottom == 0) {
                    continue;
                }
                // createSnowColumnInstance (ваниль, verbatim)
                int seed = x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761;
                random.setSeed(seed);
                float uOffset = (float) (random.nextDouble() + t * 0.01F * (float) random.nextGaussian());
                float vOffset = vScroll + (float) (random.nextDouble() + t * (float) random.nextGaussian() * 0.001F);
                int lightAt = LevelRenderer.getLightColor(level, pos.set(x, Math.max(iy, ground), z));
                int light = LightTexture.pack(
                    (LightTexture.block(lightAt) * 3 + 15) / 4,
                    (LightTexture.sky(lightAt) * 3 + 15) / 4);

                // renderInstances (ваниль, verbatim; снежная альфа-база 0.8F)
                float fx = (float) (x + 0.5 - camX);
                float fz = (float) (z + 0.5 - camZ);
                float alpha = Mth.lerp((float) Mth.lengthSquared(fx, fz) / (radius * radius), 0.8F, 0.5F)
                    * rainLevel;
                int color = ARGB.white(alpha);
                int idx = (z - iz + 16) * 32 + (x - ix + 16);
                float ox = COLUMN_SIZE_X[idx] / 2.0F;
                float oz = COLUMN_SIZE_Z[idx] / 2.0F;
                float x0 = fx - ox, x1 = fx + ox;
                float yTop = (float) (top - camY);
                float yBottom = (float) (bottom - camY);
                float z0 = fz - oz, z1 = fz + oz;
                float u1 = uOffset + 1.0F;
                float vt = top * 0.25F + vOffset;
                float vb = bottom * 0.25F + vOffset;
                consumer.addVertex(x0, yTop, z0).setUv(uOffset, vb).setColor(color).setLight(light);
                consumer.addVertex(x1, yTop, z1).setUv(u1, vb).setColor(color).setLight(light);
                consumer.addVertex(x1, yBottom, z1).setUv(u1, vt).setColor(color).setLight(light);
                consumer.addVertex(x0, yBottom, z0).setUv(uOffset, vt).setColor(color).setLight(light);
            }
        }
    }
}

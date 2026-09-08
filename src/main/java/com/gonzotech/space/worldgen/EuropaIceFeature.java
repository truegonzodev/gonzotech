package com.gonzotech.space.worldgen;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Фаза 4 — «подводная жизнь» ледяного океана Европы (переработка по фидбэку #5):
 * <ol>
 *   <li>ВИСЯЧИЕ ЛЕДЯНЫЕ ГОРЫ — с нижней кромки корки («подкорки») в воду свисают
 *       хаотичные сосульки-«горы» разной толщины/длины с неровным, бугристым
 *       профилем (не гладкие конусы), разбавленные ванильным packed/blue ice.</li>
 *   <li>ПЛАВАЮЩИЕ ГЛЫБЫ — от мелких (d~4) до огромных (d~50) КУСКОВ ПРИЧУДЛИВОЙ
 *       формы: слепленные из нескольких долей-«лопастей» (metaball-объединение) с
 *       шумовой шероховатостью краёв — НЕ эллипсоиды. Крупные бывают пористыми.</li>
 * </ol>
 *
 * <p>Работает по чанку в шаге {@code underground_decoration}. Заменяются только
 * вода/воздух — саму корку и бедрок не трогаем. Сверхплотный лёд НЕ используется
 * (он только на дне океана, ставится surface-rule); глыбы/сосульки — из
 * европианского/packed/blue/обычного льда.
 *
 * <p>ВСЕ записи ограничены безопасной зоной 3×3 чанка вокруг origin (как в
 * {@link CraterFeature}) — иначе крупные глыбы писали бы в дальние чанки и
 * спамили «setBlock in a far chunk».
 */
public class EuropaIceFeature extends Feature<NoneFeatureConfiguration> {

    /** Уровень океана Европы (см. noise_settings sea_level). */
    private static final int SEA_LEVEL = 230;
    /** Шанс на чанк вырастить группу сосулек-гор с подкорки. */
    private static final float STALACTITE_CHUNK_CHANCE = 0.9F;
    /** Шанс на чанк раскидать плавающие глыбы. */
    private static final float BLOB_CHUNK_CHANCE = 0.7F;

    public EuropaIceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Границы безопасной зоны записи (3×3 чанка вокруг генерируемого чанка). */
    private int safeMinX, safeMaxX, safeMinZ, safeMaxZ;

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int chunkMinX = (origin.getX() >> 4) << 4;
        int chunkMinZ = (origin.getZ() >> 4) << 4;
        safeMinX = chunkMinX - 16;
        safeMaxX = chunkMinX + 31;
        safeMinZ = chunkMinZ - 16;
        safeMaxZ = chunkMinZ + 31;

        // 1) Висячие ледяные горы с нижней кромки корки.
        if (random.nextFloat() < STALACTITE_CHUNK_CHANCE) {
            int count = 3 + random.nextInt(5); // 3..7 сосулек на чанк
            for (int i = 0; i < count; i++) {
                int x = origin.getX() + random.nextInt(16);
                int z = origin.getZ() + random.nextInt(16);
                int crustBottom = findCrustBottom(level, x, z);
                if (crustBottom != Integer.MIN_VALUE) {
                    growStalactite(level, random, x, crustBottom, z);
                }
            }
        }

        // 2) Плавающие глыбы в водной толще.
        if (random.nextFloat() < BLOB_CHUNK_CHANCE) {
            int count = 1 + random.nextInt(3); // 1..3 глыбы на чанк
            for (int i = 0; i < count; i++) {
                // Радиус глыбы: в основном мелкие/средние, изредка ОГРОМНЫЕ (до d~50).
                int maxR;
                float roll = random.nextFloat();
                if (roll < 0.60F) {
                    maxR = 2 + random.nextInt(4);   // мелкие r2..5 (d 4..10)
                } else if (roll < 0.90F) {
                    maxR = 6 + random.nextInt(7);   // средние r6..12 (d 12..24)
                } else {
                    maxR = 13 + random.nextInt(13); // ОГРОМНЫЕ r13..25 (d 26..50)
                }

                // Крупные глыбы центрируем ближе к середине чанка, чтобы safe-зона
                // не срезала их сильно; мелкие можно раскидывать свободнее.
                int margin = Math.min(7, maxR);
                int span = Math.max(1, 16 - 2 * Math.min(6, margin));
                int x = origin.getX() + Math.min(6, margin) + random.nextInt(span);
                int z = origin.getZ() + Math.min(6, margin) + random.nextInt(span);

                // Центр глыбы — в средней/нижней части водного столба, но с запасом
                // maxR от дна и от корки, чтобы висела в воде.
                int floor = level.getMinY() + 8 + maxR;
                int top = SEA_LEVEL - 8 - maxR;
                if (top <= floor + 4) {
                    continue;
                }
                int cy = floor + random.nextInt(top - floor);
                chaoticBlob(level, random, x, cy, z, maxR);
            }
        }
        return true;
    }

    /** true, если запись в (x,z) не выходит за безопасную 3×3-зону чанков. */
    private boolean inSafe(int x, int z) {
        return x >= safeMinX && x <= safeMaxX && z >= safeMinZ && z <= safeMaxZ;
    }

    /**
     * Ищет нижнюю кромку корки: идём сверху (SEA_LEVEL+6) вниз, находим сплошной
     * лёд, затем первую воду/воздух под ним. Возвращает Y последнего блока корки.
     */
    private int findCrustBottom(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Math.min(SEA_LEVEL + 6, level.getMaxY() - 1);
        boolean inCrust = false;
        for (int y = top; y > level.getMinY() + 4; y--) {
            pos.set(x, y, z);
            BlockState st = level.getBlockState(pos);
            if (isIce(st)) {
                inCrust = true;
            } else if (inCrust) {
                return y + 1; // предыдущий y — низ корки
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Свисающая с подкорки сосулька-«гора»: длина 6..50, толщина сужается книзу,
     * но с ХАОТИЧНЫМ бугристым профилем (шумовой множитель радиуса), а не гладкий
     * конус. ~25% — толстые «горы» (широкое основание, большая длина).
     */
    private void growStalactite(WorldGenLevel level, RandomSource random,
                                int x, int crustBottom, int z) {
        boolean mountain = random.nextInt(4) == 0;         // 25% «горы»
        int length = mountain ? 20 + random.nextInt(31)    // 20..50
                              : 6 + random.nextInt(20);     // 6..25
        int baseRadius = mountain ? 3 + random.nextInt(5)  // 3..7
                                  : 1 + random.nextInt(3);  // 1..3
        long jitterSeed = random.nextLong();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int d = 0; d < length; d++) {
            int y = crustBottom - d;
            if (y <= level.getMinY() + 2) {
                break;
            }
            // Профиль: линейное сужение к кончику × хаотичный множитель 0.55..1.25.
            float t = 1.0F - (float) d / length;
            double jitter = 0.55 + hash01(jitterSeed, 0, d, 0) * 0.70;
            int r = Math.max(0, (int) Math.round(baseRadius * t * jitter));
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Круглое сечение + лёгкая шумовая эрозия края.
                    double dd = dx * dx + dz * dz;
                    if (dd > (r + 0.5) * (r + 0.5)) {
                        continue;
                    }
                    if (r >= 2 && dd > (r - 0.5) * (r - 0.5)
                        && hash01(jitterSeed, dx, d, dz) < 0.35) {
                        continue; // выгрызаем часть кромки → неровно
                    }
                    int wx = x + dx, wz = z + dz;
                    if (!inSafe(wx, wz)) {
                        continue;
                    }
                    pos.set(wx, y, wz);
                    if (isWaterOrAir(level.getBlockState(pos))) {
                        level.setBlock(pos, iceMix(random), 2);
                    }
                }
            }
        }
    }

    /**
     * Плавающая глыба ПРИЧУДЛИВОЙ формы (не эллипсоид): объединение нескольких
     * долей-«лопастей» (metaball union) + шумовая шероховатость краёв. Крупные
     * бывают пористыми (пустоты внутри).
     */
    private void chaoticBlob(WorldGenLevel level, RandomSource random,
                             int cx, int cy, int cz, int maxR) {
        int lobes = 3 + random.nextInt(6); // 3..8 долей
        int[] lx = new int[lobes], ly = new int[lobes], lz = new int[lobes];
        double[] lr = new double[lobes];
        for (int i = 0; i < lobes; i++) {
            double spread = 0.55;
            lx[i] = (int) Math.round((random.nextDouble() * 2 - 1) * maxR * spread);
            ly[i] = (int) Math.round((random.nextDouble() * 2 - 1) * maxR * spread * 0.7);
            lz[i] = (int) Math.round((random.nextDouble() * 2 - 1) * maxR * spread);
            lr[i] = maxR * (0.35 + random.nextDouble() * 0.45); // доля 0.35..0.8·R
        }
        boolean porous = maxR >= 7 && random.nextInt(3) == 0;
        long poreSeed = random.nextLong();
        long edgeSeed = random.nextLong();

        int R = maxR + 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    // metaball-union: воксель внутри, если попал хотя бы в одну долю.
                    boolean inside = false;
                    double nearest = 9.9;
                    for (int i = 0; i < lobes; i++) {
                        double ddx = dx - lx[i], ddy = dy - ly[i], ddz = dz - lz[i];
                        double n = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / lr[i];
                        if (n <= 1.0) {
                            inside = true;
                        }
                        nearest = Math.min(nearest, n);
                    }
                    if (!inside) {
                        continue;
                    }
                    // Шероховатость: у самой кромки (nearest в 0.8..1.0) случайно
                    // выгрызаем воксели → рваная, «причудливая» поверхность.
                    if (nearest > 0.80 && hash01(edgeSeed, dx, dy, dz) < 0.45) {
                        continue;
                    }
                    if (porous && nearest < 0.65 && pore(poreSeed, dx, dy, dz)) {
                        continue;
                    }
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (!inSafe(x, z)) {
                        continue;
                    }
                    if (y <= level.getMinY() + 2 || y >= level.getMaxY() - 1) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (isWaterOrAir(level.getBlockState(pos))) {
                        level.setBlock(pos, iceMix(random), 2);
                    }
                }
            }
        }
    }

    /** Детерминированная «пористость»: ~30% вокселей внутри становятся пустотами. */
    private boolean pore(long seed, int dx, int dy, int dz) {
        long h = seed;
        h = h * 6364136223846793005L + (dx * 341873128712L);
        h = h * 6364136223846793005L + (dy * 132897987541L);
        h = h * 6364136223846793005L + (dz * 1274126177L);
        return ((h >>> 33) % 10) < 3;
    }

    /** Детерминированный хеш-шум в [0,1) от (seed, x, y, z). */
    private double hash01(long seed, int x, int y, int z) {
        long h = seed;
        h = h * 6364136223846793005L + (x * 341873128712L);
        h = h * 6364136223846793005L + (y * 132897987541L);
        h = h * 6364136223846793005L + (z * 1274126177L);
        h ^= (h >>> 29);
        return ((h >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) 0x20000000000000L;
    }

    /**
     * Смесь льдов для глыб/сосулек: европианский (наш) + ванильные packed/blue/ice.
     * СВЕРХПЛОТНЫЙ ЛЁД НЕ ВКЛЮЧАЕМ — он только на дне океана (по требованию).
     */
    private BlockState iceMix(RandomSource random) {
        int r = random.nextInt(100);
        if (r < 45) {
            return ModBlocks.EUROPAN_ICE.get().defaultBlockState();
        } else if (r < 75) {
            return Blocks.PACKED_ICE.defaultBlockState();
        } else if (r < 92) {
            return Blocks.BLUE_ICE.defaultBlockState();
        } else {
            return Blocks.ICE.defaultBlockState();
        }
    }

    private boolean isIce(BlockState st) {
        return st.is(ModBlocks.EUROPAN_ICE.get())
            || st.is(ModBlocks.SUPERDENSE_ICE.get())
            || st.is(Blocks.PACKED_ICE)
            || st.is(Blocks.BLUE_ICE)
            || st.is(Blocks.ICE);
    }

    private boolean isWaterOrAir(BlockState st) {
        return st.isAir() || st.getFluidState().is(Fluids.WATER) || st.getFluidState().is(Fluids.FLOWING_WATER);
    }
}

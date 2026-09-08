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
 * Фаза 4 — «подводная жизнь» ледяного океана Европы (по фидбэку):
 * <ol>
 *   <li>ВИСЯЧИЕ ЛЕДЯНЫЕ ГОРЫ — с нижней поверхности корки в воду свисают
 *       сталактиты/«кишки» изо льда (конусы разной толщины/длины), разбавленные
 *       ванильным packed_ice/blue_ice + европианским/плотным льдом.</li>
 *   <li>ПЛАВАЮЩИЕ ГЛЫБЫ — большие монолитные и пористые (с пустотами) куски льда
 *       посреди водной толщи.</li>
 * </ol>
 *
 * <p>Работает по чанку в шаге {@code underground_decoration}: ищет нижнюю кромку
 * корки (первый воздух/вода под сплошным льдом сверху) и от неё выращивает
 * сталактиты вниз; отдельно раскидывает плавающие глыбы в средней части водного
 * столба. Заменяются только вода/воздух — саму корку и бедрок не трогаем.
 */
public class EuropaIceFeature extends Feature<NoneFeatureConfiguration> {

    /** Уровень океана Европы (см. noise_settings sea_level). */
    private static final int SEA_LEVEL = 230;
    /** Шанс на чанк вырастить группу сталактитов. */
    private static final float STALACTITE_CHUNK_CHANCE = 0.9F;
    /** Шанс на чанк раскидать плавающие глыбы. */
    private static final float BLOB_CHUNK_CHANCE = 0.6F;

    public EuropaIceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        // 1) Висячие ледяные горы с нижней кромки корки.
        if (random.nextFloat() < STALACTITE_CHUNK_CHANCE) {
            int count = 3 + random.nextInt(5); // 3..7 сталактитов на чанк
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
                int x = origin.getX() + 2 + random.nextInt(12);
                int z = origin.getZ() + 2 + random.nextInt(12);
                // Центр глыбы — в средней/нижней части водного столба.
                int floor = level.getMinY() + 8;
                int top = SEA_LEVEL - 20;
                if (top <= floor + 10) {
                    continue;
                }
                int cy = floor + random.nextInt(top - floor);
                floatingBlob(level, random, x, cy, z);
            }
        }
        return true;
    }

    /**
     * Ищет нижнюю кромку корки: идём сверху (SEA_LEVEL+4) вниз, находим сплошной
     * лёд, затем первую воду/воздух под ним. Возвращает Y последнего блока корки
     * (с которого свисает сталактит) или MIN_VALUE.
     */
    private int findCrustBottom(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Math.min(SEA_LEVEL + 6, level.getMaxY() - 1);
        boolean inCrust = false;
        for (int y = top; y > level.getMinY() + 4; y--) {
            pos.set(x, y, z);
            BlockState st = level.getBlockState(pos);
            boolean solid = isIce(st);
            if (solid) {
                inCrust = true;
            } else if (inCrust) {
                // нашли пустоту под коркой → предыдущий y был низом корки
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Свисающий сталактит-«гора»: конус, сужающийся книзу, длиной 6..40. */
    private void growStalactite(WorldGenLevel level, RandomSource random,
                                int x, int crustBottom, int z) {
        int length = 6 + random.nextInt(35);          // 6..40
        int baseRadius = 1 + random.nextInt(3);       // 1..3
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int d = 0; d < length; d++) {
            int y = crustBottom - d;
            if (y <= level.getMinY() + 2) {
                break;
            }
            // Радиус линейно сужается к кончику.
            float t = 1.0F - (float) d / length;
            int r = Math.max(0, Math.round(baseRadius * t));
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r + 1) {
                        continue;
                    }
                    pos.set(x + dx, y, z + dz);
                    if (isWaterOrAir(level.getBlockState(pos))) {
                        level.setBlock(pos, iceMix(random), 2);
                    }
                }
            }
        }
    }

    /** Плавающая глыба: эллипсоид льда, у «пористых» вырезаем пустоты. */
    private void floatingBlob(WorldGenLevel level, RandomSource random, int cx, int cy, int cz) {
        int rx = 3 + random.nextInt(6); // 3..8
        int ry = 3 + random.nextInt(5); // 3..7
        int rz = 3 + random.nextInt(6);
        boolean porous = random.nextBoolean();
        long poreSeed = random.nextLong();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    double e = (double) (dx * dx) / (rx * rx)
                        + (double) (dy * dy) / (ry * ry)
                        + (double) (dz * dz) / (rz * rz);
                    if (e > 1.0) {
                        continue;
                    }
                    // Пористость: псевдослучайные пустоты внутри тела глыбы.
                    if (porous && e < 0.7 && pore(poreSeed, dx, dy, dz)) {
                        continue;
                    }
                    int x = cx + dx, y = cy + dy, z = cz + dz;
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

    /** Смесь льдов: европианский/плотный (наши) + ванильные packed/blue ice. */
    private BlockState iceMix(RandomSource random) {
        int r = random.nextInt(100);
        if (r < 35) {
            return ModBlocks.EUROPAN_ICE.get().defaultBlockState();
        } else if (r < 60) {
            return ModBlocks.SUPERDENSE_ICE.get().defaultBlockState();
        } else if (r < 82) {
            return Blocks.PACKED_ICE.defaultBlockState();
        } else {
            return Blocks.BLUE_ICE.defaultBlockState();
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

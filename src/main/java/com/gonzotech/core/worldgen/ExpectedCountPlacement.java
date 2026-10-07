package com.gonzotech.core.worldgen;

import com.gonzotech.core.registry.ModPlacementModifiers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Emits an expected, possibly fractional number of feature starts for a chunk.
 * The integer part is guaranteed; the fractional part is independently sampled
 * for each input position. This keeps rates such as 0.35 or 0.88 exact without
 * relying on rarity filters whose probabilities are restricted to 1 / N.
 */
public final class ExpectedCountPlacement extends PlacementModifier {

    public static final MapCodec<ExpectedCountPlacement> CODEC =
            Codec.doubleRange(0.0D, 4096.0D)
                    .fieldOf("count")
                    .xmap(ExpectedCountPlacement::new, ExpectedCountPlacement::count);

    private final double count;

    public ExpectedCountPlacement(double count) {
        this.count = count;
    }

    public double count() {
        return count;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int starts = (int) Math.floor(count);
        if (random.nextDouble() < count - starts) {
            starts++;
        }
        return Stream.generate(() -> pos).limit(starts);
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifiers.EXPECTED_COUNT.get();
    }
}

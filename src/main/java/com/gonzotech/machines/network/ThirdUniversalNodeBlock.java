package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Universal node of the third opening (shielded, stats x0.88). Carries all
 * current resource streams at the second-opening throughput, factor
 * {@value #STAT_FACTOR} applied on top.
 */
public class ThirdUniversalNodeBlock extends UniversalNodeBlock implements ThirdTierPipe {

    @Override
    public double throughputFactor(net.minecraft.world.level.block.state.BlockState state,
                                   PipeType type) {
        return STAT_FACTOR;
    }

    public static final MapCodec<ThirdUniversalNodeBlock> CODEC = simpleCodec(ThirdUniversalNodeBlock::new);

    public ThirdUniversalNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends net.minecraft.world.level.block.RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    public long throughputLimit(BlockState state, PipeType type) {
        return switch (type) {
            case WIRE -> SecondTierDefs.WIRE_THROUGHPUT;
            case HEAT -> SecondTierDefs.HEAT_THROUGHPUT;
            case MASH -> SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT / 2;
            case WATER, STEAM, WORT, DISTILLATE, RECTIFICATE, BOILING_WATER, POISON_POTION,
                 SULFURIC_ACID, ETHYLENE, AMINOBLAZEETHANOL, FORMALDEHYDE ->
                SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
            case ITEM -> SecondTierDefs.ITEM_THROUGHPUT;
        };
    }

    @Override
    public int perItemThroughputLimit(BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }

    @Override
    public long sharedFluidThroughputLimit(BlockState state) {
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }
}

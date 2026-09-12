package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Universal node II. It carries all current resource streams and applies the
 * same 0.9 factor as the first universal node to the level-II base limits.
 */
public final class SecondUniversalNodeBlock extends UniversalNodeBlock implements SecondTierPipe {

    public static final MapCodec<SecondUniversalNodeBlock> CODEC = simpleCodec(SecondUniversalNodeBlock::new);

    public SecondUniversalNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    public long throughputLimit(BlockState state, PipeType type) {
        return switch (type) {
            case WIRE -> SecondTierDefs.WIRE_THROUGHPUT;
            case HEAT -> SecondTierDefs.HEAT_THROUGHPUT;
            // Water/Steam are one universal-fluid stream, not two 1900 mB/t streams.
            case WATER, STEAM -> SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
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

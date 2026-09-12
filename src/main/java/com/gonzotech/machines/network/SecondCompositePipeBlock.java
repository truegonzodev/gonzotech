package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Internal level-II pipe bundle. It is not an item: it is formed only by
 * combining compatible level-II pipes and drops the original level-II parts.
 */
public final class SecondCompositePipeBlock extends CompositePipeBlock implements SecondTierPipe {

    public static final MapCodec<SecondCompositePipeBlock> CODEC = simpleCodec(SecondCompositePipeBlock::new);

    public SecondCompositePipeBlock(Properties properties) {
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
            // A bundled universal-fluid part shares its 1500 mB/t stream;
            // standalone water/steam parts keep their individual 1900 mB/t rate.
            case WATER -> carriesUniversalFluid(state)
                ? SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT : SecondTierDefs.WATER_THROUGHPUT;
            case STEAM -> carriesUniversalFluid(state)
                ? SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT : SecondTierDefs.STEAM_THROUGHPUT;
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

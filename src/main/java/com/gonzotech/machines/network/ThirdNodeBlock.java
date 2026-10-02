package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;

/** Six-sided one-resource node of the third opening (shielded, stats x0.88). */
public class ThirdNodeBlock extends NodeBlock implements ThirdTierPipe {

    public ThirdNodeBlock(Properties properties, PipeType pipeType) {
        super(properties, pipeType);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        PipeType type = pipeType();
        return simpleCodec(properties -> new ThirdNodeBlock(properties, type));
    }

    @Override
    public long throughputLimit(BlockState state, PipeType type) {
        return switch (type) {
            case WIRE -> SecondTierDefs.WIRE_THROUGHPUT;
            case HEAT -> SecondTierDefs.HEAT_THROUGHPUT;
            case WATER -> SecondTierDefs.WATER_THROUGHPUT;
            case STEAM -> SecondTierDefs.STEAM_THROUGHPUT;
            case ITEM -> SecondTierDefs.ITEM_THROUGHPUT;
            default -> type.maxThroughput();
        };
    }

    @Override
    public double throughputFactor(BlockState state, PipeType type) {
        return STAT_FACTOR;
    }
}

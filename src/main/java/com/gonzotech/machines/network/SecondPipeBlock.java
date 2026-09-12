package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/** One-resource straight pipe of the second opening. */
public final class SecondPipeBlock extends PipeBlock implements SecondTierPipe {

    public SecondPipeBlock(Properties properties, PipeType pipeType) {
        super(properties, pipeType);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        PipeType type = pipeType();
        return simpleCodec(properties -> new SecondPipeBlock(properties, type));
    }

    @Override
    public long throughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return switch (type) {
            case WIRE -> SecondTierDefs.WIRE_THROUGHPUT;
            case HEAT -> SecondTierDefs.HEAT_THROUGHPUT;
            case WATER -> SecondTierDefs.WATER_THROUGHPUT;
            case STEAM -> SecondTierDefs.STEAM_THROUGHPUT;
            case ITEM -> SecondTierDefs.ITEM_THROUGHPUT;
        };
    }
}

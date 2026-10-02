package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/** Item pipe of the third opening (shielded, stats x0.88). */
public class ThirdItemPipeBlock extends ItemPipeBlock implements ThirdTierPipe {

    public ThirdItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ThirdItemPipeBlock::new);
    }

    @Override
    public long throughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_THROUGHPUT;
    }

    @Override
    public int perItemThroughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }

    @Override
    public double throughputFactor(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return STAT_FACTOR;
    }
}

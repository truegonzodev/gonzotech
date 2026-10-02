package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/** Item node of the third opening (shielded, stats x0.88). */
public class ThirdItemNodeBlock extends ItemNodeBlock implements ThirdTierPipe {

    public ThirdItemNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ThirdItemNodeBlock::new);
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

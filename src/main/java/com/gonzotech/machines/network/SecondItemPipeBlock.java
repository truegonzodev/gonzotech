package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;

/** Active straight item pipe II: five item types, two items of each per tick. */
public final class SecondItemPipeBlock extends ItemPipeBlock implements SecondTierPipe {

    public SecondItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(SecondItemPipeBlock::new);
    }

    @Override
    public long throughputLimit(BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_THROUGHPUT;
    }

    @Override
    public int perItemThroughputLimit(BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }
}

package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SingularHeatSourceBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Admin-only, stateless virtual source of GTH. */
public final class SingularHeatSourceBlock extends MachineBlock {

    public static final MapCodec<SingularHeatSourceBlock> CODEC = simpleCodec(SingularHeatSourceBlock::new);

    public SingularHeatSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SingularHeatSourceBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularHeatSourceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SINGULAR_HEAT_SOURCE.get(),
            SingularHeatSourceBlockEntity::serverTick);
    }
}

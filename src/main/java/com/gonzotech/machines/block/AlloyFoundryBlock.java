package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.AlloyFoundryBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Functional second-discovery Alloy Foundry with a 5×5 material grid. */
public final class AlloyFoundryBlock extends MachineBlock {

    public static final MapCodec<AlloyFoundryBlock> CODEC = simpleCodec(AlloyFoundryBlock::new);

    public AlloyFoundryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<AlloyFoundryBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlloyFoundryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.ALLOY_FOUNDRY.get(),
            AlloyFoundryBlockEntity::serverTick);
    }
}

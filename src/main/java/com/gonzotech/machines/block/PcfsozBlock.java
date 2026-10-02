package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.PcfsozBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Центрифуга ПЦФСОЗ — изотопное разделение топливного цикла (0.3.89). */
public class PcfsozBlock extends MachineBlock {

    public static final MapCodec<PcfsozBlock> CODEC = simpleCodec(PcfsozBlock::new);

    public PcfsozBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PcfsozBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PcfsozBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.PCFSOZ.get(), PcfsozBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PcfsozBlockEntity pcfsoz) {
            pcfsoz.dropPendingOutputsForBreak();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

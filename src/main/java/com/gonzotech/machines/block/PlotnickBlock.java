package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.PlotnickBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Плотник (0.3.256, автор) — контроллер сектора автошахтера 3×3 чанка:
 * разметка площадок метками-вымпелами, хранилище (9 активных слотов ×512 +
 * 54 виртуальных стака ×512), позднее — сейсмика, буры и сеть рабочих блоков.
 */
public final class PlotnickBlock extends MachineBlock {

    public static final MapCodec<PlotnickBlock> CODEC = simpleCodec(PlotnickBlock::new);

    public PlotnickBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PlotnickBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlotnickBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.PLOTNICK.get(),
            PlotnickBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof PlotnickBlockEntity plotnick) {
            plotnick.onBroken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

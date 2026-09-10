package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.PumpBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Помпа (водокачка) паровой эры. «Тупая» машина: тратит GTU пассивно и, пока
 * запитана, всасывает воду из окрестности; логика — в {@link PumpBlockEntity}.
 */
public class PumpBlock extends MachineBlock {

    public static final MapCodec<PumpBlock> CODEC = simpleCodec(PumpBlock::new);

    public PumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PumpBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.PUMP.get(), PumpBlockEntity::serverTick);
    }
}

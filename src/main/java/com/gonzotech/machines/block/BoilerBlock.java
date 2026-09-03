package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.BoilerBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Паровой котёл. Задуман как сложный 3D-объект — тут только логика. */
public class BoilerBlock extends MachineBlock {

    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);

    public BoilerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<BoilerBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.BOILER.get(), BoilerBlockEntity::serverTick);
    }
}

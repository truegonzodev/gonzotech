package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.ElectricFurnaceBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Электропечь второго открытия. В текущем балансовом patch повторяет электропечь
 * первого открытия, но хранит данные в собственном зарегистрированном типе BE.
 */
public final class SecondElectricFurnaceBlock extends ElectricFurnaceBlock {

    public static final MapCodec<SecondElectricFurnaceBlock> CODEC = simpleCodec(SecondElectricFurnaceBlock::new);

    public SecondElectricFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondElectricFurnaceBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricFurnaceBlockEntity(ModBlockEntities.SECOND_ELECTRIC_FURNACE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_ELECTRIC_FURNACE.get(),
            ElectricFurnaceBlockEntity::serverTick);
    }
}

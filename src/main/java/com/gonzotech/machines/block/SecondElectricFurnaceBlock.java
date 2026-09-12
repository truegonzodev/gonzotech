package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SecondElectricFurnaceBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Электропечь второго открытия. Использует две независимые линии плавки и собственный баланс второго
 * уровня; первый уровень и его BlockEntity остаются неизменными.
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
        return new SecondElectricFurnaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_ELECTRIC_FURNACE.get(),
            SecondElectricFurnaceBlockEntity::serverTick);
    }
}

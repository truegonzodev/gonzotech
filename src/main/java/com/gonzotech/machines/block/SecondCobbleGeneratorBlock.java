package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SecondCobbleGeneratorBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Генератор булыжника второго открытия. Использует независимую фиксированную 60-тактную логику без слота
 * кирки; генератор первого уровня не изменяется.
 */
public final class SecondCobbleGeneratorBlock extends CobbleGeneratorBlock {

    public static final MapCodec<SecondCobbleGeneratorBlock> CODEC = simpleCodec(SecondCobbleGeneratorBlock::new);

    public SecondCobbleGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondCobbleGeneratorBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecondCobbleGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_COBBLE_GENERATOR.get(),
            SecondCobbleGeneratorBlockEntity::serverTick);
    }
}

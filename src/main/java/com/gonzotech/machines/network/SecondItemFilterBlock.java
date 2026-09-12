package com.gonzotech.machines.network;

import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Фильтр предметов второго открытия. Пока это точная рабочая копия первого
 * фильтра (те же 3 ghost-слота, 5-слотовый буфер и маршрутизация), с отдельным
 * BlockEntityType для будущего независимого баланса.
 */
public final class SecondItemFilterBlock extends ItemFilterBlock {

    public static final MapCodec<SecondItemFilterBlock> CODEC = simpleCodec(SecondItemFilterBlock::new);

    public SecondItemFilterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondItemFilterBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemFilterBlockEntity(ModBlockEntities.SECOND_ITEM_FILTER.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.SECOND_ITEM_FILTER.get(), ItemFilterBlockEntity::serverTick);
    }
}

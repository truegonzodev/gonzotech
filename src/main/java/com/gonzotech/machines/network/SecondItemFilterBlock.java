package com.gonzotech.machines.network;

import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Фильтр предметов второго открытия: 5 ghost-слотов и поток пять типов по
 * две штуки за тик. Буфер и маршрутизация совпадают с первым уровнем.
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
        return new ItemFilterBlockEntity(ModBlockEntities.SECOND_ITEM_FILTER.get(), pos, state,
            SecondTierDefs.ITEM_FILTER_SLOTS);
    }

    @Override
    public int itemThroughputLimit() {
        return (int) SecondTierDefs.ITEM_THROUGHPUT;
    }

    @Override
    public int perItemThroughputLimit() {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.SECOND_ITEM_FILTER.get(), ItemFilterBlockEntity::serverTick);
    }
}

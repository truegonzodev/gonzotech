package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SeismicSourceBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Источник колебаний: нижний блок сейсмической установки. Геофон ставится
 * прямо на него сверху; это соседство двух блоков, а не мультиблок.
 */
public final class SeismicSourceBlock extends MachineBlock {

    public static final MapCodec<SeismicSourceBlock> CODEC = simpleCodec(SeismicSourceBlock::new);

    public SeismicSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SeismicSourceBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeismicSourceBlockEntity(pos, state);
    }

    /**
     * 0.3.299 (N7): серверный тикер — сброс loadSurplus (TNT сверх единицы,
     * поднятое из старого сейва). Пустой путь — одна проверка счётчика.
     */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type,
            com.gonzotech.machines.registry.ModBlockEntities.SEISMIC_SOURCE.get(),
            SeismicSourceBlockEntity::serverTick);
    }
}

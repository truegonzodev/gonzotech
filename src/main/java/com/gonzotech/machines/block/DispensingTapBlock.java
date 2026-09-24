package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.DispensingTapBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Разливной кран: барная стойка / кран для разлива пива и водки.
 * Две шкалы: дистиллят (256 mB) и сусло (1024 mB).
 * Ведро → Ведро пива (1000 mB).
 * Пузырёк → Бутылка водки (128 mB дистиллята) / Кружка пива (128 mB сусла).
 */
public class DispensingTapBlock extends MachineBlock {

    public static final MapCodec<DispensingTapBlock> CODEC = simpleCodec(DispensingTapBlock::new);

    public DispensingTapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DispensingTapBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DispensingTapBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.THIRD_DISPENSING_TAP.get(), DispensingTapBlockEntity::serverTick);
    }
}

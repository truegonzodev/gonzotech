package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.RectifierBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ректификатор («Открытие 3»): разделяет дистиллят на чистый спирт-ректификат
 * (конверсия 2:1, темп 2 mB/t за 6 GTH/t + 9 GTU/t).
 */
public class RectifierBlock extends MachineBlock {

    public static final MapCodec<RectifierBlock> CODEC = simpleCodec(RectifierBlock::new);

    public RectifierBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RectifierBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THIRD_RECTIFIER.get()
            ? (lvl, pos, st, be) -> ((RectifierBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.WortKettleBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сусловарочный котёл («Открытие 3»): варит брагу в сусло (26 mB/t за 42 GTH/t),
 * полностью удаляя гниль. При избытке тепла выпаривает сусло (2 mB/t за 16 GTH/t),
 * повышая концентрацию спирта до 30%. Также фасует пиво в кружки и вёдра.
 */
public class WortKettleBlock extends MachineBlock {

    public static final MapCodec<WortKettleBlock> CODEC = simpleCodec(WortKettleBlock::new);

    public WortKettleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WortKettleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THIRD_WORT_KETTLE.get()
            ? (lvl, pos, st, be) -> ((WortKettleBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.FillerBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Наполнитель («Открытие 3»): полу-реакторный аппарат для смешивания растворов,
 * химических реакций и распределения жидкостей по таре (канистры и вёдра).
 */
public class FillerBlock extends MachineBlock {

    public static final MapCodec<FillerBlock> CODEC = simpleCodec(FillerBlock::new);

    public FillerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FillerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THIRD_FILLER.get()
            ? (lvl, pos, st, be) -> ((FillerBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

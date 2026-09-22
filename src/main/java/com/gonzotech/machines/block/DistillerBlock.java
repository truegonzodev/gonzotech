package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.DistillerBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Дистиллятор («Открытие 3»): перегоняет брагу или сусло (11 mB/t + 20 mB воды + 64 GTH/t)
 * в дистиллят (48% спирт) и кипяток (20 mB).
 * При гнили > 8% (или наличии > 127 mB яда на выходе) партия превращается в зелье отравления II (2:1).
 */
public class DistillerBlock extends MachineBlock {

    public static final MapCodec<DistillerBlock> CODEC = simpleCodec(DistillerBlock::new);

    public DistillerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistillerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THIRD_DISTILLER.get()
            ? (lvl, pos, st, be) -> ((DistillerBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

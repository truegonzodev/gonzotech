package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SingularEnergySourceBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Admin-only, stateless virtual source of GTU. */
public final class SingularEnergySourceBlock extends MachineBlock {

    public static final MapCodec<SingularEnergySourceBlock> CODEC = simpleCodec(SingularEnergySourceBlock::new);

    public SingularEnergySourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SingularEnergySourceBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularEnergySourceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SINGULAR_ENERGY_SOURCE.get(),
            SingularEnergySourceBlockEntity::serverTick);
    }
}

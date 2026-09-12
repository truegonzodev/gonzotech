package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.TurbineRotorBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ротор занимает КАЖДУЮ клетку внутреннего объёма турбины.
 *
 * <p>BlockEntity существует у каждого ротора, но тикер получает только один
 * отмеченный {@link #CONTROLLER} ротор. Таким образом многоблок с 99 роторами
 * не делает 99 энергетических обходов в тик.</p>
 */
public final class TurbineRotorBlock extends TurbinePartBlock implements EntityBlock {

    public static final MapCodec<TurbineRotorBlock> CODEC = simpleCodec(TurbineRotorBlock::new);
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");

    public TurbineRotorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, false)
            .setValue(CONTROLLER, false));
    }

    @Override
    protected MapCodec<TurbineRotorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONTROLLER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineRotorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(CONTROLLER)) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.TURBINE_ROTOR.get(),
            TurbineRotorBlockEntity::serverTick);
    }
}

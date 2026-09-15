package com.gonzotech.machines.block;

import com.gonzotech.machines.turbine.TurbineStructure;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Общая часть корпуса и ротора турбины.
 *
 * <p>{@link #FORMED} — намеренно очень лёгкая CTM-версия: при успешной сборке
 * меняется только один блокстейт корпуса, а модель переключается с монтажной
 * рамки на бесшовную панель. Никаких 47 вариантов текстур и никаких клиентских
 * поисков соседей каждый кадр.</p>
 */
public abstract class TurbinePartBlock extends Block {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected TurbinePartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected abstract MapCodec<? extends TurbinePartBlock> codec();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            TurbineStructure.partPlaced(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            TurbineStructure.partRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return TurbineStructure.openMenu(level, pos, player)
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
    }
}

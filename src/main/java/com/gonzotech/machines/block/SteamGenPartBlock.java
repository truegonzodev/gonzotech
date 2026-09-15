package com.gonzotech.machines.block;

import com.gonzotech.machines.steamgen.SteamGenStructure;
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
 * Общая часть корпуса и ядра продвинутого парогенератора.
 *
 * <p>{@link #FORMED} — лёгкая CTM-версия: при успешной сборке меняется только
 * один блокстейт, а модель переключается с монтажной рамки на бесшовную
 * панель. Клиентский Smart CTM собирает стыки по этим же флагам.</p>
 */
public abstract class SteamGenPartBlock extends Block {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected SteamGenPartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected abstract MapCodec<? extends SteamGenPartBlock> codec();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            SteamGenStructure.partPlaced(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            SteamGenStructure.partRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return SteamGenStructure.openMenu(level, pos, player)
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
    }
}

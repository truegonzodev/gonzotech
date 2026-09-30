package com.gonzotech.machines.block;

import com.gonzotech.machines.blastfurnace.BlastFurnaceStructure;
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
 * Шамотный кирпич — строительный блок доменной печи (0.3.65).
 *
 * <p>{@link #FORMED} — та же лёгкая CTM-версия, что у турбины/парогена: при
 * сборке структуры меняется один блокстейт, клиентский Smart CTM рисует
 * бесшовную обшивку из {@code fireclay_formed} + бордюров.</p>
 */
public class FireclayBlock extends Block {

    public static final MapCodec<FireclayBlock> CODEC = simpleCodec(FireclayBlock::new);

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public FireclayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends FireclayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            BlastFurnaceStructure.partChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlastFurnaceStructure.partChanged(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return BlastFurnaceStructure.openMenu(level, pos, player)
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
    }
}

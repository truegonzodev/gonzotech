package com.gonzotech.machines.litho;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Техническая оболочка сформированной литографии: каждый из 17 блоков-участников
 * рендерит свой срез UV-развёртки 96×80 ({@code slice} — позиция в коробке
 * x + 3*z + 9*слой, {@code variant} — вид чипа). Не добывается и не крафтится:
 * сломанная оболочка распадает структуру и выпадает оригинальным блоком через
 * его loot-таблицу ({@link SiliconFactoryStructure#shellBroken}).
 */
public final class SiliconFactoryShellBlock extends Block {

    public static final IntegerProperty SLICE = IntegerProperty.create("slice", 0, 17);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 1, 3);
    public static final MapCodec<SiliconFactoryShellBlock> CODEC = simpleCodec(SiliconFactoryShellBlock::new);

    public SiliconFactoryShellBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(SLICE, 0)
            .setValue(VARIANT, 1));
    }

    @Override
    protected MapCodec<? extends SiliconFactoryShellBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLICE, VARIANT);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            SiliconFactoryStructure.shellBroken(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return SiliconFactoryStructure.openMenu(level, pos, player)
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
    }
}

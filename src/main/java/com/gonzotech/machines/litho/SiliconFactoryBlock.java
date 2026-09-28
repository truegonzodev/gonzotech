package com.gonzotech.machines.litho;

import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Литографическая фабрика — контроллер многоблока 3×3×2 (верхний центр).
 *
 * <p>{@code formed} — переключение монтажной модели на срез UV-развёртки
 * структуры; {@code variant} (1..3) — вид чипа (раскладка автора). ПКМ по
 * сформированной структуре (в том числе по фабрике) открывает GUI; без
 * структуры — ничего.</p>
 */
public final class SiliconFactoryBlock extends Block implements EntityBlock {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 1, 3);
    public static final MapCodec<SiliconFactoryBlock> CODEC = simpleCodec(SiliconFactoryBlock::new);

    public SiliconFactoryBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, false)
            .setValue(VARIANT, 1));
    }

    @Override
    protected MapCodec<? extends SiliconFactoryBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, VARIANT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SiliconFactoryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.SILICON_FACTORY.get()
            ? (BlockEntityTicker<T>) (BlockEntityTicker<SiliconFactoryBlockEntity>) SiliconFactoryBlockEntity::serverTick
            : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel server) {
            SiliconFactoryStructure.factoryPlaced(server, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            SiliconFactoryStructure.partRemoved(server, pos);
        }
        if (!state.is(newState.getBlock()) && !level.isClientSide()
            && level.getBlockEntity(pos) instanceof SiliconFactoryBlockEntity factory) {
            Containers.dropContents(level, pos, factory);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SiliconFactoryBlockEntity factory && factory.isFormed()
            ? factory
            : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return SiliconFactoryStructure.openMenu(level, pos, player)
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return player.isSecondaryUseActive()
            ? super.useItemOn(stack, state, level, pos, player, hand, hit)
            : useWithoutItem(state, level, pos, player, hit);
    }
}

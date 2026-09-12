package com.gonzotech.machines.network;

import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ФИЛЬТР предметов — полный куб с {@link ItemFilterBlockEntity} и меню (3
 * ghost-слота). Активен: каждый тик тянет из прилегающих контейнеров и раскидывает
 * поток по правилам ({@link ItemFilterRouting}). ПКМ пустой рукой — открыть меню.
 * <p>
 * Форма — обычный полный куб (в отличие от тонких труб), поэтому это НЕ
 * {@link PipeBlock}, а самостоятельный {@link EntityBlock}.
 */
public class ItemFilterBlock extends Block implements EntityBlock {

    public static final MapCodec<ItemFilterBlock> CODEC = simpleCodec(ItemFilterBlock::new);

    public ItemFilterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ItemFilterBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemFilterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.ITEM_FILTER.get(), ItemFilterBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Высыпаем транзитный буфер (реальные предметы в пути). Ghost-образцы
            // не предметы — их дропать не нужно.
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                net.minecraft.world.Containers.dropContents(level, pos, container);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
                player.openMenu(provider, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @SuppressWarnings("unchecked")
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
        BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}

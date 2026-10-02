package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import com.gonzotech.machines.block.entity.ThirdLeadChestBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Свинцовый ящик (0.3.111, эпоха 3): копия ванильной бочки — один ряд из 9 слотов,
 * «двойных» сцепок нет. Сам блок НЕ замыкает радиационный контур и экранирующим
 * свойством не обладает; содержимое ящика действует на чанк на 80% слабее
 * ({@link #CONTENT_CHUNK_FACTOR}, рад-скан чанка и визуальные источники).
 */
public class ThirdLeadChestBlock extends Block implements EntityBlock {

    /** Содержимое ящика влияет на чанк на 80% слабее (автор, раунд 11). */
    public static final double CONTENT_CHUNK_FACTOR = 0.2;

    /** Полная ориентация, как у бочки: включая вверх/вниз. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public static final MapCodec<ThirdLeadChestBlock> CODEC = simpleCodec(ThirdLeadChestBlock::new);

    public ThirdLeadChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(OPEN, Boolean.FALSE));
    }

    @Override
    protected MapCodec<ThirdLeadChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                   Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider, pos);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** При сломе блока высыпаем содержимое (и снимаем компараторный сигнал). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThirdLeadChestBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gonzotech.lead_chest")
                .withStyle(ChatFormatting.GRAY));
    }
}

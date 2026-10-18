package com.gonzotech.core.block;

import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.reactor.ReactorFuelStorage;
import com.gonzotech.machines.reactor.ReactorStructure;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Six-way reactor controller; normal clicks open its formed multiblock menu and Shift-clicks report diagnostics. */
public final class ReactorControlPanelBlock extends Block implements EntityBlock {

    public static final MapCodec<ReactorControlPanelBlock> CODEC = simpleCodec(ReactorControlPanelBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public ReactorControlPanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorControlPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.THIRD_URANIUM_REACTOR_CONTROL_PANEL.get()) {
            return null;
        }
        return (tickLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof ReactorControlPanelBlockEntity panel) {
                panel.serverTick();
            }
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            ReactorStructure.partChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            ReactorStructure.partRemoved(level, pos);
            if (level.getBlockEntity(pos) instanceof ReactorControlPanelBlockEntity panel) {
                Containers.dropContents(level, pos, panel);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player);
    }

    private static InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof ReactorControlPanelBlockEntity panel) {
            if (!player.isShiftKeyDown() && panel.isFormedController()) {
                if (!ReactorFuelStorage.canOpenFuelInventory((net.minecraft.server.level.ServerLevel) level, panel)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Инвентарь топлива недоступен: загрузите все чанки реакторной зоны."), false);
                } else {
                    player.openMenu(panel, buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeVarInt(panel.fuelSlotCount());
                    });
                }
            } else {
                for (net.minecraft.network.chat.Component line : ReactorStructure.debugReportComponents(level, pos)) {
                    player.displayClientMessage(line, false);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}

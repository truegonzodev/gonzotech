package com.gonzotech.core.block;

import com.gonzotech.machines.reactor.ReactorStructure;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Предохранительный клапан реактора I-поколения. Корпус — как пилястра
 * (боковые грани и торцы одинаковые), плюс выделенная face-грань по FACING.
 * <p>
 * Допустим только на ГРАНИ обшивки (не на ребре и не на углу): его face или
 * back должен смотреть на активную зону. Такой клапан считается валидной
 * деталью оболочки, как экранированный узел; иначе структура не формируется.
 * <p>
 * Сам блок без BlockEntity: сброс GTH и гейзер частиц ведёт
 * {@code ReactorRuntime} от панели управления, когда она запитана редстоуном.
 */
public final class ReactorValveBlock extends Block {

    public static final MapCodec<ReactorValveBlock> CODEC = simpleCodec(ReactorValveBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public ReactorValveBlock(BlockBehaviour.Properties properties) {
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
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

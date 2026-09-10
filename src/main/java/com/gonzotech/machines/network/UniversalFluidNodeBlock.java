package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Узел универсальной жидкостной трубы — то же, что {@link UniversalFluidPipeBlock}
 * (несёт воду И пар), но с ОТКРЫТЫМИ 6 гранями: точка ветвления/уголков для
 * универсальных труб (и стык с водными/паровыми трубами и машинами в любую
 * сторону). Полный куб, как остальные узлы.
 */
public class UniversalFluidNodeBlock extends UniversalFluidPipeBlock {

    public UniversalFluidNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(UniversalFluidNodeBlock::new);
    }

    @Override
    public boolean connectsAllSides() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(MODE, PipeMode.AUTO)
            .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}

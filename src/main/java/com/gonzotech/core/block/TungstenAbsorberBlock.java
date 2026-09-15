package com.gonzotech.core.block;

import com.gonzotech.core.block.entity.TungstenAbsorberBlockEntity;
import com.gonzotech.machines.energy.NuclearDefs;
import com.gonzotech.machines.nuclear.ThermalHazards;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ordinary tungsten storage block until a heat pipe/node touches it. The hidden
 * BlockEntity then becomes a high-loss GTH absorber without adding a GUI.
 */
public final class TungstenAbsorberBlock extends Block implements EntityBlock {

    public static final MapCodec<TungstenAbsorberBlock> CODEC = simpleCodec(TungstenAbsorberBlock::new);

    public TungstenAbsorberBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<TungstenAbsorberBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TungstenAbsorberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.TUNGSTEN_ABSORBER.get(), TungstenAbsorberBlockEntity::serverTick);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof TungstenAbsorberBlockEntity absorber
            && absorber.storedGth() > NuclearDefs.TUNGSTEN_ABSORBER_IGNITION_THRESHOLD) {
            ThermalHazards.igniteTouchingPlayer(entity);
        }
        super.entityInside(state, level, pos, entity);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
        BlockEntityType<A> actual, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker
    ) {
        return actual == expected ? (BlockEntityTicker<A>) ticker : null;
    }
}

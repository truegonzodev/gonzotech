package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Топка. */
public class FireboxBlock extends MachineBlock {

    public static final MapCodec<FireboxBlock> CODEC = simpleCodec(FireboxBlock::new);

    public FireboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<FireboxBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FireboxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.FIREBOX.get(), FireboxBlockEntity::serverTick);
    }

    /** Сверяет тип BE с тикером и безопасно кастует (как в ванильных EntityBlock). */
    @SuppressWarnings("unchecked")
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
        BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}

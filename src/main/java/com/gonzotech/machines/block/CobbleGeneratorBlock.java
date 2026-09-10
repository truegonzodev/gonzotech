package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.CobbleGeneratorBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Генератор булыжника. Ставится как верстак — грань только по сторонам света
 * (горизонтальный {@code FACING} из {@link MachineBlock}, не вверх/вниз). Вся
 * логика — в {@link CobbleGeneratorBlockEntity}.
 */
public class CobbleGeneratorBlock extends MachineBlock {

    public static final MapCodec<CobbleGeneratorBlock> CODEC = simpleCodec(CobbleGeneratorBlock::new);

    public CobbleGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CobbleGeneratorBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CobbleGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.COBBLE_GENERATOR.get(),
            CobbleGeneratorBlockEntity::serverTick);
    }
}

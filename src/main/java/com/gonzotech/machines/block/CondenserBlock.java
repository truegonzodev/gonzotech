package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.CondenserBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Конденсатор — пассивный блок без меню. Даёт бонус к водоотдаче примыкающему
 * генератору Стирлинга (см. {@link CondenserBlockEntity}). Тика нет —
 * достаточно {@link EntityBlock#newBlockEntity} как маркера.
 */
public class CondenserBlock extends Block implements EntityBlock {

    public static final MapCodec<CondenserBlock> CODEC = simpleCodec(CondenserBlock::new);

    public CondenserBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CondenserBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CondenserBlockEntity(pos, state);
    }
}

package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Размещаемая болванка будущей машины второго открытия.
 *
 * <p>У блока намеренно нет BlockEntity, тика, инвентаря и GUI. Он существует,
 * чтобы завод сплавов и измельчитель уже были видимы и размещаемы во втором
 * открытии, не притворяясь готовым перерабатывающим механизмом.</p>
 */
public final class SecondMachineStubBlock extends MachineBlock {

    public static final MapCodec<SecondMachineStubBlock> CODEC = simpleCodec(SecondMachineStubBlock::new);

    public SecondMachineStubBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondMachineStubBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}

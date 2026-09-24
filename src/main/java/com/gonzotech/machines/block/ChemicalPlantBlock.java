package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.ChemicalPlantBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Химический завод («Открытие 3»): аппарат для органического и неорганического
 * синтеза полимеров, хелатов и медицинских препаратов.
 * Работает от GTU, требует катализаторы (платина/палладий) и сырьё в сетке 3×3.
 */
public class ChemicalPlantBlock extends MachineBlock {

    public static final MapCodec<ChemicalPlantBlock> CODEC = simpleCodec(ChemicalPlantBlock::new);

    public ChemicalPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemicalPlantBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.CHEMICAL_PLANT.get()
            ? (lvl, pos, st, be) -> ((ChemicalPlantBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

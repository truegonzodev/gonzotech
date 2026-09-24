package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.FermentationVatBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бродильный чан («Открытие 3»): принимает любую органику кроме мяса,
 * превращая её в брагу (128 mB на предмет). Сбраживает брагу при стабильном GTH
 * (до 13% спирта); при недостатке GTH брага загнивает (до 98% гнили).
 * Вольфрамовые блоки по бокам расширяют ёмкость GTH (+30K на блок).
 */
public class FermentationVatBlock extends MachineBlock {

    public static final MapCodec<FermentationVatBlock> CODEC = simpleCodec(FermentationVatBlock::new);

    public FermentationVatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FermentationVatBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THIRD_FERMENTATION_VAT.get()
            ? (lvl, pos, st, be) -> ((FermentationVatBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

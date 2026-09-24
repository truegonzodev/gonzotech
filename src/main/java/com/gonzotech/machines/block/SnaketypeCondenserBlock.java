package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SnaketypeCondenserBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Змеевиковый конденсатор («Открытие 3»):
 * <ul>
 *   <li>Приём кипятка до 1 560 mB/t;</li>
 *   <li>Баки кипятка 8 000 mB, воды 8 000 mB;</li>
 *   <li>Базовая скорость конденсации 2 mB/t;</li>
 *   <li>Бонусы от 6 смежных блоков льда: обычный +1, плотный +3, европианский +7,
 *       синий +12, сверхплотный +29 mB/t;</li>
 *   <li>Автослив охлаждённой воды до 1 560 mB/t.</li>
 * </ul>
 */
public class SnaketypeCondenserBlock extends MachineBlock {

    public static final MapCodec<SnaketypeCondenserBlock> CODEC = simpleCodec(SnaketypeCondenserBlock::new);

    public SnaketypeCondenserBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SnaketypeCondenserBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THIRD_SNAKETYPE_CONDENSER.get()
            ? (lvl, pos, st, be) -> ((SnaketypeCondenserBlockEntity) be).tick(lvl, pos, st)
            : null;
    }
}

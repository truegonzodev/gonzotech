package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Внутренняя связка экранированной семьи (эпоха 3, 0.3.114). Не предмет:
 * образуется только сборкой совместимых экранированных труб одного тира
 * («пучки возможны только из одного тира», автор, раунд 12) и при разборке
 * отдаёт исходные экранированные части. Лимиты — тир II ×{@value ThirdTierPipe#STAT_FACTOR}
 * (все статы семьи ×0.88).
 */
public final class ThirdCompositePipeBlock extends CompositePipeBlock implements ThirdTierPipe {

    public static final MapCodec<ThirdCompositePipeBlock> CODEC = simpleCodec(ThirdCompositePipeBlock::new);

    public ThirdCompositePipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    public long throughputLimit(BlockState state, PipeType type) {
        return switch (type) {
            case WIRE -> SecondTierDefs.WIRE_THROUGHPUT;
            case HEAT -> SecondTierDefs.HEAT_THROUGHPUT;
            // Универсальный жидкостный угол делит общий поток 682 mB/t;
            // отдельные вода/пар в связке держат свои 852 mB/t.
            case WATER -> carriesUniversalFluid(state)
                ? SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT : SecondTierDefs.WATER_THROUGHPUT;
            case STEAM -> carriesUniversalFluid(state)
                ? SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT : SecondTierDefs.STEAM_THROUGHPUT;
            case MASH -> SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT / 2;
            case WORT, DISTILLATE, RECTIFICATE, BOILING_WATER, POISON_POTION,
                 SULFURIC_ACID, ETHYLENE, AMINOBLAZEETHANOL, FORMALDEHYDE ->
                SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
            case ITEM -> SecondTierDefs.ITEM_THROUGHPUT;
        };
    }

    @Override
    public int perItemThroughputLimit(BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }

    @Override
    public long sharedFluidThroughputLimit(BlockState state) {
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }

    @Override
    public double throughputFactor(BlockState state, PipeType type) {
        return STAT_FACTOR;
    }
}

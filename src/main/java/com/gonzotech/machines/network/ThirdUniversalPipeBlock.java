package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/**
 * Universal pipe of the third opening (shielded, stats x0.88): a pipe-form of
 * the universal node — carries EVERY resource stream (wire, heat, fluids,
 * items). Geometry/behavior host: universal fluid pipe; item carriage flows
 * through the generic {@code PipeCarrier.carries} gating of ItemRouting.
 */
public class ThirdUniversalPipeBlock extends UniversalFluidPipeBlock implements ThirdTierPipe {

    public ThirdUniversalPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ThirdUniversalPipeBlock::new);
    }

    @Override
    public boolean carries(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return true; // как универсальный узел: все потоки
    }

    @Override
    public long throughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return switch (type) {
            case WIRE -> SecondTierDefs.WIRE_THROUGHPUT;
            case HEAT -> SecondTierDefs.HEAT_THROUGHPUT;
            case MASH -> SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT / 2;
            case WATER, STEAM, WORT, DISTILLATE, RECTIFICATE, BOILING_WATER, POISON_POTION,
                 SULFURIC_ACID, ETHYLENE, AMINOBLAZEETHANOL, FORMALDEHYDE ->
                SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
            case ITEM -> SecondTierDefs.ITEM_THROUGHPUT;
        };
    }

    @Override
    public int perItemThroughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }

    @Override
    public long sharedFluidThroughputLimit(net.minecraft.world.level.block.state.BlockState state) {
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }

    @Override
    public double throughputFactor(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return STAT_FACTOR;
    }
}

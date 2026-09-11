package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;

/** Внешний корпус прямоугольной паровой турбины. */
public final class TurbineCasingBlock extends TurbinePartBlock {

    public static final MapCodec<TurbineCasingBlock> CODEC = simpleCodec(TurbineCasingBlock::new);

    public TurbineCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<TurbineCasingBlock> codec() {
        return CODEC;
    }
}

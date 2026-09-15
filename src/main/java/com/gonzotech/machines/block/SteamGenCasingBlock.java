package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;

/**
 * Корпус продвинутого парогенератора: внешний слой многоблока 5×5×5.
 * Внутри (3×3×3) стоят ядра и драгоценные блоки-теплообменники.
 */
public final class SteamGenCasingBlock extends SteamGenPartBlock {

    public static final MapCodec<SteamGenCasingBlock> CODEC = simpleCodec(SteamGenCasingBlock::new);

    public SteamGenCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SteamGenCasingBlock> codec() {
        return CODEC;
    }
}

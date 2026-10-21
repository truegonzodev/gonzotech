package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Бур автошахтера (0.3.257). Ставится Плотником в точке бурения поверх
 * дорожки из рабочих блоков; собственная логика скважины (туннель 1×1 до
 * бедрока, всасывание 5×5×5) — следующий пакет добычи.
 */
public final class MinerDrillBlock extends Block {

    public static final MapCodec<MinerDrillBlock> CODEC = simpleCodec(MinerDrillBlock::new);

    public MinerDrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MinerDrillBlock> codec() {
        return CODEC;
    }
}

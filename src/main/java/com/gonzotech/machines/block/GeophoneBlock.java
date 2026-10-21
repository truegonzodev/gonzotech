package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Геофон автошахтера. Собранная сейсмическая установка — просто соседние
 * блоки без мультиблок-структуры: геофон ставится непосредственно НАД
 * источником колебаний.
 */
public final class GeophoneBlock extends Block {

    public static final MapCodec<GeophoneBlock> CODEC = simpleCodec(GeophoneBlock::new);

    public GeophoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GeophoneBlock> codec() {
        return CODEC;
    }
}

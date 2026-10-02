package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/** Свинцовый липкий поршень: полная копия ванильного (sticky), экранирование 81%. */
public class ThirdStickyPistonBlock extends PistonBaseBlock {

    public static final MapCodec<ThirdStickyPistonBlock> CODEC = simpleCodec(ThirdStickyPistonBlock::new);

    public ThirdStickyPistonBlock(Properties properties) {
        super(true, properties);
    }

    /**
     * Ванильный PistonBaseBlock.codec() объявлен ТОЧНЫМ типом MapCodec<PistonBaseBlock>
     * (не «? extends Block», как у большинства блоков), поэтому ковариация не проходит.
     * Отдаём свой CODEC (simpleCodec строит ThirdStickyPistonBlock) через erasure-каст: если кодек
     * когда-нибудь реально декодирует — соберётся наш класс, а не ванильный поршень.
     */
    @SuppressWarnings("unchecked")
    @Override
    public MapCodec<PistonBaseBlock> codec() {
        return (MapCodec<PistonBaseBlock>) (MapCodec<?>) CODEC;
    }
}

package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/** Свинцовый липкий поршень: полная копия ванильного (sticky), экранирование 81%. */
public class ThirdStickyPistonBlock extends PistonBaseBlock {

    public static final MapCodec<ThirdStickyPistonBlock> CODEC = simpleCodec(ThirdStickyPistonBlock::new);

    public ThirdStickyPistonBlock(Properties properties) {
        super(true, properties);
    }

    @Override
    public MapCodec<? extends PistonBaseBlock> codec() {
        return CODEC;
    }
}

package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * Свинцовый поршень (0.3.110, эпоха 3): полная копия ванильного поршня
 * (вся механика — {@link PistonBaseBlock}), свои текстуры, экранирование 81%
 * и замыкание радиационного контура (тег contour_seal) в любом состоянии штока.
 */
public class ThirdPistonBlock extends PistonBaseBlock {

    public static final MapCodec<ThirdPistonBlock> CODEC = simpleCodec(ThirdPistonBlock::new);

    public ThirdPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public MapCodec<? extends PistonBaseBlock> codec() {
        return CODEC;
    }
}

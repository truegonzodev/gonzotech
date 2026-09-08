package com.gonzotech.space.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Простой падающий блок (как ванильный песок) для космических измерений —
 * например «Лунный песок». В 1.21.4 {@link FallingBlock} требует реализации
 * {@link #codec()}, поэтому заводим свой подкласс с {@code simpleCodec}.
 *
 * <p>NB: {@code getDustColor} становится абстрактным только в 1.21.5 — в 1.21.4
 * базовой реализации достаточно, ничего доопределять не нужно.
 */
public class GonzoFallingBlock extends FallingBlock {

    public static final MapCodec<GonzoFallingBlock> CODEC = simpleCodec(GonzoFallingBlock::new);

    public GonzoFallingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }
}

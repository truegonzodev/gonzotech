package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Скваженная труба (0.3.258) — физическая стенка скважины 1×1. Бур по
 * мере углубления заменяет блок под собой очередной трубой; трубы
 * расходуются из хранилища Плотника. При «свертывании» возвращаются в
 * Плотник, при закладке ТНТ — заменяются на взрывчатку и не возвращаются.
 */
public final class BorePipeBlock extends Block {

    public static final MapCodec<BorePipeBlock> CODEC = simpleCodec(BorePipeBlock::new);

    public BorePipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BorePipeBlock> codec() {
        return CODEC;
    }
}

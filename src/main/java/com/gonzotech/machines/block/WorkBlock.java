package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Рабочий блок автошахтера: звено будущей цепи Плотник ↔ сейсмические
 * установки и буры. На этом этапе это обычный полноразмерный блок без
 * собственной логики; построение связей добавляется следующим пакетом.
 */
public final class WorkBlock extends Block {

    public static final MapCodec<WorkBlock> CODEC = simpleCodec(WorkBlock::new);

    public WorkBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WorkBlock> codec() {
        return CODEC;
    }
}

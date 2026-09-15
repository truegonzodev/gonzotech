package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/**
 * Отсеиватель II: остаётся зависимой reject-веткой Фильтра, но пропускает через
 * неё пять точных видов по две единицы — 10 предметов за тик.
 */
public final class SecondItemScavengerBlock extends ItemScavengerBlock {

    public static final MapCodec<SecondItemScavengerBlock> CODEC = simpleCodec(SecondItemScavengerBlock::new);

    public SecondItemScavengerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondItemScavengerBlock> codec() {
        return CODEC;
    }

    @Override
    public int itemThroughputLimit() {
        return (int) SecondTierDefs.ITEM_THROUGHPUT;
    }

    @Override
    public int perItemThroughputLimit() {
        return SecondTierDefs.ITEM_PER_TYPE_THROUGHPUT;
    }
}

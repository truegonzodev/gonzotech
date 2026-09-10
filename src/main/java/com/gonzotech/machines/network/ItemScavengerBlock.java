package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ОТСЕИВАТЕЛЬ — пассивный блок-маркер БЕЗ меню и без {@code BlockEntity}.
 * <p>
 * Сам ничего не делает: он лишь «якорь» второй выходной сети для прилегающего
 * {@link ItemFilterBlock}. Когда Фильтр отсеивает несовпавший предмет, он ищет
 * Отсеиватель у своих граней и гонит поток по ЕГО цепи предметных труб (BFS от
 * отсеивателя, не назад в Фильтр — см. {@link ItemFilterRouting}).
 * <p>
 * Меню/GUI нет — вся настройка у Фильтра. Форма — полный куб.
 */
public class ItemScavengerBlock extends Block {

    public static final MapCodec<ItemScavengerBlock> CODEC = simpleCodec(ItemScavengerBlock::new);

    public ItemScavengerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ItemScavengerBlock> codec() {
        return CODEC;
    }

    /** true, если блок в этом состоянии — Отсеиватель. */
    public static boolean isScavenger(BlockState state) {
        return state.getBlock() instanceof ItemScavengerBlock;
    }
}

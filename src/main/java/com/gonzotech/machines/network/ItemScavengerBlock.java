package com.gonzotech.machines.network;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ОТСЕИВАТЕЛЬ — пассивный блок-маркер БЕЗ меню и без {@code BlockEntity}.
 * <p>
 * Он работает только как вторая, reject-ветка непосредственно прилегающего
 * {@link ItemFilterBlock}: несовпавший с шаблонами предмет Фильтр отправляет в
 * сеть предметных труб, начинающуюся у Отсеивателя (см. {@link ItemFilterRouting}).
 * Самостоятельно он не извлекает предметы из контейнеров.
 * <p>
 * Если Отсеиватель получает redstone-сигнал, он становится конечной точкой reject
 * ветки: Фильтр удаляет предметы сразу в нём, не продолжая поиск подключённых к
 * нему труб или контейнеров. Без сигнала сохраняется обычная маршрутизация.
 */
public class ItemScavengerBlock extends Block {

    public static final MapCodec<ItemScavengerBlock> CODEC = simpleCodec(ItemScavengerBlock::new);

    public ItemScavengerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ItemScavengerBlock> codec() {
        return CODEC;
    }

    /** Общий лимит reject-ветки этого Отсеивателя за тик. */
    public int itemThroughputLimit() {
        return (int) PipeType.ITEM.maxThroughput();
    }

    /** Лимит одного точного вида предмета в reject-ветке за тик. */
    public int perItemThroughputLimit() {
        return ItemRouting.PER_ITEM_TICK_CAP;
    }

    /** true, если соседний redstone-компонент питает этот Отсеиватель. */
    public boolean isPowered(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    /** true, если блок в этом состоянии — Отсеиватель. */
    public static boolean isScavenger(BlockState state) {
        return state.getBlock() instanceof ItemScavengerBlock;
    }
}

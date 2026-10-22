package com.gonzotech.machines.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Хелпер для клиентских конструкторов меню: читает позицию блока из буфера
 * и достаёт нужный {@link BlockEntity} из клиентского мира.
 */
public final class MenuHelper {

    private MenuHelper() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> T readBlockEntity(Inventory inv, RegistryFriendlyByteBuf buf, Class<T> type) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (type.isInstance(be)) {
            return (T) be;
        }
        throw new IllegalStateException("Неверный BlockEntity для меню: " + be);
    }

    /**
     * То же, но без броска: клиент ещё не видит BE (гонка переустановки
     * блока, два Плотника в одной клетке за кадр) — меню строится с
     * отсоединённым BE, содержимое всё равно приедет через слоты.
     * Бросок сюда ронял соединение целиком («Не удалось открыть экран»).
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> T readBlockEntitySafe(Inventory inv,
            RegistryFriendlyByteBuf buf, Class<T> type) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        return type.isInstance(be) ? (T) be : null;
    }
}

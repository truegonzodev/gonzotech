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
}

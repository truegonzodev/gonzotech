package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.StirlingBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню генератора Стирлинга: предметных слотов нет, только шкалы пара и GTU
 * и индикаторы «цепочка собрана» / «работает».
 */
public class StirlingMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;

    private final StirlingBlockEntity be;

    public StirlingMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, StirlingBlockEntity.class), new SimpleContainerData(5));
    }

    public StirlingMenu(int id, Inventory inv, StirlingBlockEntity be, ContainerData data) {
        super(ModMenus.STIRLING.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;
        addPlayerInventory(inv, 8, 84);
    }

    public StirlingBlockEntity blockEntity() {
        return be;
    }

    public int steam() {
        return data.get(0);
    }

    /** GTU собирается из двух half-word'ов ContainerData. */
    public int gtu() {
        return (data.get(1) & 0xFFFF) | ((data.get(2) & 0xFFFF) << 16);
    }

    public boolean chainOk() {
        return data.get(3) != 0;
    }

    public boolean running() {
        return data.get(4) != 0;
    }
}

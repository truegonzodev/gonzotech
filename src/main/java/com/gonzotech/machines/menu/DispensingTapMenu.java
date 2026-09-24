package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.DispensingTapBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню разливного крана: раскладка точь-в-точь как у помпы (PumpMenu).
 * Верхний слот — вход пустой тары (ведро/пузырёк), нижний — выход.
 * Две шкалы: дистиллят (256 mB) и сусло (1024 mB).
 */
public class DispensingTapMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final DispensingTapBlockEntity be;

    public DispensingTapMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, DispensingTapBlockEntity.class), new SimpleContainerData(2));
    }

    public DispensingTapMenu(int id, Inventory inv, DispensingTapBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_DISPENSING_TAP.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new PumpInputSlot(be, DispensingTapBlockEntity.SLOT_CONTAINER_IN, 80, 17));
        addSlot(new OutputOnlySlot(be, DispensingTapBlockEntity.SLOT_FILLED_OUT, 80, 53));

        addPlayerInventory(inv, 8, 84);
    }

    public DispensingTapBlockEntity blockEntity() {
        return be;
    }

    public int distillate() {
        return data.get(0);
    }

    public int wort() {
        return data.get(1);
    }
}

package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.PlotnickBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * Плотник — GUI по замеру {@code plotnick_gui.png} автора (0.3.256):
 * окно 176×180, шкала GTU (7,17) рисуется {@code MachineScreen}, 6 кнопок
 * 18×18 в (26/44, 18/36/54), карт-слоты (98,36) и (116,36), 9 активных
 * слотов по 512 в ряд y=76, инвентарь (8,98) с хотбаром на y=156.
 */
public final class PlotnickMenu extends BaseMachineMenu {

    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 180;

    private final PlotnickBlockEntity plotnick;

    public PlotnickMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, PlotnickBlockEntity.class),
            new SimpleContainerData(2));
    }

    public PlotnickMenu(int id, Inventory inventory, PlotnickBlockEntity be, ContainerData data) {
        super(ModMenus.PLOTNICK.get(), id, be, data, PlotnickBlockEntity.CONTAINER_SIZE);
        this.plotnick = be;
        for (int k = 0; k < PlotnickBlockEntity.SLOTS_ACTIVE; k++) {
            addSlot(new Slot(be, k, 8 + 18 * k, 76));
        }
        addSlot(new Slot(be, PlotnickBlockEntity.SLOT_CARD_A, 98, 36));
        addSlot(new Slot(be, PlotnickBlockEntity.SLOT_CARD_B, 116, 36));
        addPlayerInventory(inventory, 8, 98);
    }

    /** GTU (единицы) для шкалы GUI. */
    public int gtu() {
        return data.get(0);
    }

    /** Занятых виртуальных стаков (0..54) — тултип кнопки хранилища. */
    public int virtualUsed() {
        return data.get(1);
    }

    /** Кнопка GUI: сервер уже проверил id меню и доступ. */
    public void handleButtonClick(int buttonId) {
        plotnick.handleButton(buttonId);
    }

    /** Кнопка GUI с игроком-инициатором — ему уходит отчёт разведки в чат. */
    public void handleButtonClick(int buttonId, ServerPlayer player) {
        plotnick.handleButton(buttonId, player);
    }
}

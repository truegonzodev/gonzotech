package com.gonzotech.machines.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * База для меню функциональных блоков паровой ветки: раскладка инвентаря
 * игрока, синхронизация {@link ContainerData} и общий quick-move (Shift+ЛКМ).
 * <p>
 * Наследник добавляет свои машинные слоты в конструкторе ДО вызова
 * {@link #addPlayerInventory}, и объявляет, какие индексы слотов относятся к
 * машине, через {@link #machineSlotCount}.
 */
public abstract class BaseMachineMenu extends AbstractContainerMenu {

    protected final Container container;
    protected final ContainerData data;
    private final int machineSlots;

    protected BaseMachineMenu(MenuType<?> type, int id, Container container, ContainerData data, int machineSlots) {
        super(type, id);
        this.container = container;
        this.data = data;
        this.machineSlots = machineSlots;
        addDataSlots(data);
    }

    /** Стандартная раскладка 3×9 + хотбар, левый верхний угол = (x, y). */
    protected void addPlayerInventory(Inventory inv, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, x + col * 18, y + 58));
        }
    }

    protected int machineSlotCount() {
        return machineSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            int machineEnd = machineSlots;
            int invEnd = this.slots.size();

            if (index < machineEnd) {
                // из машины → в инвентарь игрока
                if (!moveItemStackTo(stack, machineEnd, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // из инвентаря игрока → в машину
                if (!moveItemStackTo(stack, 0, machineEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}

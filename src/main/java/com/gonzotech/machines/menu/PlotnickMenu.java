package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.PlotnickBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
            new SimpleContainerData(PlotnickBlockEntity.DATA_SLOTS));
    }

    public PlotnickMenu(int id, Inventory inventory, PlotnickBlockEntity be, ContainerData data) {
        super(ModMenus.PLOTNICK.get(), id, be, data, PlotnickBlockEntity.CONTAINER_SIZE);
        this.plotnick = be;
        if (!inventory.player.level().isClientSide) be.menuOpened();
        for (int k = 0; k < PlotnickBlockEntity.SLOTS_ACTIVE; k++) {
            addSlot(new MaxStackSlot(be, k, 8 + 18 * k, 76));
        }
        addSlot(new CardSlot(be, PlotnickBlockEntity.SLOT_CARD_A, 98, 36));
        addSlot(new CardSlot(be, PlotnickBlockEntity.SLOT_CARD_B, 116, 36));
        addPlayerInventory(inventory, 8, 98);
    }

    /**
     * Активный слот Плотника: стопка до 512 предметов одного типа (автор).
     * Ваниальные слияния считают потолок как min(лимита предмета, лимита
     * слота), поэтому shift-перенос в Плотник переопределен в меню — иначе
     * стопка так и оставалась бы 64.
     */
    private static final class MaxStackSlot extends Slot {

        private MaxStackSlot(PlotnickBlockEntity be, int index, int x, int y) {
            super(be, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return PlotnickBlockEntity.MAX_STACK;
        }
    }

    /**
     * Shift+ЛКМ в машину: сначала доливка в неполные стопки до 512 (мимо
     * ванильного min с лимитом предмета), затем пустые слоты. Обратный путь
     * (из машины в игрок) — стандартный: 512-стопка честно распадается на
     * переносимые куски.
     */
    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineEnd = machineSlotCount();
        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveIntoPlotnick(stack, machineEnd)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private boolean moveIntoPlotnick(ItemStack stack, int machineEnd) {
        boolean movedAny = false;
        for (int i = 0; i < machineEnd && !stack.isEmpty(); i++) {
            Slot target = slots.get(i);
            ItemStack stored = target.getItem();
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, stack)) continue;
            if (!target.mayPlace(stack)) continue;
            int space = target.getMaxStackSize() - stored.getCount();
            if (space <= 0) continue;
            int moved = Math.min(space, stack.getCount());
            stored.grow(moved);
            stack.shrink(moved);
            target.setChanged();
            movedAny = true;
        }
        for (int i = 0; i < machineEnd && !stack.isEmpty(); i++) {
            Slot target = slots.get(i);
            if (target.hasItem() || !target.mayPlace(stack)) continue;
            target.setByPlayer(stack.split(target.getMaxStackSize()));
            movedAny = true;
        }
        return movedAny;
    }

    /** Слот карт памяти: одна пустая или заполненная карта, не стопка. */
    private static final class CardSlot extends Slot {

        private CardSlot(PlotnickBlockEntity be, int index, int x, int y) {
            super(be, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(com.gonzotech.machines.registry.ModMachines.MEMORY_CARD.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** GTU (единицы) для шкалы GUI. */
    public int gtu() {
        return data.get(0);
    }

    /** Занятых виртуальных стаков (0..54) — тултип кнопки хранилища. */
    public int virtualUsed() {
        return data.get(1);
    }

    /**
     * Состояние Плотника для названий кнопок: в центральном слоте лежит
     * заполненная карта памяти (рудный режим «Разведки» и «Связей»).
     * Синхронизируется через ContainerData, без сканирования территории.
     */
    public boolean hasSectorCard() {
        return data.get(2) != 0;
    }

    /** Буров ещё поставить (по точкам разметки). */
    public int networkDrillsToPlace() {
        return data.get(3);
    }

    /** Рабочих блоков для полной постройки (ЛКМ). */
    public int networkRoadsFull() {
        return data.get(4);
    }

    /** Уже поставленных буров в секторе (ПКМ-режим). */
    public int networkDrillsFound() {
        return data.get(5);
    }

    /** Рабочих блоков, чтобы подсоединить найденные буры (ПКМ). */
    public int networkRoadsToLink() {
        return data.get(6);
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        plotnick.menuClosed();
        // Уносимая в руке стопка больше лимита предмета не должна попасть
        // ни в инвентарь, ни в ItemEntity при выходе: раскладываем излишек.
        clampCarried(player);
    }

    /**
     * Щелчки по слайтам вне Плотника с избыточной стопкой в руке: ванильный
     * слот игрока принимает максимум 64, а кодек стека на диске умеет лишь
     * 1..99. Ставим ровно лимит предмета, излишек возвращаем в руку, а если
     * рука занята (обмен) — в инвентарь/под ноги.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType,
            net.minecraft.world.entity.player.Player player) {
        ItemStack carried = getCarried();
        boolean foreign = slotId >= machineSlotCount() && slotId < slots.size();
        int overflow = 0;
        ItemStack template = ItemStack.EMPTY;
        if (foreign && !carried.isEmpty() && carried.getCount() > carried.getMaxStackSize()
                && (clickType == ClickType.PICKUP || clickType == ClickType.SWAP)) {
            overflow = carried.getCount() - carried.getMaxStackSize();
            template = carried.copy();
            template.setCount(overflow);
            carried.setCount(carried.getMaxStackSize());
        }
        super.clicked(slotId, button, clickType, player);
        if (overflow <= 0 || player.level().isClientSide) return;
        if (getCarried().isEmpty()) {
            setCarried(template);
        } else if (ItemStack.isSameItemSameComponents(getCarried(), template)) {
            getCarried().grow(overflow);
        } else if (!player.getInventory().add(template)) {
            player.drop(template, false);
        }
    }

    private void clampCarried(net.minecraft.world.entity.player.Player player) {
        ItemStack carried = getCarried();
        while (carried.getCount() > carried.getMaxStackSize()) {
            ItemStack excess = carried.split(carried.getMaxStackSize());
            if (!player.getInventory().add(excess)) player.drop(excess, false);
        }
        if (!getCarried().isEmpty() && getCarried().getCount() > getCarried().getMaxStackSize()) {
            ItemStack excess = getCarried().split(getCarried().getCount()
                - getCarried().getMaxStackSize());
            if (!player.getInventory().add(excess)) player.drop(excess, false);
        }
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

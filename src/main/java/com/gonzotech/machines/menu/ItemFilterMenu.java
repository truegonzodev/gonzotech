package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню ФИЛЬТРА: 3 ghost-слота (образцы) + инвентарь игрока.
 * <p>
 * Ghost-слоты — не настоящий инвентарь: клик по ним лишь ЗАДАЁТ образец (копию
 * предмета, что игрок держит на курсоре или, при пустом курсоре, очищает слот).
 * Предметы игрока при этом НЕ расходуются и НЕ забираются. Поэтому обычный
 * {@code quickMoveStack}/drag сюда не применяется — всё через {@link #clicked}.
 */
public class ItemFilterMenu extends AbstractContainerMenu {

    private final ItemFilterBlockEntity be;
    private final int filterSlots;

    /** Клиентский конструктор (из буфера — читаем позицию блока). */
    public ItemFilterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, readBE(inv, buf));
    }

    /** Серверный/общий конструктор. */
    public ItemFilterMenu(int id, Inventory inv, ItemFilterBlockEntity be) {
        super(ModMenus.ITEM_FILTER.get(), id);
        this.be = be;
        this.filterSlots = be.filterSize();

        // 3 ghost-слота по центру верхней зоны.
        int gx = 62;
        int gy = 20;
        for (int i = 0; i < filterSlots; i++) {
            addSlot(new GhostSlot(be, i, gx + i * 18, gy));
        }

        // Инвентарь игрока (стандартная раскладка).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 109));
        }
    }

    private static ItemFilterBlockEntity readBE(Inventory inv, RegistryFriendlyByteBuf buf) {
        var be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof ItemFilterBlockEntity f) return f;
        throw new IllegalStateException("Неверный BlockEntity для меню фильтра: " + be);
    }

    public ItemFilterBlockEntity blockEntity() {
        return be;
    }

    @Override
    public boolean stillValid(Player player) {
        return !be.isRemoved()
            && player.distanceToSqr(be.getBlockPos().getX() + 0.5,
                                    be.getBlockPos().getY() + 0.5,
                                    be.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    /**
     * Клик по слоту. Для ghost-слотов задаём образец из предмета на курсоре
     * (копия), либо очищаем (пустой курсор / правая кнопка). Курсор НЕ меняем.
     * Прочие слоты — обычное поведение.
     */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < filterSlots) {
            ItemStack carried = getCarried();
            if (clickType == ClickType.PICKUP) {
                if (dragType == 1 || carried.isEmpty()) {
                    // ПКМ или пустой курсор → очистить образец.
                    be.setFilter(slotId, ItemStack.EMPTY);
                } else {
                    // ЛКМ с предметом → задать образец (копия, курсор не тратится).
                    be.setFilter(slotId, carried);
                }
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    /** Shift-клик по слоту игрока задаёт образец в первый свободный ghost-слот. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < filterSlots) {
            return ItemStack.EMPTY; // из ghost ничего не «вынимается»
        }
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            for (int i = 0; i < filterSlots; i++) {
                if (be.getFilter(i).isEmpty()) {
                    be.setFilter(i, stack);
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Слот-образец: только отображает шаблон, взаимодействие через {@link #clicked}. */
    private static class GhostSlot extends Slot {
        private final ItemFilterBlockEntity filterBe;
        private final int idx;

        GhostSlot(ItemFilterBlockEntity be, int idx, int x, int y) {
            super(DUMMY, idx, x, y);
            this.filterBe = be;
            this.idx = idx;
        }

        @Override
        public ItemStack getItem() {
            return filterBe.getFilter(idx);
        }

        @Override
        public void set(ItemStack stack) {
            filterBe.setFilter(idx, stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // взаимодействие только через clicked()
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Пустой контейнер-заглушка для позиционирования ghost-слотов. */
    private static final net.minecraft.world.SimpleContainer DUMMY =
        new net.minecraft.world.SimpleContainer(ItemFilterBlockEntity.FILTER_SLOTS);
}

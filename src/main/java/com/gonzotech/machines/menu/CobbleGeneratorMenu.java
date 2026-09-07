package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.CobbleGeneratorBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню генератора булыжника: слот ведра лавы (требование), слот кирки (скорость),
 * слот выдачи (только забор), плюс шкалы GTU / воды. Плейсхолдер-раскладка —
 * художник задаёт финальные координаты слотов/текстуры.
 */
public class CobbleGeneratorMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 3;

    private final CobbleGeneratorBlockEntity be;

    public CobbleGeneratorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, CobbleGeneratorBlockEntity.class),
            new SimpleContainerData(4));
    }

    public CobbleGeneratorMenu(int id, Inventory inv, CobbleGeneratorBlockEntity be, ContainerData data) {
        super(ModMenus.COBBLE_GENERATOR.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        // Ведро лавы: только ведро лавы.
        addSlot(new FilteredSlot(be, CobbleGeneratorBlockEntity.SLOT_LAVA, 26, 24));
        // Кирка: только кирки.
        addSlot(new FilteredSlot(be, CobbleGeneratorBlockEntity.SLOT_PICKAXE, 26, 48));
        // Выдача: только забор. Сдвинут правее на 16px — между шкалой GTU и слотом
        // помещается горизонтальный прогресс-бар «вскапывания».
        addSlot(new OutputOnlySlot(be, CobbleGeneratorBlockEntity.SLOT_OUTPUT, 132, 35));

        addPlayerInventory(inv, 8, 84);
    }

    public CobbleGeneratorBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }

    public int water() {
        return data.get(1);
    }

    public int digProgress() {
        return data.get(2);
    }

    public int digTotal() {
        return data.get(3);
    }

    /** Слот, чей приём делегирован {@link Container#canPlaceItem} блок-сущности. */
    private static final class FilteredSlot extends Slot {
        private final Container backing;
        private final int slotIndex;

        FilteredSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.backing = container;
            this.slotIndex = slot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return backing.canPlaceItem(slotIndex, stack);
        }
    }
}

package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.PcfsozBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * Меню ПЦФСОЗ: один вход (жёлтый кек), гарантированный уран-238 и побочный
 * уран-235. Обычная вода не синхронизируется; клиенту нужны GTU, кипяток
 * и прогресс разделения.
 */
public class PcfsozMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 5;
    private final PcfsozBlockEntity be;

    public PcfsozMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, PcfsozBlockEntity.class),
            new SimpleContainerData(4));
    }

    public PcfsozMenu(int id, Inventory inventory, PcfsozBlockEntity be, ContainerData data) {
        super(ModMenus.PCFSOZ.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new Slot(be, PcfsozBlockEntity.SLOT_INPUT, 62, 35));
        addSlot(new OutputOnlySlot(be, PcfsozBlockEntity.SLOT_PRIMARY_OUTPUT, 116, 17));
        addSlot(new OutputOnlySlot(be, PcfsozBlockEntity.SLOT_BYPRODUCT_1, 152, 17));
        addSlot(new OutputOnlySlot(be, PcfsozBlockEntity.SLOT_BYPRODUCT_2, 116, 53));
        addSlot(new OutputOnlySlot(be, PcfsozBlockEntity.SLOT_BYPRODUCT_3, 152, 53));

        addPlayerInventory(inventory, 8, 84);
    }

    public PcfsozBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }

    public int hotWater() {
        return data.get(1);
    }

    public int washProgress() {
        return data.get(2);
    }

    public int washTotal() {
        return data.get(3);
    }

    /** UI value; сервер хранит точное число оплаченных тиков. */
    public int washProgressPercent() {
        int total = washTotal();
        return total <= 0 ? 0 : Math.min(100, (int) ((long) washProgress() * 100L / total));
    }
}

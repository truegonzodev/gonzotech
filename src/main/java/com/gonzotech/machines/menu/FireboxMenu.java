package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * Меню топки: слоты нагрузки (вход), топлива, выхода + шкала GTH (ContainerData).
 */
public class FireboxMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 3;

    private final FireboxBlockEntity be;

    /** Клиентский конструктор (из буфера). */
    public FireboxMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, FireboxBlockEntity.class), new SimpleContainerData(6));
    }

    /** Серверный конструктор. */
    public FireboxMenu(int id, Inventory inv, FireboxBlockEntity be, ContainerData data) {
        super(ModMenus.FIREBOX.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        // вход-нагрузка (верх), топливо (низ, только валидное топливо), выход (справа, только вывод)
        addSlot(new Slot(be, FireboxBlockEntity.SLOT_INPUT, 44, 17));
        addSlot(new FuelSlot(be, FireboxBlockEntity.SLOT_FUEL, 44, 53, inv));
        addSlot(new OutputOnlySlot(be, FireboxBlockEntity.SLOT_OUTPUT, 104, 35));

        addPlayerInventory(inv, 8, 84);
    }

    public FireboxBlockEntity blockEntity() {
        return be;
    }

    public int gth() {
        return data.get(0);
    }

    public int gthCapacity() {
        return data.get(1);
    }

    public int litTime() {
        return data.get(2);
    }

    public int litDuration() {
        return data.get(3);
    }

    public int cookProgress() {
        return data.get(4);
    }

    public int cookTotal() {
        return data.get(5);
    }
}

package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.BoilerBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * Меню парового котла: слот ведра воды (вход) и слот пустого ведра (выход),
 * плюс шкалы GTH / воды / пара и индикатор собранной цепочки.
 */
public class BoilerMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final BoilerBlockEntity be;

    public BoilerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, BoilerBlockEntity.class), new SimpleContainerData(4));
    }

    public BoilerMenu(int id, Inventory inv, BoilerBlockEntity be, ContainerData data) {
        super(ModMenus.BOILER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new Slot(be, BoilerBlockEntity.SLOT_WATER_IN, 44, 35));
        addSlot(new Slot(be, BoilerBlockEntity.SLOT_BUCKET_OUT, 44, 57));

        addPlayerInventory(inv, 8, 84);
    }

    public BoilerBlockEntity blockEntity() {
        return be;
    }

    public int gth() {
        return data.get(0);
    }

    public int water() {
        return data.get(1);
    }

    public int steam() {
        return data.get(2);
    }

    public boolean chainOk() {
        return data.get(3) != 0;
    }
}

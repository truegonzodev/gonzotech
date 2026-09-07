package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.BoilerBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню парового котла: слот ведра-провайдера воды (вход, только валидные вёдра)
 * и слот забора пустых вёдер (только вывод), плюс шкалы GTH / воды / пара.
 */
public class BoilerMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final BoilerBlockEntity be;

    public BoilerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, BoilerBlockEntity.class), new SimpleContainerData(3));
    }

    public BoilerMenu(int id, Inventory inv, BoilerBlockEntity be, ContainerData data) {
        super(ModMenus.BOILER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new WaterInputSlot(be, BoilerBlockEntity.SLOT_WATER_IN, 44, 35));
        addSlot(new OutputOnlySlot(be, BoilerBlockEntity.SLOT_BUCKET_OUT, 44, 57));

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
}

package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.PumpBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню помпы: слот входа пустой тары (ведро/пузырёк) и слот вывода наполненной
 * тары (только вывод), плюс шкалы GTU / воды. Полный реверс котла по слотам.
 */
public class PumpMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final PumpBlockEntity be;

    public PumpMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, PumpBlockEntity.class), new SimpleContainerData(2));
    }

    public PumpMenu(int id, Inventory inv, PumpBlockEntity be, ContainerData data) {
        super(ModMenus.PUMP.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new PumpInputSlot(be, PumpBlockEntity.SLOT_CONTAINER_IN, 44, 35));
        addSlot(new OutputOnlySlot(be, PumpBlockEntity.SLOT_FILLED_OUT, 44, 57));

        addPlayerInventory(inv, 8, 84);
    }

    public PumpBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }

    public int water() {
        return data.get(1);
    }
}

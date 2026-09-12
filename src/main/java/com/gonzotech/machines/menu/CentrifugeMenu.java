package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.CentrifugeBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/**
 * Меню ЦФ1УР: один ввод промывки, гарантированная пыль и три жёстко закреплённых
 * output slots. Обычная вода и пар намеренно не синхронизируются/не рисуются;
 * клиенту нужны только GTU, видимый кипяток и прогресс.
 */
public class CentrifugeMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 5;
    private final CentrifugeBlockEntity be;

    public CentrifugeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, CentrifugeBlockEntity.class),
            new SimpleContainerData(4));
    }

    public CentrifugeMenu(int id, Inventory inventory, CentrifugeBlockEntity be, ContainerData data) {
        super(ModMenus.CENTRIFUGE.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new Slot(be, CentrifugeBlockEntity.SLOT_INPUT, 56, 35));
        addSlot(new OutputOnlySlot(be, CentrifugeBlockEntity.SLOT_PRIMARY_OUTPUT, 110, 17));
        addSlot(new OutputOnlySlot(be, CentrifugeBlockEntity.SLOT_BYPRODUCT_1, 132, 17));
        addSlot(new OutputOnlySlot(be, CentrifugeBlockEntity.SLOT_BYPRODUCT_2, 110, 53));
        addSlot(new OutputOnlySlot(be, CentrifugeBlockEntity.SLOT_BYPRODUCT_3, 132, 53));

        addPlayerInventory(inventory, 8, 84);
    }

    public CentrifugeBlockEntity blockEntity() {
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

    /** UI value; the server keeps the exact paid-tick count internally. */
    public int washProgressPercent() {
        int total = washTotal();
        return total <= 0 ? 0 : Math.min(100, (int) ((long) washProgress() * 100L / total));
    }
}

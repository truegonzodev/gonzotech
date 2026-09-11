package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.CrusherBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/** Дробилка использует компоновку ЦФ: один input и четыре защищённых outputs. */
public class CrusherMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 5;
    private final CrusherBlockEntity be;

    public CrusherMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, MenuHelper.readBlockEntity(inventory, buffer, CrusherBlockEntity.class), new SimpleContainerData(3));
    }

    public CrusherMenu(int id, Inventory inventory, CrusherBlockEntity be, ContainerData data) {
        super(ModMenus.CRUSHER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addSlot(new Slot(be, CrusherBlockEntity.SLOT_INPUT, 56, 35));
        addSlot(new OutputOnlySlot(be, CrusherBlockEntity.SLOT_PRIMARY_OUTPUT, 110, 17));
        addSlot(new OutputOnlySlot(be, CrusherBlockEntity.SLOT_SECONDARY_OUTPUT, 132, 17));
        addSlot(new OutputOnlySlot(be, CrusherBlockEntity.SLOT_BYPRODUCT_1, 110, 53));
        addSlot(new OutputOnlySlot(be, CrusherBlockEntity.SLOT_BYPRODUCT_2, 132, 53));
        addPlayerInventory(inventory, 8, 84);
    }

    public CrusherBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }

    public int crushProgress() {
        return data.get(1);
    }

    public int crushTotal() {
        return data.get(2);
    }

    /** Display value for the GUI; internals retain fixed-point progress units. */
    public int crushProgressPercent() {
        int total = crushTotal();
        return total <= 0 ? 0 : Math.min(100, (int) ((long) crushProgress() * 100L / total));
    }
}

package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SnaketypeCondenserBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню Змеевикового конденсатора: 0 слотов предметов, 2 шкалы (кипяток, вода),
 * 1 хитбокс тултипа охлаждения.
 */
public class SnaketypeCondenserMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;
    public static final int DATA_COUNT = 8;

    private final SnaketypeCondenserBlockEntity be;

    public SnaketypeCondenserMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, SnaketypeCondenserBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public SnaketypeCondenserMenu(int id, Inventory inv, SnaketypeCondenserBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_SNAKETYPE_CONDENSER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addPlayerInventory(inv, 8, 84);
    }

    public SnaketypeCondenserBlockEntity blockEntity() {
        return be;
    }

    public int boilingWater() {
        return data.get(0);
    }

    public int maxBoilingWater() {
        return SnaketypeCondenserBlockEntity.BOILING_WATER_CAPACITY;
    }

    public int water() {
        return data.get(1);
    }

    public int maxWater() {
        return SnaketypeCondenserBlockEntity.WATER_CAPACITY;
    }

    public int coolingRate() {
        return data.get(2);
    }

    public int regularIce() {
        return data.get(3);
    }

    public int packedIce() {
        return data.get(4);
    }

    public int europanIce() {
        return data.get(5);
    }

    public int blueIce() {
        return data.get(6);
    }

    public int superdenseIce() {
        return data.get(7);
    }
}

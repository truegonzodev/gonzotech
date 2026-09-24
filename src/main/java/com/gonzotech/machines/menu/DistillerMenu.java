package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.DistillerBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню Дистиллятора: 0 слотов предметов, 5 шкал (GTH, вода, кипяток, входное сырьё, выход).
 */
public class DistillerMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;
    public static final int DATA_COUNT = 9;

    private final DistillerBlockEntity be;

    public DistillerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, DistillerBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public DistillerMenu(int id, Inventory inv, DistillerBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_DISTILLER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addPlayerInventory(inv, 8, 84);
    }

    public DistillerBlockEntity blockEntity() {
        return be;
    }

    public int gth() {
        return data.get(1) * 10_000 + data.get(0);
    }

    public int maxGth() {
        return DistillerBlockEntity.GTH_CAPACITY;
    }

    public int water() {
        return data.get(2);
    }

    public int boilingWater() {
        return data.get(3);
    }

    public int rawAmount() {
        return data.get(4);
    }

    public double rawAlcohol() {
        return (double) data.get(5) / 100.0;
    }

    public double rawRot() {
        return (double) data.get(6) / 100.0;
    }

    public int distillate() {
        return data.get(7);
    }

    public int poison() {
        return data.get(8);
    }
}

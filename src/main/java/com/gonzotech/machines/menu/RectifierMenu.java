package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.RectifierBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню Ректификатора: 0 слотов предметов, 4 шкалы (GTU, GTH, дистиллят, ректификат).
 */
public class RectifierMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 0;
    public static final int DATA_COUNT = 6;

    private final RectifierBlockEntity be;

    public RectifierMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, RectifierBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public RectifierMenu(int id, Inventory inv, RectifierBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_RECTIFIER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        addPlayerInventory(inv, 8, 84);
    }

    public RectifierBlockEntity blockEntity() {
        return be;
    }

    public int gth() {
        return data.get(1) * 10_000 + data.get(0);
    }

    public int maxGth() {
        return RectifierBlockEntity.GTH_CAPACITY;
    }

    public int gtu() {
        return data.get(3) * 10_000 + data.get(2);
    }

    public int maxGtu() {
        return RectifierBlockEntity.GTU_CAPACITY;
    }

    public int distillate() {
        return data.get(4);
    }

    public int rectificate() {
        return data.get(5);
    }
}

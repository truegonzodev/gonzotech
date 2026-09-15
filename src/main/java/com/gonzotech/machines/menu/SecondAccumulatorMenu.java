package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondAccumulatorBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Menu for the level-II accumulator; its two data slots encode a 48 900 GTU buffer safely. */
public final class SecondAccumulatorMenu extends BaseMachineMenu {

    private final SecondAccumulatorBlockEntity be;

    public SecondAccumulatorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, SecondAccumulatorBlockEntity.class), new SimpleContainerData(2));
    }

    public SecondAccumulatorMenu(int id, Inventory inv, SecondAccumulatorBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_ACCUMULATOR.get(), id, be, data, 0);
        this.be = be;
        addPlayerInventory(inv, 8, 84);
    }

    public SecondAccumulatorBlockEntity blockEntity() {
        return be;
    }

    /** Full GTU amount decoded from [remainder, thousands] ContainerData slots. */
    public int gtu() {
        return data.get(1) * 1_000 + data.get(0);
    }
}

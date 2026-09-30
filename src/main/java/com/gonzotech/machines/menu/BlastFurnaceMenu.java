package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Меню доменной печи (0.3.65): пять топливных слотов в один ряд (44..116, 53),
 * GTH-шкала (8, 17, 16×52) и бар burnout (80, 35, 16×16) — по раскладке автора.
 * Слоты только под топливо; забор автоматизацией запрещён на стороне BE.
 */
public final class BlastFurnaceMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 5;

    private final FireboxBlockEntity firebox;

    public BlastFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, MenuHelper.readBlockEntity(inventory, buffer, FireboxBlockEntity.class),
            new SimpleContainerData(6));
    }

    public BlastFurnaceMenu(int id, Inventory inventory, FireboxBlockEntity firebox, ContainerData data) {
        super(ModMenus.BLAST_FURNACE.get(), id, firebox, data, MACHINE_SLOTS);
        this.firebox = firebox;
        for (int i = 0; i < FireboxBlockEntity.FUEL_SLOTS.length; i++) {
            addSlot(new FuelSlot(firebox, FireboxBlockEntity.FUEL_SLOTS[i], 44 + i * 18, 53, inventory));
        }
        addPlayerInventory(inventory, 8, 84);
    }

    public FireboxBlockEntity blockEntity() {
        return firebox;
    }

    public int gth() {
        return data.get(1) * 1_000 + data.get(0);
    }

    public int litTime() {
        return data.get(3) * 1_000 + data.get(2);
    }

    public int litDuration() {
        return data.get(5) * 1_000 + data.get(4);
    }
}

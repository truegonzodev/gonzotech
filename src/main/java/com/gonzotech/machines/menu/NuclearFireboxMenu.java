package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.NuclearFireboxBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** One nuclear-fuel slot plus a two-part GTH amount and burn-time data. */
public final class NuclearFireboxMenu extends BaseMachineMenu {

    private final NuclearFireboxBlockEntity firebox;

    public NuclearFireboxMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, MenuHelper.readBlockEntity(inventory, buffer, NuclearFireboxBlockEntity.class),
            new SimpleContainerData(6));
    }

    public NuclearFireboxMenu(int id, Inventory inventory, NuclearFireboxBlockEntity firebox, ContainerData data) {
        super(ModMenus.SECOND_NUCLEAR_FIREBOX.get(), id, firebox, data, 1);
        this.firebox = firebox;
        // Kept at the original Firebox fuel coordinate until the dedicated sheet is painted.
        addSlot(new NuclearFuelSlot(firebox, NuclearFireboxBlockEntity.SLOT_FUEL, 80, 53));
        addPlayerInventory(inventory, 8, 84);
    }

    public NuclearFireboxBlockEntity blockEntity() {
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

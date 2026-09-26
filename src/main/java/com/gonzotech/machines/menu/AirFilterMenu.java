package com.gonzotech.machines.menu;

import com.gonzotech.cleanroom.AirFilterBlockEntity;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Client receives inventory/data packets; opening never depends on a client BE arriving first. */
public final class AirFilterMenu extends BaseMachineMenu {
    private final ContainerLevelAccess access;

    public AirFilterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(AirFilterBlockEntity.SLOT_COUNT),
                new SimpleContainerData(AirFilterBlockEntity.DATA_COUNT), buffer.readBlockPos());
    }

    public AirFilterMenu(int id, Inventory inventory, AirFilterBlockEntity filter, ContainerData data) {
        this(id, inventory, filter, data, filter.getBlockPos());
    }

    private AirFilterMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
        super(ModMenus.AIR_FILTER.get(), id, container, data, AirFilterBlockEntity.SLOT_COUNT);
        access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int slot = 0; slot < 3; slot++) {
            addSlot(new Slot(container, slot, 62 + slot * 18, 35) {
                @Override public boolean mayPlace(ItemStack stack) { return AirFilterBlockEntity.isSupply(stack); }
            });
        }
        // Same layout as the other machines: machine grid above player inventory.
        addPlayerInventory(inventory, 8, 84);
    }

    public int gtuMilli() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    public int coalUsedHundredths() { return data.get(2); }
    public int catalystUsedHundredths() { return data.get(3); }
    public int qualityHundredths() { return data.get(4); }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player) && stillValid(access, player, ModBlocks.AIR_FILTER.get());
    }
}

package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ThirdLeadChestBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Меню свинцового ящика: один ряд из 9 слотов (копия бочки, без второй строки).
 */
public class ThirdLeadChestMenu extends BaseMachineMenu {

    private static final int CHEST_SLOTS = 9;

    public ThirdLeadChestMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, ThirdLeadChestBlockEntity.class), new SimpleContainerData(0));
    }

    public ThirdLeadChestMenu(int id, Inventory inv, ThirdLeadChestBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_LEAD_CHEST.get(), id, be, data, CHEST_SLOTS);
        for (int i = 0; i < CHEST_SLOTS; i++) {
            addSlot(new net.minecraft.world.inventory.Slot(be, i, 8 + i * 18, 18));
        }
        addPlayerInventory(inv, 8, 84);
    }

    public ThirdLeadChestBlockEntity blockEntity() {
        return (ThirdLeadChestBlockEntity) container;
    }
}

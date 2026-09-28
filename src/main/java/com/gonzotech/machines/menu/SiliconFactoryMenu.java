package com.gonzotech.machines.menu;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.litho.SiliconFactoryBlockEntity;
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

/**
 * Раскладка по PNG автора (лист 512×512 @ −128, панель 176×166):
 * 3 входа (x 61/97/133, y 17), 4 выхода (x 43/79/115/151, y 53),
 * вертикальная шкала прогресса (8, 17) 16×52, инвентарь игрока (8, 84).
 */
public final class SiliconFactoryMenu extends BaseMachineMenu {
    private final ContainerLevelAccess access;

    public SiliconFactoryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(SiliconFactoryBlockEntity.SLOT_COUNT),
            new SimpleContainerData(SiliconFactoryBlockEntity.DATA_COUNT), buffer.readBlockPos());
    }

    public SiliconFactoryMenu(int id, Inventory inventory, SiliconFactoryBlockEntity factory, ContainerData data) {
        this(id, inventory, factory, data, factory.getBlockPos());
    }

    private SiliconFactoryMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
        super(ModMenus.SILICON_FACTORY.get(), id, container, data, SiliconFactoryBlockEntity.SLOT_COUNT);
        access = ContainerLevelAccess.create(inventory.player.level(), pos);
        for (int i = 0; i < SiliconFactoryBlockEntity.INPUT_SLOTS; i++) {
            int x = 61 + i * 36;
            addSlot(new Slot(container, i, x, 17) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.CHIP_SOUP.get());
                }
            });
        }
        for (int i = 0; i < SiliconFactoryBlockEntity.OUTPUT_SLOTS; i++) {
            int x = 43 + i * 36;
            addSlot(new Slot(container, SiliconFactoryBlockEntity.INPUT_SLOTS + i, x, 53) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addPlayerInventory(inventory, 8, 84);
    }

    public int progress() {
        return data.get(0);
    }

    public int progressTotal() {
        return SiliconFactoryBlockEntity.PROGRESS_TOTAL;
    }

    /** 0 — вариант ещё не синхронизирован; 1..3 — вид чипа структуры. */
    public int variant() {
        return data.get(1);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player)
            && stillValid(access, player, ModBlocks.THIRD_SILICON_FACTORY.get());
    }
}

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
 * Раскладка по PNG автора (лист 512×512 @ −128, панель 176×166), канва 176×166:
 * ГТУ-шкала (136,145) 16×52; три бара 16×34 (190/226/262, y163); нижний
 * конвейер (y181): вход 172 → транзит 208 → транзит 244 → выход 280;
 * шлак-слоты (y145): 190/226/262. Инвентарь игрока (8, 84).
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
        // Конвейер (y=181): вход — «суп-набор», класть и брать.
        addSlot(new Slot(container, SiliconFactoryBlockEntity.INPUT_SLOT, 172, 181) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.CHIP_SOUP.get());
            }
        });
        // Транзит заготовки: нельзя ни положить, ни достать.
        for (int i = SiliconFactoryBlockEntity.TRANSIT_FIRST; i <= SiliconFactoryBlockEntity.TRANSIT_LAST; i++) {
            int index = i;
            addSlot(new Slot(container, index, 172 + index * 36, 181) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        // Выход: только достать.
        addSlot(new Slot(container, SiliconFactoryBlockEntity.OUTPUT_SLOT, 280, 181) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        // Шлак шагов 0..2 (y=145): только достать.
        for (int i = 0; i < 3; i++) {
            addSlot(new Slot(container, SiliconFactoryBlockEntity.SLAG_BASE + i, 190 + i * 36, 145) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addPlayerInventory(inventory, 8, 84);
    }

    /** Слоты 1/2 — транзит заготовки: Shift-щелчок тоже запрещён. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == SiliconFactoryBlockEntity.TRANSIT_FIRST || index == SiliconFactoryBlockEntity.TRANSIT_LAST) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    public long gtuMilli() {
        return ((long) data.get(1) << 16) | (data.get(0) & 0xffffL);
    }

    /** -1 — простой; 0..2 — активный шаг. */
    public int step() {
        return data.get(2);
    }

    public int progressTicks() {
        return data.get(3);
    }

    /** Чистота воздуха в сотых долях процента; -1 — вне контура («обычный»). */
    public int qualityHundredths() {
        return data.get(4);
    }

    /** 0 — вариант ещё не синхронизирован; 1..3 — вид чипа структуры. */
    public int variant() {
        return data.get(5);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player)
            && stillValid(access, player, ModBlocks.THIRD_SILICON_FACTORY.get());
    }
}

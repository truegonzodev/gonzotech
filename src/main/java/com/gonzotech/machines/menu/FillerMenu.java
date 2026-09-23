package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.FillerBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню Наполнителя:
 * <ul>
 *   <li>Слоты левого бака: верхний (62, 17) [холст 190, 145], нижний (62, 53) [холст 190, 181];</li>
 *   <li>Слоты правого бака: верхний (98, 17) [холст 226, 145], нижний (98, 53) [холст 226, 181];</li>
 *   <li>Сетка ингредиентов 2×3: (134, 17), (134, 35), (134, 53), (152, 17), (152, 35), (152, 53);</li>
 *   <li>Кнопка смены баков: clickMenuButton(0).</li>
 * </ul>
 */
public class FillerMenu extends BaseMachineMenu {

    public static final int MACHINE_SLOTS = 10;
    public static final int DATA_COUNT = 11;

    private final FillerBlockEntity be;

    public FillerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, FillerBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public FillerMenu(int id, Inventory inv, FillerBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_FILLER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        // 0: Левый бак верхний (тара)
        addSlot(new Slot(be, 0, 62, 17));
        // 1: Левый бак нижний (выход / соль)
        addSlot(new Slot(be, 1, 62, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // 2: Правый бак верхний (тара)
        addSlot(new Slot(be, 2, 98, 17));
        // 3: Правый бак нижний (выход / хлорид кальция)
        addSlot(new Slot(be, 3, 98, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Сетка 2×3 под ингредиенты (слоты 4..9)
        // Колонка 1 (X = 134)
        addSlot(new Slot(be, 4, 134, 17));
        addSlot(new Slot(be, 5, 134, 35));
        addSlot(new Slot(be, 6, 134, 53));
        // Колонка 2 (X = 152)
        addSlot(new Slot(be, 7, 152, 17));
        addSlot(new Slot(be, 8, 152, 35));
        addSlot(new Slot(be, 9, 152, 53));

        addPlayerInventory(inv, 8, 84);
    }

    public FillerBlockEntity blockEntity() {
        return be;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && be != null) {
            be.swapTanks(player);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public int gth() {
        return data.get(0);
    }

    public int maxGth() {
        return FillerBlockEntity.GTH_CAPACITY;
    }

    public int gtu() {
        return data.get(1);
    }

    public int maxGtu() {
        return FillerBlockEntity.GTU_CAPACITY;
    }

    public int leftFluidType() {
        return data.get(2);
    }

    public int leftFluidAmount() {
        return data.get(3);
    }

    public int leftSaltMb() {
        return data.get(4);
    }

    public int rightFluidType() {
        return data.get(5);
    }

    public int rightFluidAmount() {
        return data.get(6);
    }

    public int rightSaltMb() {
        return data.get(7);
    }

    public int smeltProgress() {
        return data.get(8);
    }

    public int smeltTotal() {
        return data.get(9);
    }

    public int activeRecipe() {
        return data.get(10);
    }
}

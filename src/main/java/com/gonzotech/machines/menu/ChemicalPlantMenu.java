package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ChemicalPlantBlockEntity;
import com.gonzotech.machines.processing.ChemicalPlantRecipes;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню Химического завода:
 * <ul>
 *   <li>Шкала GTU: (8, 17) 16×52;</li>
 *   <li>3 слота катализаторов: (30, 17), (30, 35), (30, 53);</li>
 *   <li>Сетка ингредиентов 3×3: X: 58, 76, 94; Y: 17, 35, 53;</li>
 *   <li>Стрелка прогресса chemical: (116, 35) 24×16;</li>
 *   <li>Слот выхода: (148, 35).</li>
 * </ul>
 */
public class ChemicalPlantMenu extends BaseMachineMenu {

    public static final int MACHINE_SLOTS = 13;
    public static final int DATA_COUNT = 3;

    private final ChemicalPlantBlockEntity be;

    public ChemicalPlantMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, ChemicalPlantBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public ChemicalPlantMenu(int id, Inventory inv, ChemicalPlantBlockEntity be, ContainerData data) {
        super(ModMenus.CHEMICAL_PLANT.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        // 0..2: Слоты катализаторов (платиновые/палладиевые самородки)
        addSlot(new Slot(be, 0, 30, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ChemicalPlantRecipes.isCatalyst(stack);
            }
        });
        addSlot(new Slot(be, 1, 30, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ChemicalPlantRecipes.isCatalyst(stack);
            }
        });
        addSlot(new Slot(be, 2, 30, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ChemicalPlantRecipes.isCatalyst(stack);
            }
        });

        // 3..11: Сетка ингредиентов 3×3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = 3 + row * 3 + col;
                addSlot(new Slot(be, slotIndex, 58 + col * 18, 17 + row * 18));
            }
        }

        // 12: Слот выхода (готовая продукция)
        addSlot(new Slot(be, 12, 148, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        addPlayerInventory(inv, 8, 84);
    }

    public ChemicalPlantBlockEntity blockEntity() {
        return be;
    }

    public int gtu() {
        return data.get(0);
    }

    public int maxGtu() {
        return 2600;
    }

    public int progress() {
        return data.get(1);
    }

    public int total() {
        return data.get(2);
    }

    public int progressPercent() {
        int tot = total();
        return tot <= 0 ? 0 : Math.min(100, progress() * 100 / tot);
    }
}

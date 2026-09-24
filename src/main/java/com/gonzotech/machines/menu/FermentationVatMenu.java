package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.FermentationVatBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню Бродильного чана: 1 слот органики (80, 35) и шкалы GTH и браги.
 */
public class FermentationVatMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 1;
    public static final int DATA_COUNT = 7;

    private final FermentationVatBlockEntity be;

    public FermentationVatMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, FermentationVatBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public FermentationVatMenu(int id, Inventory inv, FermentationVatBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_FERMENTATION_VAT.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        // Слот органики (80, 35) — абсолютные координаты на холсте (208, 163)
        addSlot(new Slot(be, FermentationVatBlockEntity.SLOT_ORGANIC, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FermentationVatBlockEntity.isPlantOrganic(stack);
            }
        });

        addPlayerInventory(inv, 8, 84);
    }

    public FermentationVatBlockEntity blockEntity() {
        return be;
    }

    public int gth() {
        return data.get(1) * 10_000 + data.get(0);
    }

    public int maxGth() {
        return data.get(3) * 10_000 + data.get(2);
    }

    public int mashAmount() {
        return data.get(4);
    }

    public double mashAlcohol() {
        return (double) data.get(5) / 100.0;
    }

    public double mashRot() {
        return (double) data.get(6) / 100.0;
    }
}

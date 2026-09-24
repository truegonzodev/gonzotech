package com.gonzotech.machines.menu;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.WortKettleBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Меню Сусловарочного котла: 1 слот тары под пиво (26, 35) и шкалы браги, сусла, GTH.
 */
public class WortKettleMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 1;
    public static final int DATA_COUNT = 7;

    private final WortKettleBlockEntity be;

    public WortKettleMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, MenuHelper.readBlockEntity(inv, buf, WortKettleBlockEntity.class), new SimpleContainerData(DATA_COUNT));
    }

    public WortKettleMenu(int id, Inventory inv, WortKettleBlockEntity be, ContainerData data) {
        super(ModMenus.THIRD_WORT_KETTLE.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;

        // Слот тары под пиво (26, 35) — абсолютные координаты на холсте (154, 163)
        addSlot(new Slot(be, WortKettleBlockEntity.SLOT_CONTAINER, 26, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET)
                    || stack.is(ModItems.BEER_MUG.get()) || stack.is(ModItems.BEER_BUCKET.get());
            }
        });

        addPlayerInventory(inv, 8, 84);
    }

    public WortKettleBlockEntity blockEntity() {
        return be;
    }

    public int gth() {
        return data.get(1) * 10_000 + data.get(0);
    }

    public int maxGth() {
        return WortKettleBlockEntity.GTH_CAPACITY;
    }

    public int mashAmount() {
        return data.get(2);
    }

    public double mashAlcohol() {
        return (double) data.get(3) / 100.0;
    }

    public double mashRot() {
        return (double) data.get(4) / 100.0;
    }

    public int wortAmount() {
        return data.get(5);
    }

    public double wortAlcohol() {
        return (double) data.get(6) / 100.0;
    }
}

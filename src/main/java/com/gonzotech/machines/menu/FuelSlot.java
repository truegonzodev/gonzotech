package com.gonzotech.machines.menu;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Слот, принимающий только предметы с ненулевым временем горения (топливо).
 * <p>
 * Проверка авторитетно ведётся на сервере через {@code ServerLevel#fuelValues()};
 * на клиенте слот разрешает вставку (предикция), а сервер её перепроверит.
 */
public class FuelSlot extends Slot {

    private final Inventory playerInv;

    public FuelSlot(Container container, int slot, int x, int y, Inventory playerInv) {
        super(container, slot, x, y);
        this.playerInv = playerInv;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        Level level = playerInv.player.level();
        if (level instanceof ServerLevel server) {
            return stack.getBurnTime(RecipeType.SMELTING, server.fuelValues()) > 0;
        }
        return true;
    }
}

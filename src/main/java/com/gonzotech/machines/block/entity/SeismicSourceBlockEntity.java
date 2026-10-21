package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.menu.SeismicSourceMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Источник колебаний: контейнер ровно на один слот, в который помещается
 * только одна единица динамита (ванильный TNT). Расход динамита по кнопке
 * «Произвести импульс» — следующий сейсмический пакет.
 */
public final class SeismicSourceBlockEntity extends BaseMachineBlockEntity
    implements WorldlyContainer {

    public static final int SLOT_DYNAMITE = 0;
    public static final int CONTAINER_SIZE = 1;

    private static final int[] SLOTS = {SLOT_DYNAMITE};

    public SeismicSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEISMIC_SOURCE.get(), pos, state, CONTAINER_SIZE);
    }

    /** В единственный слот источника допустим только динамит (TNT). */
    public static boolean isDynamite(ItemStack stack) {
        return stack.is(Items.TNT);
    }

    public boolean hasDynamite() {
        return isDynamite(items.get(SLOT_DYNAMITE));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Количество ограничивают getMaxStackSize()/слот меню: сюда автоматика
        // может приходить со стопкой, из которой будет взята ровно одна единица.
        return slot == SLOT_DYNAMITE && isDynamite(stack);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != SLOT_DYNAMITE) return;
        if (!stack.isEmpty() && !isDynamite(stack)) return;
        super.setItem(slot, stack.isEmpty()
            ? ItemStack.EMPTY
            : stack.copyWithCount(Math.min(1, stack.getCount())));
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!items.isEmpty()) {
            ItemStack loaded = items.get(SLOT_DYNAMITE);
            if (!loaded.isEmpty() && !isDynamite(loaded)) {
                items.set(SLOT_DYNAMITE, ItemStack.EMPTY);
            } else if (loaded.getCount() > 1) {
                items.set(SLOT_DYNAMITE, loaded.copyWithCount(1));
            }
        }
    }

    // Автоматика может только зарядить источник одной единицей динамита;
    // обратно трубы/воронки заряд не высасывают.
    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SeismicSourceMenu(id, inventory, this);
    }
}

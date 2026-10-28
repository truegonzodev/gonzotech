package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.menu.SeismicSourceMenu;
import com.gonzotech.machines.inventory.SlotCapacity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Источник колебаний: контейнер ровно на один слот, в который помещается
 * только одна единица динамита (ванильный TNT). Расход динамита по кнопке
 * «Произвести импульс» — следующий сейсмический пакет.
 */
public final class SeismicSourceBlockEntity extends BaseMachineBlockEntity
    implements WorldlyContainer, SlotCapacity {

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
        // 0.3.299 (N7): занятый слот отказывает: вторая единица TNT ни автоматике,
        // ни игроку не кладётся; раньше излишек срезался в setItem молча (десятки
        // исчезали при загрузке/забивке).
        return slot == SLOT_DYNAMITE && isDynamite(stack)
            && items.get(SLOT_DYNAMITE).isEmpty();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != SLOT_DYNAMITE) return;
        if (!stack.isEmpty() && !isDynamite(stack)) return;
        SlotCapacity.requireReplacement(this, slot, stack);
        super.setItem(slot, stack);
    }

    @Override
    public int slotCapacity(int slot) {
        return slot == SLOT_DYNAMITE ? 1 : 0;
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
                // 0.3.299 (N7): перегруз на загрузке сейва — не обрезка:
                // излишек копится в loadSurplus и сыплется на землю первым
                // тиком (во время загрузки чанка дропать сущности нельзя).
                loadSurplus = loaded.getCount() - 1;
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

    /** TNT сверх единицы, поднятый из сейва: очередь наземного дропа. */
    private int loadSurplus;

    /** 0.3.299 (N7): тикер нужен только наземному сбросу loadSurplus. */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
            SeismicSourceBlockEntity be) {
        if (be.loadSurplus <= 0 || level == null) return;
        int n = be.loadSurplus;
        be.loadSurplus = 0;
        net.minecraft.world.level.block.Block.popResource(level, pos,
            new ItemStack(Items.TNT, n));
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

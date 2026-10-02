package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.block.ThirdLeadChestBlock;
import com.gonzotech.machines.menu.ThirdLeadChestMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность свинцового ящика: ровно 9 слотов (один ряд), автоматизация —
 * все слоты в обе стороны (копия бочки). Счётчик зрителей управляет свойством
 * OPEN и звуками крышки, как у ванильной бочки.
 */
public class ThirdLeadChestBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_COUNT = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int openers;

    public ThirdLeadChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_LEAD_CHEST.get(), pos, state);
    }

    // ─────────────────────────── Container ───────────────────────────

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), getMaxStackSize()))));
        setChanged();
    }

    @Override public int getMaxStackSize() { return 64; }

    @Override
    public boolean stillValid(Player player) {
        return level != null && !isRemoved() && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    // ─────────────────────── WorldlyContainer ───────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    // ─── зрители: свойство OPEN + звуки крышки, как у ванильной бочки ───

    @Override
    public void startOpen(Player player) {
        if (level == null || isRemoved()) return;
        if (++openers == 1) {
            setLid(true, SoundEvents.BARREL_OPEN);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (level == null) {
            openers = Math.max(0, openers - 1);
            return;
        }
        openers = Math.max(0, openers - 1);
        if (openers == 0) {
            setLid(false, SoundEvents.BARREL_CLOSE);
        }
    }

    private void setLid(boolean open, net.minecraft.sounds.SoundEvent sound) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof ThirdLeadChestBlock
                && state.getValue(ThirdLeadChestBlock.OPEN) != open) {
            level.setBlock(worldPosition, state.setValue(ThirdLeadChestBlock.OPEN, open), 3);
        }
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    // ─────────────────────────── MenuProvider ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ThirdLeadChestMenu(id, inventory, this);
    }

    // ────────────────────────────── NBT ──────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        openers = 0;
    }
}

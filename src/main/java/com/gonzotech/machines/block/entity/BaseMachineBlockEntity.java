package com.gonzotech.machines.block.entity;

import com.gonzotech.radiation.CarrierItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Общая база для всех функциональных блоков паровой ветки.
 * <p>
 * Даёт:
 * <ul>
 *   <li>простой предметный инвентарь на N слотов ({@link Container});</li>
 *   <li>сериализацию предметов в NBT;</li>
 *   <li>{@link MenuProvider} — ПКМ по блоку откроет меню (реализуется наследником).</li>
 * </ul>
 * Ресурсные буферы (GTH/GTU/Steam/Water) и тик-логика — в наследниках.
 */
public abstract class BaseMachineBlockEntity extends BlockEntity implements Container, MenuProvider {

    protected final NonNullList<ItemStack> items;

    protected BaseMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    // ─────────────────────────── Container ───────────────────────────

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // Щипцы/ковш в слоте станка (0.3.79, EPOCH3-BASE §2.8): содержимое
        // появляется В ЭТОМ слоте, носитель выпадает на землю возле блока.
        if (level != null && !level.isClientSide()
                && stack.getItem() instanceof CarrierItem carrier && carrier.hasCarried(stack)) {
            ItemStack content = carrier.previewCarried(stack);
            ItemStack current = items.get(slot);
            boolean fits = current.isEmpty()
                || (ItemStack.isSameItemSameComponents(current, content)
                    && current.getCount() + content.getCount() <= current.getMaxStackSize());
            if (fits) {
                carrier.takeCarried(stack);
                if (current.isEmpty()) {
                    items.set(slot, content);
                } else {
                    current.grow(content.getCount());
                }
                net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack);
                setChanged();
                return;
            }
        }
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}

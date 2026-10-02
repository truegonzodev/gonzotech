package net.minecraft.world;
import net.minecraft.world.item.ItemStack;
/** Стаб: члены Container, используемые ItemRouting (сверены с использованием). */
public interface Container {
    int getContainerSize();
    ItemStack getItem(int slot);
    void setItem(int slot, ItemStack stack);
    ItemStack removeItem(int slot, int count);
    void setChanged();
    boolean canPlaceItem(int slot, ItemStack stack);
    int getMaxStackSize();
}

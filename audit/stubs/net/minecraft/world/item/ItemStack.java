package net.minecraft.world.item;
/** Стаб: члены, используемые сетевым пакетом (сверены с использованием в репо). */
public class ItemStack {
    public ItemStack() { }
    public boolean isEmpty() { return true; }
    public Item getItem() { return null; }
    public int getCount() { return 0; }
    public int getMaxStackSize() { return 64; }
    public ItemStack copy() { return new ItemStack(); }
    public void setCount(int count) { }
    public void grow(int by) { }
    public static boolean isSameItemSameComponents(ItemStack a, ItemStack b) { return false; }
}

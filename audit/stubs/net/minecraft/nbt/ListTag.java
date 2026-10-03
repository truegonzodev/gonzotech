package net.minecraft.nbt;
/** Стаб компилятора: NBT-список (только используемые члены). */
public class ListTag implements Tag {
    public ListTag() {}
    public ListTag add(Tag entry) { return this; }
    public CompoundTag getCompound(int index) { return new CompoundTag(); }
    public int size() { return 0; }
}

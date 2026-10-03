package net.minecraft.nbt;
/** Стаб компилятора: составной NBT-тег (только используемые члены). */
public class CompoundTag implements Tag {
    public CompoundTag() {}
    public CompoundTag putLong(String key, long value) { return this; }
    public CompoundTag putString(String key, String value) { return this; }
    public CompoundTag put(String key, Tag value) { return this; }
    public long getLong(String key) { return 0; }
    public String getString(String key) { return ""; }
    public long[] getLongArray(String key) { return new long[0]; }
    public ListTag getList(String key, int type) { return new ListTag(); }
    public boolean contains(String key) { return false; }
    public boolean contains(String key, int type) { return false; }
}

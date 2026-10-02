package net.minecraft.core;
/** Стаб типа для локального типчека (сигнатуры сверены сборкой автора 04.10.2026). */
public class BlockPos {
    public BlockPos(int x, int y, int z) { }
    public long asLong() { return 0L; }
    public static BlockPos of(long packed) { return new BlockPos(0, 0, 0); }
    public BlockPos relative(net.minecraft.core.Direction dir) { return new BlockPos(0, 0, 0); }
    public int getX() { return 0; }
    public int getY() { return 0; }
    public int getZ() { return 0; }
    @Override public boolean equals(Object o) { return false; }
    @Override public int hashCode() { return 0; }
}

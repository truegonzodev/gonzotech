package net.minecraft.world.level;

/** Typecheck-only signatures used by Containment. */
public class ChunkPos {
    public ChunkPos(net.minecraft.core.BlockPos pos) { }
    public long toLong() { return 0L; }
    public static long asLong(int x, int z) { return 0L; }
    public static int getX(long packed) { return 0; }
    public static int getZ(long packed) { return 0; }
}

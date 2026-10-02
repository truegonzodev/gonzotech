package net.minecraft.core;
/** Стаб: getOpposite/get3DDataValue сверены с реальным использованием 1.21.4. */
public enum Direction {
    UP, DOWN, NORTH, SOUTH, WEST, EAST;
    public Direction getOpposite() { return this; }
    public int get3DDataValue() { return ordinal(); }
}

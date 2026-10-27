package net.minecraft.core;
/** Стаб: члены, используемые сетевым пакетом (сверены с реальным использованием). */
public enum Direction {
    UP, DOWN, NORTH, SOUTH, WEST, EAST;
    public Direction getOpposite() { return this; }
    public int get3DDataValue() { return ordinal(); }
    public int getStepX() { return 0; }
    public int getStepY() { return 0; }
    public int getStepZ() { return 0; }
    public Axis getAxis() { return Axis.Y; }
    public enum Axis { X, Y, Z }
}

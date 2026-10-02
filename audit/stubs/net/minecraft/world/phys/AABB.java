package net.minecraft.world.phys;
public class AABB {
    public final double minX, minY, minZ, maxX, maxY, maxZ;
    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        minX = x1; minY = y1; minZ = z1; maxX = x2; maxY = y2; maxZ = z2;
    }
}

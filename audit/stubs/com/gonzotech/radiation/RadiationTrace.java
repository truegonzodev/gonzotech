package com.gonzotech.radiation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
public class RadiationTrace {
 public static boolean isActive(ServerLevel level, BlockPos pos) { return false; }
 public static void log(ServerLevel level, BlockPos pos, String kind, String details) {}
 public static String rate(double nZt) { return ""; }
}

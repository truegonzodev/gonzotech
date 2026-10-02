package com.gonzotech.machines.turbine;
import com.gonzotech.machines.energy.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
/** Стаб резолверов портов (сигнатуры реальных методов 0.3.90). */
public final class TurbineStructure {
    public static Transfer.Receiver steamReceiverAt(Level level, BlockPos pos) { return null; }
    public static boolean isMember(Level level, BlockPos pos) { return false; }
    private TurbineStructure() { }
}

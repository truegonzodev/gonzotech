package com.gonzotech.machines.steamgen;
import com.gonzotech.machines.energy.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
/** Стаб резолверов портов (сигнатуры реальных методов 0.3.90). */
public final class SteamGenStructure {
    public static Transfer.Receiver waterReceiverAt(Level level, BlockPos pos) { return null; }
    public static Transfer.Receiver gthReceiverAt(Level level, BlockPos pos) { return null; }
    public static void portPlaced(Level level, BlockPos pos) { }
    public static void portRemoved(Level level, BlockPos pos) { }
    /** 0.3.104: реальная сигнатура (гвард «порт сформированного мультиблока»). */
    public static boolean isMember(Level level, BlockPos pos) { return false; }
    private SteamGenStructure() { }
}

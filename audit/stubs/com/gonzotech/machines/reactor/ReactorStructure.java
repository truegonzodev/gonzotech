package com.gonzotech.machines.reactor;
import com.gonzotech.machines.energy.Transfer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
public class ReactorStructure {
 public static Transfer.Receiver waterReceiverAt(Level level, BlockPos pos) { return null; }
 /** 0.3.335 REACT-VAL1: реальная сигнатура гварда «узел в корпусе реактора — порт». */
 public static boolean isMember(Level level, BlockPos pos) { return false; }
 /** 0.3.336 D-PORTMODE1: реальная сигнатура потолка GTH-порта реактора. */
 public static long gthPortLimit(Level level, BlockPos pos) { return 0L; }
}

package com.gonzotech.machines.network;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
/** Стаб: carriesUniversalFluid сверен с реальным классом (static boolean). */
public class CompositePipeBlock extends Block {
    public static boolean carriesUniversalFluid(BlockState state) { return false; }
}

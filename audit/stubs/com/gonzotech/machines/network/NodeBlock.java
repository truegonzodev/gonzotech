package com.gonzotech.machines.network;
import net.minecraft.world.level.block.Block;
/** Стаб для kindOf (реальный NodeBlock не компилируется гейтом; pipeType сверен). */
public class NodeBlock extends Block {
    public PipeType pipeType() { return null; }
}

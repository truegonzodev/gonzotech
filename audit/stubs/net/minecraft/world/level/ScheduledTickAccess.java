package net.minecraft.world.level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
public interface ScheduledTickAccess {
    void scheduleTick(BlockPos pos, Block block, int delay);
    void scheduleTick(BlockPos pos, Fluid fluid, int delay);
}

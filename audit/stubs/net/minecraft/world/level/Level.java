package net.minecraft.world.level;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public abstract class Level {
    public abstract BlockState getBlockState(BlockPos pos);
    public abstract BlockEntity getBlockEntity(BlockPos pos);
    public long getGameTime() { return 0L; }
    public ResourceKey<Level> dimension() { return null; }
}

package net.minecraft.world.level;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
public abstract class Level {
    public boolean isClientSide;
    public final java.util.Random random = new java.util.Random(0);
    public void playSound(net.minecraft.world.entity.player.Player player, BlockPos pos,
        net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch) {}
    public boolean isClientSide() { return false; }
    public boolean isLoaded(BlockPos pos) { return true; }
    public boolean hasChunkAt(BlockPos pos) { return true; }
    public boolean setBlock(BlockPos pos, BlockState state, int flags) { return true; }
    public FluidState getFluidState(BlockPos pos) { return null; }
    public void scheduleTick(BlockPos pos, Block block, int delay) { }
    public abstract BlockState getBlockState(BlockPos pos);
    public abstract BlockEntity getBlockEntity(BlockPos pos);
    public long getDayTime(){return 0;}
    public long getGameTime() { return 0L; }
    public ResourceKey<Level> dimension() { return null; }

    /** Сигнализирует соседям об изменении силы редстоуна (стаб компилятора). */
    public void updateNeighbourForOutputSignal(net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.Block block) {
    }
}

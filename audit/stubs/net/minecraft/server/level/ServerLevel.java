package net.minecraft.server.level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public abstract class ServerLevel extends Level {
    @Override public abstract BlockState getBlockState(BlockPos pos);
    @Override public abstract BlockEntity getBlockEntity(BlockPos pos);
    public int sendParticles(ParticleOptions type, double x, double y, double z, int count,
                             double dx, double dy, double dz, double speed) { return 0; }
}

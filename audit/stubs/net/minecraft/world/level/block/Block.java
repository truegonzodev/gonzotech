package net.minecraft.world.level.block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
public class Block extends BlockBehaviour {
    public static final int UPDATE_ALL = 3;
    public static final int UPDATE_NEIGHBORS = 1;
    public static final int UPDATE_CLIENTS = 2;
    public Block(Properties properties) { }
    public Block() { }
    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) { return null; }
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(net.minecraft.world.level.block.state.BlockState state) {
        return net.minecraft.world.level.material.PushReaction.NORMAL;
    }
}

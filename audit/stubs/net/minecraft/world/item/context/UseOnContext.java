package net.minecraft.world.item.context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
public class UseOnContext {
    public BlockPos getClickedPos() { return null; }
    public Direction getClickedFace() { return null; }
    public Vec3 getClickLocation() { return null; }
    public ItemStack getItemInHand() { return null; }
    public Player getPlayer() { return null; }
    public InteractionHand getHand() { return null; }
    public Level getLevel() { return null; }
    public boolean isInside() { return false; }
}

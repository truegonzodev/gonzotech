package net.minecraft.world.level.block.piston;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Typecheck-only 1.21.4 API surface used by piston rendering/containment. */
public class PistonMovingBlockEntity extends BlockEntity {
    public BlockState getMovedState() { return null; }
    public Direction getDirection() { return null; }
    public boolean isSourcePiston() { return false; }
}

package net.minecraft.world.level.block;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
/** Compile boundary: Mojmap1.21.4 TYPE/getConnectedDirection, not chest gameplay. */
public class ChestBlock extends Block {
    public static final EnumProperty<ChestType> TYPE = EnumProperty.create("type", ChestType.class);
    public static Direction getConnectedDirection(BlockState state) { return Direction.NORTH; }
}

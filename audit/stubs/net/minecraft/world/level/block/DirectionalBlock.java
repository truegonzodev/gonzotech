package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Стаб компилятора: блок с направлением (FACING public static). */
public abstract class DirectionalBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing");

    protected DirectionalBlock(Properties properties) { super(properties); }
}

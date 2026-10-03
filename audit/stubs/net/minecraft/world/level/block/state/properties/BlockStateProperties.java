package net.minecraft.world.level.block.state.properties;
public final class BlockStateProperties {
    public static final BooleanProperty WATERLOGGED = BooleanProperty.create("waterlogged");
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
    public static final EnumProperty<net.minecraft.world.level.block.state.properties.PistonType> PISTON_TYPE = EnumProperty.create("type", net.minecraft.world.level.block.state.properties.PistonType.class);
    public static final DirectionProperty FACING = DirectionProperty.create("facing");
    public static final BooleanProperty SHORT = BooleanProperty.create("short");
    private BlockStateProperties() { }
}

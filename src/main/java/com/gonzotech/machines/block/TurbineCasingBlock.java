package com.gonzotech.machines.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Внешний корпус прямоугольной паровой турбины.
 *
 * <p>При сборке {@link #FRAME} сохраняет положение блока на границе всего
 * параллелепипеда, а четыре {@code port_*} бита — локальные рёбра вокруг
 * service-порта. Это статический, дешёвый CTM: модели не сканируют мир во
 * время рендера и не требуют BlockEntity у каждого корпуса.</p>
 */
public final class TurbineCasingBlock extends TurbinePartBlock {

    public static final MapCodec<TurbineCasingBlock> CODEC = simpleCodec(TurbineCasingBlock::new);

    /** Положение корпуса на поверхности собранной турбины. */
    public static final EnumProperty<FrameProfile> FRAME = EnumProperty.create("frame", FrameProfile.class);
    /** Локальные обводки service-порта; их смысл определяется {@link #FRAME}. */
    public static final BooleanProperty PORT_0 = BooleanProperty.create("port_0");
    public static final BooleanProperty PORT_1 = BooleanProperty.create("port_1");
    public static final BooleanProperty PORT_2 = BooleanProperty.create("port_2");
    public static final BooleanProperty PORT_3 = BooleanProperty.create("port_3");

    public TurbineCasingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FORMED, false)
            .setValue(FRAME, FrameProfile.NONE)
            .setValue(PORT_0, false)
            .setValue(PORT_1, false)
            .setValue(PORT_2, false)
            .setValue(PORT_3, false));
    }

    @Override
    protected MapCodec<TurbineCasingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRAME, PORT_0, PORT_1, PORT_2, PORT_3);
    }

    /**
     * Все возможные местоположения клетки оболочки. Рёбра и углы определяют
     * каркас всего параллелепипеда; грани дополнительно используют port_0..3.
     */
    public enum FrameProfile implements StringRepresentable {
        NONE("none"),

        FACE_DOWN("face_down"),
        FACE_UP("face_up"),
        FACE_NORTH("face_north"),
        FACE_SOUTH("face_south"),
        FACE_WEST("face_west"),
        FACE_EAST("face_east"),

        EDGE_X_DOWN_NORTH("edge_x_down_north"),
        EDGE_X_DOWN_SOUTH("edge_x_down_south"),
        EDGE_X_UP_NORTH("edge_x_up_north"),
        EDGE_X_UP_SOUTH("edge_x_up_south"),
        EDGE_Y_WEST_NORTH("edge_y_west_north"),
        EDGE_Y_WEST_SOUTH("edge_y_west_south"),
        EDGE_Y_EAST_NORTH("edge_y_east_north"),
        EDGE_Y_EAST_SOUTH("edge_y_east_south"),
        EDGE_Z_WEST_DOWN("edge_z_west_down"),
        EDGE_Z_WEST_UP("edge_z_west_up"),
        EDGE_Z_EAST_DOWN("edge_z_east_down"),
        EDGE_Z_EAST_UP("edge_z_east_up"),

        CORNER_WEST_DOWN_NORTH("corner_west_down_north"),
        CORNER_WEST_DOWN_SOUTH("corner_west_down_south"),
        CORNER_WEST_UP_NORTH("corner_west_up_north"),
        CORNER_WEST_UP_SOUTH("corner_west_up_south"),
        CORNER_EAST_DOWN_NORTH("corner_east_down_north"),
        CORNER_EAST_DOWN_SOUTH("corner_east_down_south"),
        CORNER_EAST_UP_NORTH("corner_east_up_north"),
        CORNER_EAST_UP_SOUTH("corner_east_up_south");

        private final String name;

        FrameProfile(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}

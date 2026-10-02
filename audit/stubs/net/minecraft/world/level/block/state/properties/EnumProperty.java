package net.minecraft.world.level.block.state.properties;
import net.minecraft.util.StringRepresentable;
public class EnumProperty<T extends Enum<T>> extends Property<T> {
    public static <T extends Enum<T>> EnumProperty<T> create(String name, Class<T> type) { return new EnumProperty<>(); }
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(
            String name, Class<T> type, java.util.function.Predicate<T> filter) { return new EnumProperty<>(); }
}

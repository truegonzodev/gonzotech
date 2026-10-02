package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
public class StateDefinition<O, S> {
    public S any() { return null; }
    public static class Builder<O, S> {
        public Builder(O owner, Property<?>... properties) { }
        public Builder<O, S> add(Property<?>... properties) { return this; }
    }
}

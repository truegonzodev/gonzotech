package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
public class BlockState {
    public Block getBlock() { return null; }
    public boolean is(Block block) { return false; }
    public boolean is(net.minecraft.tags.TagKey<Block> tag) { return false; }
    public boolean isAir() { return false; }
    public boolean canOcclude() { return false; }
    public boolean hasProperty(Property<?> property) { return false; }
    public <T extends Comparable<T>> T getValue(Property<T> property) { return null; }
    public <T extends Comparable<T>, V extends T> BlockState setValue(Property<T> property, V value) { return this; }
}

package net.minecraft.world;
import net.minecraft.world.item.ItemStack;
/** Compile boundary: vanilla CompoundContainer implements Container; contains uses child reference identity. */
public abstract class CompoundContainer implements Container {
    public abstract boolean contains(Container container);
}

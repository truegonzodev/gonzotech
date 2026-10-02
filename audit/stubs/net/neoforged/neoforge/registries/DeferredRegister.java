package net.neoforged.neoforge.registries;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
public class DeferredRegister<T> {
    public static <B> DeferredRegister<B> create(Registry<B> registry, String modid) {
        throw new UnsupportedOperationException("stub");
    }
    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> sup) {
        throw new UnsupportedOperationException("stub");
    }
    protected DeferredRegister() { }
}

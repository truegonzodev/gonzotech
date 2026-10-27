package net.minecraft.core;
public interface Holder<T> {
 T value();
 class Reference<T> implements Holder<T> { public T value() { return null; } }
}

package net.minecraft.world.level.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Стаб компилятора: dimension-data (только используемые члены). */
public abstract class SavedData {
    public SavedData() {}

    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    public void setDirty() {}

    /** Фабрика ванили: create / load / (не используется синтез). */
    public record Factory<T extends SavedData>(Supplier<T> constructor,
                                               BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer,
                                               Object synthetic) {
    }
}

package net.minecraft.core;
import net.minecraft.resources.ResourceLocation;
// 0.3.118: getKey — RadMaterials резолвит id предмета/блока для таблиц экранов.
public interface Registry<T> {
    ResourceLocation getKey(T value);
    boolean containsKey(ResourceLocation id);
}

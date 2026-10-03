package net.minecraft.core.registries;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
public final class BuiltInRegistries {
    public static final Registry<ParticleType<?>> PARTICLE_TYPE = null;
    // 0.3.118: реестры предмета/блока — RadMaterials (таблицы экранирования по id).
    public static final Registry<Item> ITEM = null;
    public static final Registry<Block> BLOCK = null;
    private BuiltInRegistries() { }
}

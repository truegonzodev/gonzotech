package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, GonzoTechMod.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ETHYLEN_EXPLOSION =
            PARTICLE_TYPES.register("ethylen_explosion", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ETHYLEN_EXPLOSION_EMITTER =
            PARTICLE_TYPES.register("ethylen_explosion_emitter", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RADIATION_MIST =
            PARTICLE_TYPES.register("radiation_mist", () -> new SimpleParticleType(true));

    /** 0.3.93: «горячая труба» — предупреждение о дорогой маршрутизации (≥400 дорожек). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOT_PIPE =
            PARTICLE_TYPES.register("hot_pipe", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ETHANOL_FIRE_DUST =
            PARTICLE_TYPES.register("ethanol_fire_dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ETHANOL_FIRE_DUST_LARGE =
            PARTICLE_TYPES.register("ethanol_fire_dust_large", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORMALDEHYDE_FIRE_DUST =
            PARTICLE_TYPES.register("formaldehyde_fire_dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORMALDEHYDE_FIRE_DUST_LARGE =
            PARTICLE_TYPES.register("formaldehyde_fire_dust_large", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}

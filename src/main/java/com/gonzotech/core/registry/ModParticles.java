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

    private ModParticles() {
    }
}

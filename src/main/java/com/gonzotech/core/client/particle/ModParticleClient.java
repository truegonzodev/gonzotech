package com.gonzotech.core.client.particle;

import com.gonzotech.core.registry.ModParticles;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class ModParticleClient {

    private ModParticleClient() {
    }

    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ETHYLEN_EXPLOSION.get(), EthylenExplosionParticle.Provider::new);
        event.registerSpecial(ModParticles.ETHYLEN_EXPLOSION_EMITTER.get(), new EthylenExplosionSeedParticle.Provider());
    }
}

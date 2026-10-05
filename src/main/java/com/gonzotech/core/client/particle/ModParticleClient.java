package com.gonzotech.core.client.particle;

import com.gonzotech.core.registry.ModParticles;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class ModParticleClient {

    private ModParticleClient() {
    }

    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ETHYLEN_EXPLOSION.get(), EthylenExplosionParticle.Provider::new);
        event.registerSpecial(ModParticles.ETHYLEN_EXPLOSION_EMITTER.get(), new EthylenExplosionSeedParticle.Provider());
        event.registerSpriteSet(ModParticles.RADIATION_MIST.get(), RadiationMistParticle.Provider::new);
        event.registerSpriteSet(ModParticles.HOT_PIPE.get(), HotPipeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.ETHANOL_FIRE_DUST.get(),
            sprites -> new LiquidFireDustParticle.Provider(sprites, 0x3AC5DE, 0.9F));
        event.registerSpriteSet(ModParticles.ETHANOL_FIRE_DUST_LARGE.get(),
            sprites -> new LiquidFireDustParticle.Provider(sprites, 0x3AC5DE, 1.5F));
        event.registerSpriteSet(ModParticles.FORMALDEHYDE_FIRE_DUST.get(),
            sprites -> new LiquidFireDustParticle.Provider(sprites, 0x563475, 0.9F));
        event.registerSpriteSet(ModParticles.FORMALDEHYDE_FIRE_DUST_LARGE.get(),
            sprites -> new LiquidFireDustParticle.Provider(sprites, 0x563475, 1.5F));
    }
}

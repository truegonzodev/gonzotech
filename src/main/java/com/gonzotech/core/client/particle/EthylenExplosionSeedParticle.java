package com.gonzotech.core.client.particle;

import com.gonzotech.core.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class EthylenExplosionSeedParticle extends NoRenderParticle {

    protected EthylenExplosionSeedParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.lifetime = 8;
    }

    @Override
    public void tick() {
        for (int i = 0; i < 6; ++i) {
            double xx = this.x + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            double yy = this.y + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            double zz = this.z + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            this.level.addParticle(ModParticles.ETHYLEN_EXPLOSION.get(), xx, yy, zz,
                    (double) ((float) this.age / (float) this.lifetime), 0.0, 0.0);
        }

        ++this.age;
        if (this.age == this.lifetime) {
            this.remove();
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z,
                                       double xAux, double yAux, double zAux,
                                       RandomSource random) {
            return new EthylenExplosionSeedParticle(level, x, y, z);
        }
    }
}

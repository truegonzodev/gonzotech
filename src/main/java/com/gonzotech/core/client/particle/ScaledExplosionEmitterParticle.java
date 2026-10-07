package com.gonzotech.core.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

/** Vanilla explosion-emitter pattern with a per-child sprite size multiplier. */
public final class ScaledExplosionEmitterParticle extends NoRenderParticle {
    public static final float GUNPOWDER_SIZE_SCALE = 1.22F;
    public static final float INDUSTRIAL_TNT_SIZE_SCALE = 1.55F;

    private static final int LIFETIME_TICKS = 8;
    private static final int PARTICLES_PER_TICK = 6;
    private final float sizeScale;

    private ScaledExplosionEmitterParticle(ClientLevel level, double x, double y, double z, float sizeScale) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sizeScale = sizeScale;
        this.lifetime = LIFETIME_TICKS;
    }

    @Override
    public void tick() {
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double x = this.x + (this.random.nextDouble() - this.random.nextDouble()) * 4.0D;
            double y = this.y + (this.random.nextDouble() - this.random.nextDouble()) * 4.0D;
            double z = this.z + (this.random.nextDouble() - this.random.nextDouble()) * 4.0D;
            Particle flash = Minecraft.getInstance().particleEngine.createParticle(
                ParticleTypes.EXPLOSION, x, y, z,
                (double) this.age / this.lifetime, 0.0D, 0.0D);
            if (flash != null) {
                // Keep vanilla's animated explosion sprite and emitter spread;
                // only enlarge each generated explosion flash.
                flash.scale(this.sizeScale);
            }
        }

        if (++this.age >= this.lifetime) {
            this.remove();
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final float sizeScale;

        public Provider(float sizeScale) {
            this.sizeScale = sizeScale;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new ScaledExplosionEmitterParticle(level, x, y, z, this.sizeScale);
        }
    }
}

package com.gonzotech.core.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Slow grey blast dust using the existing gonzotech:dust particle sprite. */
public final class IndustrialBlastDustParticle extends TextureSheetParticle {
    public static final int LIFETIME_TICKS = 100;
    public static final int FADE_IN_TICKS = 3;
    public static final float MAX_ALPHA = 0.8F;

    private final SpriteSet sprites;
    private final float initialSize;

    private IndustrialBlastDustParticle(ClientLevel level, double x, double y, double z,
                                        double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = 0.96F;
        this.gravity = 0.03F;
        this.hasPhysics = true;
        this.lifetime = LIFETIME_TICKS;
        this.initialSize = 0.22F + this.random.nextFloat() * 0.18F;
        this.quadSize = this.initialSize;
        this.setColor(0.58F, 0.58F, 0.58F);
        this.setAlpha(0.0F);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.lifetime) {
            return;
        }

        float lifeProgress = (float) this.age / this.lifetime;
        float fadeIn = Math.min(1.0F, (float) this.age / FADE_IN_TICKS);
        float fadeOut = Math.min(1.0F, (float) (this.lifetime - this.age) / 24.0F);
        this.alpha = MAX_ALPHA * fadeIn * fadeOut;
        this.quadSize = this.initialSize * (1.0F - 0.68F * lifeProgress);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new IndustrialBlastDustParticle(level, x, y, z,
                xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}

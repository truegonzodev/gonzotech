package com.gonzotech.core.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Аварийная пыль реактора (0.3.246): белое облако 1–4 блока, почти статично и
 * медленно оседает, плавно проявляется из прозрачности и плавно гаснет; чем
 * крупнее частица, тем дольше живёт (10–15 с). Спрайт — gonzotech:dust.
 */
public final class ReactorDustParticle extends TextureSheetParticle {
    public static final float MIN_SIZE = 1.0F;
    public static final float MAX_SIZE = 4.0F;
    public static final int MIN_LIFETIME_TICKS = 200;
    public static final int MAX_LIFETIME_TICKS = 300;
    public static final int FADE_IN_TICKS = 20;
    public static final int FADE_OUT_TICKS = 60;
    public static final float MAX_ALPHA = 0.55F;

    private final SpriteSet sprites;

    private ReactorDustParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = 0.99F;
        this.gravity = 0.012F;
        this.hasPhysics = true;
        float size = MIN_SIZE + this.random.nextFloat() * (MAX_SIZE - MIN_SIZE);
        this.lifetime = MIN_LIFETIME_TICKS + (int) ((size - MIN_SIZE) / (MAX_SIZE - MIN_SIZE)
            * (MAX_LIFETIME_TICKS - MIN_LIFETIME_TICKS));
        this.quadSize = size;
        this.setColor(1.0F, 1.0F, 1.0F);
        this.setAlpha(0.0F);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.lifetime) {
            return;
        }
        float fadeIn = Math.min(1.0F, (float) this.age / FADE_IN_TICKS);
        float fadeOut = Math.min(1.0F, (float) (this.lifetime - this.age) / FADE_OUT_TICKS);
        this.alpha = MAX_ALPHA * fadeIn * fadeOut;
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
            return new ReactorDustParticle(level, x, y, z,
                xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}

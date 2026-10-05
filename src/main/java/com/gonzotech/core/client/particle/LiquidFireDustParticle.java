package com.gonzotech.core.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Colored vanilla-dust sprite with the liquid-fire lifetime adjustment. */
public final class LiquidFireDustParticle extends TextureSheetParticle {
    public static final float LIFETIME_MULTIPLIER = 1.2F;

    private final SpriteSet sprites;

    private LiquidFireDustParticle(ClientLevel level, double x, double y, double z,
                                   double dx, double dy, double dz, int color, float scale,
                                   SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        // Match vanilla dust's initial velocity damping and drag.
        this.xd *= 0.1D;
        this.yd *= 0.1D;
        this.zd *= 0.1D;
        this.friction = 0.96F;
        this.gravity = 0.0F;
        this.quadSize *= 0.75F * scale;

        int vanillaLifetime = (int) (8.0D / (this.random.nextDouble() * 0.8D + 0.2D));
        this.lifetime = Math.max(Math.round(vanillaLifetime * LIFETIME_MULTIPLIER), 1);
        this.rCol = ((color >> 16) & 0xFF) / 255.0F;
        this.gCol = ((color >> 8) & 0xFF) / 255.0F;
        this.bCol = (color & 0xFF) / 255.0F;
        this.alpha = 1.0F;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float ageFraction = ((float) this.age + partialTick) / (float) this.lifetime * 32.0F;
        return this.quadSize * Math.max(Math.min(ageFraction, 1.0F), 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final int color;
        private final float scale;

        public Provider(SpriteSet sprites, int color, float scale) {
            this.sprites = sprites;
            this.color = color;
            this.scale = scale;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            return new LiquidFireDustParticle(level, x, y, z, dx, dy, dz,
                this.color, this.scale, this.sprites);
        }
    }
}

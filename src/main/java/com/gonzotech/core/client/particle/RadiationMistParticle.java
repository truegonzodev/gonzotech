package com.gonzotech.core.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class RadiationMistParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private RadiationMistParticle(ClientLevel level, double x, double y, double z,
                                  double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.friction = 0.94F;
        this.gravity = 0.0F;
        this.lifetime = 20 + this.random.nextInt(30);
        this.quadSize = 0.08F + this.random.nextFloat() * 0.10F;
        this.rCol = 0.55F;
        this.gCol = 0.95F;
        this.bCol = 0.12F;
        this.alpha = 0.28F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime || !level.noCollision(getBoundingBox())) {
            remove();
            return;
        }
        move(xd, yd, zd);
        xd *= friction; yd *= friction; zd *= friction;
        alpha = 0.28F * (1.0F - (float) age / lifetime);
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new RadiationMistParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}

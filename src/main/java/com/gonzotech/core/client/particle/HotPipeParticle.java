package com.gonzotech.core.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * «Горячая труба» (0.3.93): предупреждающая частица над позициями, через которые
 * маршрутизатор гоняет ≥400 дорожек за итерацию (см. PipeFlowWarnings).
 *
 * <p>Поведение — как ванильный reddust ({@code DustParticleBase}): трение 0.96,
 * скорость гасится ×0.1, жизнь 8..40 тиков, размер добирается за первые 1/32
 * жизни. Отличия: текстура своя (gonzotech:warn — язык предупреждения) и размер
 * ×1.1 от пыли ({@code quadSize ×= 0.75×1.1}). Лист OPAQUE, как у пыли.</p>
 */
public final class HotPipeParticle extends TextureSheetParticle {

    /** Размер относительно ванильной reddust (автор 04.10.2026: «х1.1»). */
    public static final float SIZE_FACTOR = 1.1F;

    private final SpriteSet sprites;

    private HotPipeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;
        this.friction = 0.96F;
        this.gravity = 0.0F;
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
        this.quadSize *= 0.75F * SIZE_FACTOR;
        int base = (int) (8.0 / (this.random.nextDouble() * 0.8 + 0.2));
        this.lifetime = Math.max(base, 1);
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
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
        float f = ((float) this.age + partialTick) / (float) this.lifetime * 32.0F;
        return this.quadSize * Math.max(Math.min(f, 1.0F), 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new HotPipeParticle(level, x, y, z, this.sprites);
        }
    }
}

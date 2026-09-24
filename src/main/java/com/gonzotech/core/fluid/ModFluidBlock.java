package com.gonzotech.core.fluid;

import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.radiation.RadUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * Кастомный блок жидкости с анимацией пыли на фактической поверхности и эффектами при погружении.
 */
public class ModFluidBlock extends LiquidBlock {

    public enum Kind {
        SULFURIC_ACID(0xB4E58E),
        ETHANOL(0x8AFFE9),
        FORMALDEHYDE(0x8374A6),
        MASH(0xB84A28),
        WORT(0xFFFFFF),
        DISTILLATE(0x8BD3FC);

        public final int particleColor;

        Kind(int particleColor) {
            this.particleColor = particleColor;
        }
    }

    private final Kind kind;

    public ModFluidBlock(Supplier<? extends FlowingFluid> fluid, Kind kind, Properties properties) {
        super(fluid.get(), properties);
        this.kind = kind;
    }

    public ModFluidBlock(FlowingFluid fluid, Kind kind, Properties properties) {
        super(fluid, properties);
        this.kind = kind;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        // Частицы создаются ТОЛЬКО на фактической верхней поверхности жидкости
        if (level.getFluidState(pos.above()).isEmpty()) {
            if (random.nextInt(30) == 0) { // каждые 1-4 сек
                int count = 5 + random.nextInt(6); // 5-10 частиц
                float fluidHeight = state.getFluidState().getHeight(level, pos);
                double surfaceY = pos.getY() + (double) fluidHeight;

                for (int i = 0; i < count; i++) {
                    double px = pos.getX() + random.nextDouble();
                    double pz = pos.getZ() + random.nextDouble();
                    double py = surfaceY + (random.nextDouble() * 0.05);
                    float scale = 0.5F + random.nextFloat() * 1.0F; // 0.5-1.5

                    level.addParticle(new DustParticleOptions(kind.particleColor, scale), px, py, pz, 0.0, 0.015, 0.0);
                }
            }
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) return;
        if (!(entity instanceof LivingEntity living)) return;

        // Эффекты накладываются каждую секунду пока ты погружён в жидкость
        if (living.tickCount % 20 != 0) return;

        switch (kind) {
            case SULFURIC_ACID -> {
                // Отравление 1 на 5 сек, 7 mTx дозы в сек
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                if (living instanceof ServerPlayer sp) {
                    PsycheChemical.addDoseToxicity(sp, 7.0 * RadUnits.MILLI);
                }
            }
            case ETHANOL -> {
                // Тошнота 3 и слепота 1 на 3 сек, 3 mTx дозы в сек
                living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 2));
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                if (living instanceof ServerPlayer sp) {
                    PsycheChemical.addDoseToxicity(sp, 3.0 * RadUnits.MILLI);
                }
            }
            case FORMALDEHYDE -> {
                // Отравление 2 и иссушение 1 на 5 сек, 13 mTx дозы в сек
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                if (living instanceof ServerPlayer sp) {
                    PsycheChemical.addDoseToxicity(sp, 13.0 * RadUnits.MILLI);
                }
            }
            case DISTILLATE -> {
                // Тошнота 1 на 2 сек, 0.1 mTx дозы в сек
                living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
                if (living instanceof ServerPlayer sp) {
                    PsycheChemical.addDoseToxicity(sp, 0.1 * RadUnits.MILLI);
                }
            }
            default -> { }
        }
    }
}

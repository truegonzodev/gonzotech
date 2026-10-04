package com.gonzotech.core.fluid;

import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.radiation.RadUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Кастомный блок жидкости: цвет поверхностной пенки и серверные эффекты при погружении.
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

    /** Shared source/flowing-fluid colour; emission is scheduled by the client puddle controller. */
    public Kind foamKind() { return kind; }

    /** Flint and steel (or a fire charge) lights exposed source cells without replacing the liquid. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        LiquidFireBlock fire = fireForKind();
        boolean ignitionTool = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
        if (fire == null || !ignitionTool) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        // Do not let vanilla FlintAndSteelItem place an ordinary fire on a
        // flowing cell when this liquid has no source to sustain the custom flame.
        if (!state.getFluidState().isSource()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!fire.ignite(level, pos)) return InteractionResult.FAIL;

        if (!player.getAbilities().instabuild) {
            if (stack.is(Items.FLINT_AND_STEEL)) {
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            } else {
                stack.shrink(1);
            }
        }
        level.playSound(null, pos.above(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos.above());
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        tryIgniteFromNearbyFire(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, orientation, isMoving);
        tryIgniteFromNearbyFire(state, level, pos);
    }

    private void tryIgniteFromNearbyFire(BlockState state, Level level, BlockPos pos) {
        LiquidFireBlock fire = fireForKind();
        if (level.isClientSide || fire == null || !state.getFluidState().isSource()
                || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof BaseFireBlock) {
                fire.ignite(level, pos);
                return;
            }
        }
    }

    private LiquidFireBlock fireForKind() {
        return switch (kind) {
            case ETHANOL -> ModBlocks.RECTIFICATE_FIRE.get();
            case FORMALDEHYDE -> ModBlocks.FORMALDEHYDE_FIRE.get();
            default -> null;
        };
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) return;
        if (!(entity instanceof LivingEntity living)) return;

        // Дозирование раз в 20 тиков на callback клетки, не дедуплицировать на игрока:
        // автор 27.09 допускает усиление при пересечении нескольких клеток.
        // Это не измерение скорости движения; геометрию/нагрузку проверить в игре.
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

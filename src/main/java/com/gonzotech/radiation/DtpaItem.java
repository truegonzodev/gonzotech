package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * ДТПА (диэтилентриаминпентауксусная кислота) — хелат курса лечения
 * (спека автора 24.09.2026).
 *
 * <p>Применение — «как еда»: зажать ПКМ ({@value #USE_TICKS} тиков), без сообщений
 * и лора (автор 24.09). Пока висит «Курс лечения», применение невозможно (жест отказа,
 * без текста). Каждый приём:</p>
 * <ol>
 *   <li>моментально <b>−2 % от ТЕКУЩЕЙ</b> дозы;</li>
 *   <li>«Абсорбция дозы» <b>1 уровня на 1 минуту</b> (разрез 31 %);</li>
 *   <li>скрытый счётчик «ДТПА принято» +1 (живёт в {@link PlayerPsyche});</li>
 *   <li>«Курс лечения» на 3 минуты ({@link ModEffects#TREATMENT_COURSE});</li>
 *   <li>когда счётчик становится 6: «Очищение» 4 уровня на 10 секунд, аппаратно
 *       <b>−80 % дозы</b>, сброс счёта, приятный звук и красивые партиклы;</li>
 *   <li>каждый приём: <b>−2000 стресса</b>, <b>+2000 зависимости</b> (по 0.2 % шкалы),
 *       <b>+100 mTx</b> химии.</li>
 * </ol>
 */
public class DtpaItem extends Item {

    /** Задержка «как у еды». */
    public static final int USE_TICKS = 32;

    /** Доз курса: шестая доза даёт «Очищение IV» и аппаратный откат. */
    public static final int COURSE_DOSES = 6;

    /** Моментальный срез дозы: −2 % текущей. */
    private static final double DOSE_REMAINING = 0.98;

    /** «Абсорбция дозы» 1 уровня (амплитуда 0) на 1 минуту. */
    private static final int ABSORPTION_SECONDS = 60;
    private static final int ABSORPTION_AMPLIFIER = 0;

    /** «Курс лечения» на 3 минуты — блокирует повторный ДТПА. */
    public static final int COURSE_SECONDS = 3 * 60;

    /** «Очищение» 4 уровня (амплитуда 3) на 10 секунд — награда шестой дозы. */
    private static final int BONUS_CLEANSE_SECONDS = 10;
    private static final int BONUS_CLEANSE_AMPLIFIER = 3;

    /** Аппаратный откат шестой дозы: остаётся 20 % текущей дозы. */
    private static final double COURSE_DOSE_REMAINING = 0.2;

    /** Химия приёма: +100 mTx = 100e6 nTx. */
    private static final double CHEM_NTX = 100.0 * 1_000_000.0;

    /** Стресс приёма: −2000 очков. */
    private static final int STRESS_RELIEF = 2_000;
    /** Зависимость приёма: +2000 очков (= 0.2 %, как тотем). */
    private static final int ADDICTION_POINTS = 2_000;

    public DtpaItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // «Курс лечения» ещё идёт — принять ещё один ДТПА невозможно (без текста).
        if (player.hasEffect(ModEffects.TREATMENT_COURSE)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer serverPlayer)) {
            return stack;
        }
        if (serverPlayer.hasEffect(ModEffects.TREATMENT_COURSE)) {
            return stack;
        }

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);

        // 1. Моментально −2 % от ТЕКУЩЕЙ дозы.
        int dose = psyche.getRadiation();
        if (dose > 0) {
            psyche.setRadiation((int) Math.round(dose * DOSE_REMAINING));
        }

        // 2. «Абсорбция дозы» I уровня на 1 минуту.
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.DOSE_ABSORPTION,
                ABSORPTION_SECONDS * 20, ABSORPTION_AMPLIFIER, false, true));

        // 3. Скрытый счётчик курса.
        int count = psyche.getDtpaCourseCount() + 1;

        // 4. «Курс лечения» на 3 минуты.
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.TREATMENT_COURSE,
                COURSE_SECONDS * 20, 0, false, true));

        // 5. Шестая доза: «Очищение» IV, аппаратно −80 % дозы, сброс, звук и партиклы.
        if (count >= COURSE_DOSES) {
            serverPlayer.addEffect(new MobEffectInstance(ModEffects.RAD_CLEANSE,
                    BONUS_CLEANSE_SECONDS * 20, BONUS_CLEANSE_AMPLIFIER, false, true));
            psyche.setRadiation((int) Math.round(psyche.getRadiation() * COURSE_DOSE_REMAINING));
            count = 0;

            double x = serverPlayer.getX();
            double y = serverPlayer.getY() + 1.0;
            double z = serverPlayer.getZ();
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 24, 0.7, 0.7, 0.7, 0.0);
            serverLevel.sendParticles(ParticleTypes.NOTE, x, y, z, 15, 0.6, 0.6, 0.6, 0.0);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y, z, 20, 0.5, 0.8, 0.5, 0.2);
            serverLevel.playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.1F);
        }
        psyche.setDtpaCourseCount(count);

        // 6. Каждый приём: −2000 стресса, +2000 зависимости, +100 mTx химии.
        psyche.setStress(Math.max(0, psyche.getStress() - STRESS_RELIEF));
        psyche.addAddiction(ADDICTION_POINTS);
        PsycheChemical.addDoseToxicity(serverPlayer, CHEM_NTX);

        serverPlayer.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(serverPlayer);

        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}

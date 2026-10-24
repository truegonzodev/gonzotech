package com.gonzotech.core.fluid.client;

import com.gonzotech.core.fluid.GonzoFluidFog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 0.3.287 (автор, п.6): все гонзо-жижи «звучат как вода», кроме кориума.
 *
 * <p>Ванильные водные звуки привязаны к тегу {@code #minecraft:water}: всплеск
 * при входе ({@code Entity#doWaterSplashEffect}), подводный луп с ENTER/EXIT и
 * редкие «additions» смотрят на {@code isEyeInFluid}/{@code isInWater}. Наши
 * жидкости в тег не входят — сознательно (0.3.281, {@link GonzoFluidFog}: тег
 * утащил бы за собой утопление, плавание, тушение огня и лодки). Поэтому набор
 * звуков повторяется на клиенте тем же составом и с теми же параметрами:
 *
 * <ul>
 *   <li>погружение ног — всплеск {@code PLAYER_SPLASH} (на скорости >= 0.25 —
 *       {@code PLAYER_SPLASH_HIGH_SPEED}); громкость считается из скорости как
 *       в ванили (min(1, |v|·0.2)), питч 1.0 ± 0.4·Δrand;</li>
 *   <li>погружение глаз — {@code AMBIENT_UNDERWATER_ENTER} + луп
 *       {@code AMBIENT_UNDERWATER_LOOP} с фадой 40 тиков (тот же приём, что у
 *       vanilla {@code UnderwaterAmbientSoundInstance}; свёрнутый луп
 *       само-останавливается), всплытие — {@code AMBIENT_UNDERWATER_EXIT};</li>
 *   <li>под водой — редкие additions с ванильными шансами за тик:
 *       1 % / 0.1 % / 0.01 % (rare, ultra rare).</li>
 * </ul>
 *
 * <p>Кориум исключён (критерий — {@code fluidMedium == WATER}: кориум даёт LAVA),
 * мёртвая жижа исключена (блок, не жидкость), ванильные вода/лава не трогаются
 * (их озвучивает ваниль).
 */
public final class GonzoFluidSounds {

    /** null — мир ещё не виден: первый кадр только запоминает состояние (без звука на спавне). */
    private static Boolean feetIn;
    private static Boolean eyeIn;
    private static int additionDelay;

    private GonzoFluidSounds() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            feetIn = null;
            eyeIn = null;
            return;
        }

        // ── Всплеск: вход ногами в водо-подобную жижу ──
        boolean nowFeet = isGonzoWater(level.getFluidState(player.blockPosition()));
        if (feetIn != null && nowFeet && !feetIn && !player.isSpectator()) {
            Vec3 v = player.getDeltaMovement();
            float power = Mth.clamp(
                (float) Math.sqrt(v.x * v.x * 0.2 + v.y * v.y + v.z * v.z * 0.2) * 0.2F,
                0.0F, 1.0F);
            SoundEvent splash = power < 0.25F
                ? SoundEvents.PLAYER_SPLASH : SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
            player.playSound(splash, power,
                1.0F + (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.4F);
        }
        feetIn = nowFeet;

        // ── Глаза: ENTER / луп-журчание / EXIT ──
        boolean nowEye = eyeSubmerged(level, player);
        if (nowEye && (eyeIn == null || !eyeIn)) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            mc.getSoundManager().play(new GonzoUnderwaterLoop());
        } else if (!nowEye && eyeIn != null && eyeIn) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.AMBIENT, 1.0F, 1.0F, false);
        }
        eyeIn = nowEye;

        // ── Редкие additions (шансы ванильного UnderwaterAmbientSoundHandler) ──
        if (nowEye) {
            if (--additionDelay <= 0) {
                additionDelay = 0;
                float roll = level.random.nextFloat();
                SoundEvent additions = roll < 1.0E-4F
                    ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE
                    : roll < 0.001F
                        ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE
                        : roll < 0.01F
                            ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS
                            : null;
                if (additions != null) {
                    mc.getSoundManager().play(SimpleSoundInstance.forAmbientAddition(additions));
                }
            }
        } else {
            additionDelay = 0;
        }
    }

    /** Наша водо-подобная жидкость (ванильные вода/лава и кориум — false). */
    private static boolean isGonzoWater(FluidState state) {
        return state != null && !state.isEmpty()
            && GonzoFluidFog.fluidMedium(state) == FogType.WATER;
    }

    /** Тот же критерий, что у ванильного updateFluidOnEyes: поверхность жидкости
     *  в глазном блоке выше уровня глаз. */
    private static boolean eyeSubmerged(ClientLevel level, LocalPlayer player) {
        double eyeY = player.getEyeY();
        BlockPos pos = BlockPos.containing(player.getX(), eyeY, player.getZ());
        FluidState state = level.getFluidState(pos);
        return isGonzoWater(state)
            && (double) ((float) pos.getY() + state.getHeight(level, pos)) > eyeY;
    }

    /** Подводный луп: фада 40 тиков внутрь/наружу (как ванильный
     *  UnderwaterAmbientSoundInstance), при полном всплытии само-останавливается. */
    private static final class GonzoUnderwaterLoop extends AbstractTickableSoundInstance {
        private static final int FADE_DURATION = 40;
        private int fade;

        GonzoUnderwaterLoop() {
            super(SoundEvents.AMBIENT_UNDERWATER_LOOP, SoundSource.AMBIENT,
                SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.relative = true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && eyeSubmerged(mc.level, mc.player)) {
                this.fade++;
            } else {
                this.fade -= 2;
            }
            this.fade = Math.min(this.fade, FADE_DURATION);
            if (this.fade < 0) {
                stop();
            } else {
                this.volume = Mth.clamp((float) this.fade / FADE_DURATION, 0.0F, 1.0F);
            }
        }
    }
}

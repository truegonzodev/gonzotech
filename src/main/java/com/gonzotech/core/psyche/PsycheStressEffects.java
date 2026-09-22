package com.gonzotech.core.psyche;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Эффекты шкал стресса и зависимости (спека автора 22.09.2026).
 * Отдельно от {@link PsycheStress}: там — «сколько накопилось», здесь — «что игрок от этого получает».
 *
 * <h2>Пороги и шансы (за секунду)</h2>
 * <table>
 *   <tr><th>условие</th><th>что происходит</th></tr>
 *   <tr><td>стресс ИЛИ зависимость &gt; {@value #TREMOR_MIN_PERCENT} %</td>
 *       <td>{@value #TREMOR_CHANCE} шанс «Тремора I» на {@value #TREMOR_SECONDS_MIN}–{@value #TREMOR_SECONDS_MAX} с
 *       (только если тремора сейчас нет)</td></tr>
 *   <tr><td>стресс ИЛИ зависимость &gt; {@value #TREMOR_STRONG_MIN_PERCENT} %</td>
 *       <td>+{@value #TREMOR_STRONG_CHANCE} шанс «Тремора III» на
 *       {@value #TREMOR_STRONG_SECONDS_MIN}–{@value #TREMOR_STRONG_SECONDS_MAX} с; прокает и поверх
 *       текущего тремора, продлевая его (потолок {@value #TREMOR_MAX_SECONDS} с)</td></tr>
 *   <tr><td>стресс &gt; {@value #HALLUCINATION_MIN_PERCENT} %</td>
 *       <td>{@value #HALLUCINATION_CHANCE} шанс случайного звука в радиусе
 *       {@value #HALLUCINATION_RADIUS} блоков: пещерная атмосфера → звуки мобов → TNT → крипер</td></tr>
 *   <tr><td>стресс &gt; {@value #DROP_MIN_PERCENT} %</td><td>{@value #DROP_CHANCE} шанс выронить предмет из руки</td></tr>
 *   <tr><td>стресс &gt; {@value #HEART_ATTACK_MIN_PERCENT} %</td>
 *       <td>{@value #HEART_ATTACK_CHANCE} шанс «Сердечного приступа» на {@value #HEART_ATTACK_SECONDS} с;
 *       по истечении — урон, который не блокируется ничем и ВСЕГДА оставляет ровно
 *       {@value #HEART_ATTACK_LEAVE_HP} HP (убить приступом нельзя — добивают последствия)</td></tr>
 *   <tr><td>стресс &gt; {@value #REACTION_MIN_PERCENT} %</td>
 *       <td>каждое получение урона — замедление I на {@value #SLOWNESS_SECONDS} с;
 *       +{@value #BLINDNESS_CHANCE} шанс слепоты I на {@value #BLINDNESS_SECONDS_MIN}–{@value #BLINDNESS_SECONDS_MAX} с</td></tr>
 * </table>
 *
 * <p><b>«Чистый» урон</b> — свой тип {@code gonzotech:heart_attack} в тегах
 * {@code bypasses_armor}, {@code bypasses_effects}, {@code bypasses_enchantments},
 * {@code bypasses_resistance}, {@code bypasses_shield}. Ванильного тега «сквозь абсорбцию» нет, поэтому
 * абсорбция перед ударом снимается вручную, а кадры неуязвимости обнуляются — иначе урон урезался бы
 * и игрок не доехал бы ровно до 1 HP.</p>
 *
 * <p><b>Отступления от буквы спеки</b> (помечены в доках): продление тремора ограничено
 * {@value #TREMOR_MAX_SECONDS} с, чтобы серия проков не сделала тряску вечной; сердечный приступ
 * не перезапускается, пока активен, и при старте даёт предупреждение (звук + строка) —
 * «тетрадь смерти» даёт 40 секунд осознанно, а не молча.</p>
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class PsycheStressEffects {

    // ── Тремор ──
    public static final int TREMOR_MIN_PERCENT = 40;
    public static final double TREMOR_CHANCE = 0.006;
    public static final int TREMOR_SECONDS_MIN = 10;
    public static final int TREMOR_SECONDS_MAX = 60;
    public static final int TREMOR_STRONG_MIN_PERCENT = 70;
    public static final double TREMOR_STRONG_CHANCE = 0.001;
    public static final int TREMOR_STRONG_SECONDS_MIN = 10;
    public static final int TREMOR_STRONG_SECONDS_MAX = 30;
    /** Потолок продления тремора (интерпретация: тряска не должна стать вечной). */
    public static final int TREMOR_MAX_SECONDS = 120;

    // ── Слуховые галлюцинации ──
    public static final int HALLUCINATION_MIN_PERCENT = 50;
    public static final double HALLUCINATION_CHANCE = 0.013;
    public static final double HALLUCINATION_RADIUS = 7.0;

    // ── Выронить предмет ──
    public static final int DROP_MIN_PERCENT = 91;
    public static final double DROP_CHANCE = 0.04;

    // ── Сердечный приступ ──
    public static final int HEART_ATTACK_MIN_PERCENT = 99;
    public static final double HEART_ATTACK_CHANCE = 0.005;
    public static final int HEART_ATTACK_SECONDS = 40;
    /**
     * Исход приступа (автор 22.09, после нерфа): урон не фиксированный — игрок ВСЕГДА
     * остаётся с 1 единицей здоровья (пол-хёрта). То есть урона ровно «текущее − 1»,
     * и убить приступ не может: добивают уже последствия.
     */
    public static final float HEART_ATTACK_LEAVE_HP = 1.0F;

    // ── Реакция на урон ──
    public static final int REACTION_MIN_PERCENT = 74;
    public static final int SLOWNESS_SECONDS = 2;
    public static final double BLINDNESS_CHANCE = 0.004;
    public static final int BLINDNESS_SECONDS_MIN = 1;
    public static final int BLINDNESS_SECONDS_MAX = 5;

    /** Приоритетный набор «страшных» звуков: пещеры → мобы → TNT → крипер. */
    private static final SoundEvent[] MOB_SOUNDS = {
            SoundEvents.ZOMBIE_AMBIENT,
            SoundEvents.SKELETON_AMBIENT,
            SoundEvents.SPIDER_AMBIENT,
            SoundEvents.ENDERMAN_AMBIENT,
            SoundEvents.WITCH_AMBIENT
    };

    /** Ключ типа урона «сердечный приступ» (датапак мода). */
    private static final ResourceKey<DamageType> HEART_ATTACK_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "heart_attack"));

    /** У кого сейчас активен сердечный приступ — чтобы поймать момент окончания. */
    private static final Map<UUID, Boolean> HEART_ATTACK = new HashMap<>();

    private static final Random RNG = new Random();

    private PsycheStressEffects() {
    }

    // ═══════════════════════ секундные броски ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        tick(player);
    }

    /** Все броски эффектов — раз в секунду, рядом с пересчётом шкал. */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wasHeartAttack = Boolean.TRUE.equals(HEART_ATTACK.get(id));

        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            HEART_ATTACK.remove(id);
            return;
        }

        // Окончание приступа: эффекта больше нет, а в прошлую секунду был → «чистый» урон.
        boolean hasHeartAttack = player.hasEffect(ModEffects.HEART_ATTACK);
        if (hasHeartAttack) {
            HEART_ATTACK.put(id, Boolean.TRUE);
        } else if (wasHeartAttack) {
            HEART_ATTACK.remove(id);
            heartAttackDamage(player);
        }

        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int stress = PsycheStress.percent(psyche.getStress());
        int addiction = PlayerPsyche.pointsPercent(psyche.getAddiction());

        // Тремор I — «приступы тряски», только пока тремора нет.
        if ((stress > TREMOR_MIN_PERCENT || addiction > TREMOR_MIN_PERCENT)
                && !player.hasEffect(ModEffects.TREMOR)
                && RNG.nextDouble() < TREMOR_CHANCE) {
            applyTremor(player, 0, rollSeconds(TREMOR_SECONDS_MIN, TREMOR_SECONDS_MAX));
        }
        // Тремор III — выше 70 %: может прокать поверх и продлевать текущий.
        if ((stress > TREMOR_STRONG_MIN_PERCENT || addiction > TREMOR_STRONG_MIN_PERCENT)
                && RNG.nextDouble() < TREMOR_STRONG_CHANCE) {
            applyTremor(player, 2, rollSeconds(TREMOR_STRONG_SECONDS_MIN, TREMOR_STRONG_SECONDS_MAX));
        }
        // Слуховые галлюцинации.
        if (stress > HALLUCINATION_MIN_PERCENT && RNG.nextDouble() < HALLUCINATION_CHANCE) {
            hallucinate(player);
        }
        // Руки не держат.
        if (stress > DROP_MIN_PERCENT && RNG.nextDouble() < DROP_CHANCE) {
            dropHeldItem(player);
        }
        // Сердечный приступ (не перезапускаем, пока активен).
        if (stress > HEART_ATTACK_MIN_PERCENT && !hasHeartAttack
                && RNG.nextDouble() < HEART_ATTACK_CHANCE) {
            startHeartAttack(player);
        }
        // Слепота.
        if (stress > REACTION_MIN_PERCENT && RNG.nextDouble() < BLINDNESS_CHANCE) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    rollSeconds(BLINDNESS_SECONDS_MIN, BLINDNESS_SECONDS_MAX) * 20, 0, false, true));
        }
    }

    /**
     * Каждое получение урона при стрессе &gt; {@value #REACTION_MIN_PERCENT} % вешает
     * замедление I на {@value #SLOWNESS_SECONDS} с (автор 22.09).
     */
    @SubscribeEvent
    public static void onDamageReaction(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        if (PsycheStress.percent(psyche.getStress()) > REACTION_MIN_PERCENT) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    SLOWNESS_SECONDS * 20, 0, false, true));
        }
    }

    // ═══════════════════════ механики ═══════════════════════

    /**
     * Наложить тремор. Если тремор уже есть — уровень не понижаем, а длительность
     * складываем с остатком («продлевая его»), с потолком {@value #TREMOR_MAX_SECONDS} с.
     */
    private static void applyTremor(ServerPlayer player, int amplifier, int seconds) {
        MobEffectInstance current = player.getEffect(ModEffects.TREMOR);
        int ticks = seconds * 20;
        int level = amplifier;
        if (current != null) {
            level = Math.max(level, current.getAmplifier());
            ticks = Math.min(TREMOR_MAX_SECONDS * 20, current.getDuration() + ticks);
        }
        player.addEffect(new MobEffectInstance(ModEffects.TREMOR, ticks, level, false, true));
    }

    /**
     * Слуховая галлюцинация: случайный звук в радиусе {@value #HALLUCINATION_RADIUS} блоков.
     * Приоритет набора — как просил автор: пещерная атмосфера, затем звуки мобов, затем
     * поджиг TNT и крипера.
     */
    private static void hallucinate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double angle = RNG.nextDouble() * Math.PI * 2.0;
        double distance = RNG.nextDouble() * HALLUCINATION_RADIUS;
        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        double y = player.getY() + (RNG.nextDouble() - 0.5) * 4.0;
        level.playSound(null, x, y, z, randomHallucination(), SoundSource.AMBIENT,
                0.8F, 0.9F + RNG.nextFloat() * 0.2F);
    }

    /** Взвешенный выбор звука: 40 % пещеры, 35 % мобы, 15 % TNT, 10 % крипер. */
    private static SoundEvent randomHallucination() {
        int roll = RNG.nextInt(100);
        if (roll < 40) {
            return SoundEvents.AMBIENT_CAVE.value();
        }
        if (roll < 75) {
            return MOB_SOUNDS[RNG.nextInt(MOB_SOUNDS.length)];
        }
        if (roll < 90) {
            return SoundEvents.TNT_PRIMED;
        }
        return SoundEvents.CREEPER_PRIMED;
    }

    /** «Руки не держат»: предмет из главной руки падает на землю. */
    private static void dropHeldItem(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        ItemStack dropped = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.drop(dropped, false, false);
    }

    /** Запуск сердечного приступа: эффект на 40 с + предупреждение (звук и строка над хотбаром). */
    private static void startHeartAttack(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.HEART_ATTACK,
                HEART_ATTACK_SECONDS * 20, 0, false, true));
        HEART_ATTACK.put(player.getUUID(), Boolean.TRUE);
        player.displayClientMessage(
                Component.translatable("message.gonzotech.heart_attack.warning"), true);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Исход приступа: урон «до 1 HP». Тип урона в тегах, которые обходят броню, эффекты,
     * зачарования, сопротивление и щит; абсорбция снимается вручную (ванильного тега нет),
     * кадры неуязвимости обнуляются — так игрок гарантированно остаётся ровно с 1 HP.
     * Если здоровья и так 1 или меньше — приступ не бьёт вовсе.
     */
    private static void heartAttackDamage(ServerPlayer player) {
        float damage = player.getHealth() - HEART_ATTACK_LEAVE_HP;
        if (damage <= 0.0F) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Holder<DamageType> type = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(HEART_ATTACK_TYPE);
        DamageSource source = new DamageSource(type);
        player.setAbsorptionAmount(0.0F);
        player.invulnerableTime = 0;
        player.hurt(source, damage);
    }

    private static int rollSeconds(int min, int max) {
        return min + RNG.nextInt(max - min + 1);
    }
}

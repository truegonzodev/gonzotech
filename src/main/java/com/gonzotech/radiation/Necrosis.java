package com.gonzotech.radiation;

import com.gonzotech.core.event.UncurableEffects;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Механика эффекта «Некроз» (спека автора 22.09.2026).
 *
 * <p>Вечный дебафф: получить его можно только случайно при высокой дозе
 * ({@link RadSickness}), снять — только отдельным лечением (Цистамин/Пентацин/ДТПА
 * будут позже), падение дозы его НЕ убирает.</p>
 *
 * <p>Что делает уровень ({@code L} = 0, 1, 2 … = «Некроз I, II, III …»):</p>
 * <ul>
 *   <li><b>Спринт сжигает воздух.</b> Пока игрок спринтует, у него появляется
 *       полоска воздуха, как под водой, и она утекает: 1 пузырёк в
 *       {@code 1 / (1 + 0.1·L)} секунды (уровень I — 1 пузырёк в 0.9 с,
 *       уровень II — в 0.8 с). Плато — 1 пузырёк за тик, то есть расход не
 *       быстрее «1 пузырёк в 0.05 с»;</li>
 *   <li><b>Радиус агра</b> растёт на {@code 10·L} %: мобы без своей цели берут
 *       игрока в цель с увеличенного расстояния (ванильное удержание цели и
 *       её потеря не трогаются — «добор» идёт раз в секунду).</li>
 * </ul>
 */
public final class Necrosis {

    /** Базовый радиус «добора» цели (блоки) — ванильный follow-range рядового моба. */
    private static final double BASE_AGGRO_RADIUS = 16.0;
    /** Прирост за уровень — 10 % (автор). */
    private static final double AGGRO_PER_LEVEL = 0.1;

    /** Накопитель расхода воздуха: «пузырьки в секунду» дробные. */
    private static final Map<UUID, Double> AIR_DEBT = new HashMap<>();
    /**
     * Воздух, который мы держим «своим»: ваниль каждый тик восстанавливает 4 пузырька
     * ({@code LivingEntity.baseTick}), поэтому просто вычитать бесполезно — полоска
     * мигала и сразу заполнялась, а спринт был бесплатным. Планку переставляем каждый тик
     * (автор 22.09.2026: «шкала воздуха то появляется, то пропадает»).
     */
    private static final Map<UUID, Integer> HELD_AIR = new HashMap<>();

    private Necrosis() {
    }

    /** Выдать (или усилить) вечный некроз. Порядок уровней не понижаем. */
    public static void grant(ServerPlayer player, int level) {
        MobEffectInstance current = player.getEffect(ModEffects.NECROSIS);
        if (current != null && current.getAmplifier() >= level && current.isInfiniteDuration()) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.NECROSIS,
                MobEffectInstance.INFINITE_DURATION, level, false, true));
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.gonzotech.necrosis.caught",
                        level + 1).withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
    }

    /**
     * Снять некроз (лечение — будущие препараты; сегодня вызывается вручную/командой).
     * Через {@link UncurableEffects#runUncancelled}: молоко и прочие «общие» снятия этот
     * эффект не берут (автор 22.09.2026).
     */
    public static void cure(ServerPlayer player) {
        UncurableEffects.runUncancelled(() -> player.removeEffect(ModEffects.NECROSIS));
        AIR_DEBT.remove(player.getUUID());
        HELD_AIR.remove(player.getUUID());
    }

    /**
     * Тик-в-тик (вызывается КАЖДЫЙ тик, а не раз в секунду): спринт жжёт воздух.
     *
     * <p>Порядок важен: мы работаем ПОСЛЕ ванильного восстановления воздуха и возвращаем
     * планку на своё место. Пока игрок не спринтует — не мешаем (ваниль сама восстанавливает
     * 4 пузырька в тик), поэтому после спринта воздух честно возвращается.</p>
     */
    public static void tickAir(ServerPlayer player) {
        MobEffectInstance necrosis = player.getEffect(ModEffects.NECROSIS);
        UUID id = player.getUUID();
        if (necrosis == null) {
            AIR_DEBT.remove(id);
            HELD_AIR.remove(id);
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!player.isSprinting()) {
            HELD_AIR.put(id, player.getAirSupply());
            return;
        }
        int level = necrosis.getAmplifier();
        double debt = AIR_DEBT.getOrDefault(id, 0.0) + bubblesPerSecond(level) / 20.0;
        int spend = (int) debt;
        AIR_DEBT.put(id, debt - spend);
        int held = Math.min(HELD_AIR.getOrDefault(id, player.getAirSupply()), player.getAirSupply());
        held = Math.max(0, held - spend);
        HELD_AIR.put(id, held);
        player.setAirSupply(Math.min(player.getAirSupply(), held));
    }

    /** Секундный тик: расход воздуха на спринте + «добор» агра. */
    public static void tick(ServerPlayer player) {
        MobEffectInstance necrosis = player.getEffect(ModEffects.NECROSIS);
        if (necrosis == null) {
            AIR_DEBT.remove(player.getUUID());
            HELD_AIR.remove(player.getUUID());
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        // Расход воздуха — в tickAir() каждый тик; здесь только последствия «на нуле».
        if (player.isSprinting() && player.getAirSupply() <= 0) {
            // Задохнулся на спринте — урон как под водой, но мягче ванильного.
            player.hurt(player.damageSources().drown(), 1.0f);
        }
        pullAggro(player, necrosis);
    }

    /**
     * «1 пузырёк в (1 − 0.1·L) секунды» (L=0 — в секунду, L=1 — в 0.9 с …).
     * Плато из спеки — 1 пузырёк за тик, то есть 20 в секунду.
     */
    private static double bubblesPerSecond(int level) {
        double period = 1.0 - AGGRO_PER_LEVEL * level;   // секунд на пузырёк
        if (period <= 0.05) {
            return 20.0;                                  // плато: 1 пузырёк за тик
        }
        return 1.0 / period;
    }

    /**
     * Увеличенный радиус агра в блоках: {@code 16 · (1 + 0.1·L)}.
     */
    public static double aggroRadius(int level) {
        return BASE_AGGRO_RADIUS * (1.0 + AGGRO_PER_LEVEL * level);
    }

    /**
     * «Добор» цели: мобы-монстры без своей цели берут игрока в цель с
     * увеличенного радиуса. Делается раз в секунду (дешевле, чем каждый тик)
     * и только для мобов, которые вообще могут атаковать игрока.
     */
    public static void pullAggro(ServerPlayer player, MobEffectInstance necrosis) {
        int level = necrosis.getAmplifier();
        double radius = aggroRadius(level);
        AABB box = player.getBoundingBox().inflate(radius);
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, box,
                mob -> mob.getTarget() == null && mob.canAttack(player));
        for (Mob mob : mobs) {
            if (mob.distanceToSqr(player) <= radius * radius) {
                mob.setTarget(player);
            }
        }
    }

    /** Забыть игрока (выход/смерть). */
    public static void forget(UUID playerId) {
        AIR_DEBT.remove(playerId);
        HELD_AIR.remove(playerId);
    }

    /** Утилита для отладки: уровень некроза игрока (-1 — нет эффекта). */
    public static int levelOf(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(ModEffects.NECROSIS);
        return instance == null ? -1 : instance.getAmplifier();
    }
}

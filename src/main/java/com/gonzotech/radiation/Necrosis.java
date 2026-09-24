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
 *   <li><b>Спринт сжигает воздух.</b> Пока игрок спринтует, полоска воздуха, как под
 *       водой, утекает со скоростью {@code 35 · (1 + 0.1·L)} пузырьков в секунду
 *       (автор 22.09.2026: «поднять траты в 30–40 раз + убрать ограничения» — то есть
 *       без прежнего плато, расход растёт с уровнем и вверх не ограничен). На нуле
 *       воздуха игрок начинает захлёбываться — 1 HP в секунду, мягче ванильного;</li>
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

    /**
     * Расход воздуха на спринте, базовый (уровень I). Автор 22.09.2026: «поднять траты
     * пузырьков в 30–40 раз»; автор 24.09.2026: «поднять в 100 раз» — итого 3500 единиц
     * в секунду (полоска 300 выгорает мгновенно при старте спринта). Единица здесь —
     * 1 «пузырёк» = 1 единица ванильного воздуха: полоска = 300 = 10 HUD-пузырей.
     */
    private static final double BUBBLES_BASE_PER_SECOND = 3500.0;
    /** Прирост расхода за уровень — +10 % (та же логика «±10 % за уровень», что у агра). */
    private static final double BUBBLES_PER_LEVEL = 0.1;

    /** Накопитель расхода воздуха: «пузырьки в секунду» дробные. */
    private static final Map<UUID, Double> AIR_DEBT = new HashMap<>();
    /**
     * Воздух, который мы держим «своим»: ваниль каждый тик восстанавливает 4 пузырька
     * ({@code LivingEntity.baseTick}), поэтому просто вычитать бесполезно — полоска
     * мигала и сразу заполнялась, а спринт был бесплатным. Планку переставляем каждый тик
     * (автор 22.09.2026: «шкала воздуха то появляется, то пропадает»).
     */
    private static final Map<UUID, Integer> HELD_AIR = new HashMap<>();

    /** ID модификатора макс. HP под некрозом (см. {@link #updateMaxHealth}). */
    private static final ResourceLocation NECROSIS_HP_ID =
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "necrosis_max_health");

    private Necrosis() {
    }

    /**
     * Выдать (или усилить) вечный некроз. Порядок уровней не понижаем.
     * Без сообщений (автор 24.09: системные подсказки в чат убрать).
     */
    public static void grant(ServerPlayer player, int level) {
        MobEffectInstance current = player.getEffect(ModEffects.NECROSIS);
        if (current != null && current.getAmplifier() >= level && current.isInfiniteDuration()) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.NECROSIS,
                MobEffectInstance.INFINITE_DURATION, level, false, true));
        updateMaxHealth(player);
    }

    /**
     * Снять некроз — ЕДИНСТВЕННЫЙ способ (автор 24.09): полное прохождение курса ДТПА
     * (шестая доза, сброс счётчика — см. {@code DtpaItem}). Через
     * {@link UncurableEffects#runUncancelled}: молоко и прочие «общие» снятия этот эффект
     * не берут (автор 22.09.2026).
     */
    public static void cure(ServerPlayer player) {
        UncurableEffects.runUncancelled(() -> player.removeEffect(ModEffects.NECROSIS));
        AIR_DEBT.remove(player.getUUID());
        HELD_AIR.remove(player.getUUID());
        updateMaxHealth(player);
    }

    /**
     * Макс. HP под некрозом (автор 24.09: «I снимает 2 макс.хп, II — 3, III — 4 и тд»):
     * уровень L (1-based) отнимает {@code L + 1} HP — амплитуда 0 → −2, 1 → −3, 2 → −4.
     * Пересчитывается тиком {@link #tick}, из {@link #grant} и {@link #cure}.
     */
    public static void updateMaxHealth(Player player) {
        // Синхронизация силы идёт по тику секунды — обновляем и макс. HP заодно.
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(NECROSIS_HP_ID);
        MobEffectInstance necrosis = player.getEffect(ModEffects.NECROSIS);
        if (necrosis != null) {
            int penalty = -(necrosis.getAmplifier() + 2);
            maxHealth.addTransientModifier(new AttributeModifier(NECROSIS_HP_ID,
                    penalty, AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
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

    /**
     * Секундный тик: пересчёт макс. HP под некрозом (см. {@link #updateMaxHealth}),
     * расход воздуха на спринте + «добор» агра.
     */
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
     * Расход воздуха в секунду: {@code 35 · (1 + 0.1·L)} (L=0 — «Некроз I»).
     *
     * <p>Прежняя формула «1 пузырёк в (1 − 0.1·L) секунды» росла слишком медленно и
     * упиралась в плато 20 пузырьков/с — заметный эффект появлялся только к 20-му
     * уровню (автор 22.09.2026). Теперь расход сразу в 35 раз выше и растёт линейно,
     * без потолка: L=0 → 35/с, L=5 → 52.5/с, L=10 → 70/с, L=20 → 105/с.</p>
     */
    private static double bubblesPerSecond(int level) {
        return BUBBLES_BASE_PER_SECOND * (1.0 + BUBBLES_PER_LEVEL * level);
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

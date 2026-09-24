package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Эффекты мода — единый реестр для всех систем (радиация и психика).
 *
 * <p>Иконки: {@code textures/mob_effect/<id>.png} — пока плейсхолдеры (арт за автором).
 * Цвета заливки ниже — тоже временные.</p>
 *
 * <ul>
 *   <li><b>{@code necrosis} «Некроз»</b> (радиация) — вечный дебафф выше 30 % дозы
 *       (или по шансу от пентацина/УФ): срезает макс. HP на {@code 2·(уровень + 1)}
 *       (автор 24.09), спринт жжёт воздух, агр мобов; лечится ТОЛЬКО полным курсом
 *       ДТПА (шестая доза), см. {@code radiation.Necrosis};</li>
 *   <li><b>{@code rad_cleanse} «Очищение»</b> (радиация) — вывод дозы абсорбентом,
 *       см. {@code radiation.RadCleanse};</li>
 *   <li><b>{@code tremor} «Тремор»</b> (психика) — приступы тряски выше 40 % стресса
 *       или зависимости; камеру дёргает клиент, см. {@code psyche.PsycheStressEffects}
 *       и {@code psyche.client.PsycheTremorClient};</li>
 *   <li><b>{@code itch} «Зуд»</b> (химия) — три уровня при химическом заражении
 *       &gt; 38 / 52 / 69 %, работает только если на игроке есть броня; урон, вытаптывание
 *       земли и бонусы к стрессу/дозе — {@code psyche.PsycheChemical};</li>
 *   <li><b>{@code heart_attack} «Сердечный приступ»</b> (психика) — выше 99 % стресса;
 *       по истечении 40 секунд — «чистый» урон, который ничем не блокируется и всегда
 *       оставляет ровно 1 HP (см. теги урона {@code gonzotech:heart_attack}).
 *       Пентацин тоже вешает его при передозировке (33 % при повторе за 2 минуты);</li>
 *   <li><b>{@code dose_absorption} «Абсорбция дозы»</b> (радиация, препараты) —
 *       срезает получаемую игроком дозу на {@code (30 + уровень²)} % (31/34 % для
 *       уровней 1/2), см. {@code radiation.RadiationSystem}. Дают Цистамин
 *       (2 уровень, 8 мин) и ДТПА (1 уровень, 1 мин), спека 24.09.2026;</li>
 *   <li><b>{@code treatment_course} «Курс лечения»</b> (препараты) — пока висит,
 *       следующий ДТПА принять нельзя (3 минуты после каждой дозы), спека 24.09.2026.</li>
 * </ul>
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, GonzoTechMod.MOD_ID);

    /**
     * Некроз — вредный, вечный, лечится только полным курсом ДТПА (автор 24.09).
     * Уровень L (амплитуда) отнимает {@code 2·(L + 1)} макс. HP — I −2, II −4, III −6 «и тд»,
     * всегда чётные, сердца полные (автор 24.09, вечер; см. {@code radiation.Necrosis#updateMaxHealth}).
     */
    public static final DeferredHolder<MobEffect, MobEffect> NECROSIS =
            MOB_EFFECTS.register("necrosis", () ->
                    new PlainEffect(MobEffectCategory.HARMFUL, 0x4A2B18));

    /** Очищение — полезный «канал» вывода дозы (сам вывод делает RadCleanse). */
    public static final DeferredHolder<MobEffect, MobEffect> RAD_CLEANSE =
            MOB_EFFECTS.register("rad_cleanse", () ->
                    new PlainEffect(MobEffectCategory.BENEFICIAL, 0x5FD3B2));

    /** Тремор — приступы тряски: камеру дёргает клиент, сервер только вешает эффект. */
    public static final DeferredHolder<MobEffect, MobEffect> TREMOR =
            MOB_EFFECTS.register("tremor", () ->
                    new PlainEffect(MobEffectCategory.HARMFUL, 0x8A8574));

    /** Зуд — не снимается молоком (см. {@code core.event.UncurableEffects}). */
    public static final DeferredHolder<MobEffect, MobEffect> ITCH =
            MOB_EFFECTS.register("itch", () ->
                    new PlainEffect(MobEffectCategory.HARMFUL, 0xB5893F));

    /** Сердечный приступ — 40 секунд на спасение, затем «чистый» урон до 1 HP. */
    public static final DeferredHolder<MobEffect, MobEffect> HEART_ATTACK =
            MOB_EFFECTS.register("heart_attack", () ->
                    new PlainEffect(MobEffectCategory.HARMFUL, 0x8B1A1A));

    /** Абсорбция дозы — срезает получаемую игроком дозу на (30 + уровень²) %. */
    public static final DeferredHolder<MobEffect, MobEffect> DOSE_ABSORPTION =
            MOB_EFFECTS.register("dose_absorption", () ->
                    new PlainEffect(MobEffectCategory.BENEFICIAL, 0xF5C542));

    /** Курс лечения — блокирует повторный ДТПА, пока висит. */
    public static final DeferredHolder<MobEffect, MobEffect> TREATMENT_COURSE =
            MOB_EFFECTS.register("treatment_course", () ->
                    new PlainEffect(MobEffectCategory.BENEFICIAL, 0x5FA8A0));

    private ModEffects() {
    }

    /**
     * Простой эффект без своего поведения: у ванильного {@link MobEffect}
     * конструктор {@code protected}, поэтому напрямую из другого пакета его
     * не создать — этот подкласс только открывает конструктор. Всё поведение
     * живёт в системах (радиация — {@code RadSickness}/{@code Necrosis}/{@code RadCleanse};
     * психика — {@code PsycheStressEffects}).
     */
    public static final class PlainEffect extends MobEffect {

        public PlainEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}

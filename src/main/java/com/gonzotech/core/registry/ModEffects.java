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
 *   <li><b>{@code necrosis} «Некроз»</b> (радиация) — вечный дебафф выше 30 % дозы,
 *       см. {@code radiation.RadSickness} / {@code radiation.Necrosis};</li>
 *   <li><b>{@code rad_cleanse} «Очищение»</b> (радиация) — вывод дозы абсорбентом,
 *       см. {@code radiation.RadCleanse};</li>
 *   <li><b>{@code tremor} «Тремор»</b> (психика) — приступы тряски выше 40 % стресса
 *       или зависимости; камеру дёргает клиент, см. {@code psyche.PsycheStressEffects}
 *       и {@code psyche.client.PsycheTremorClient};</li>
 *   <li><b>{@code heart_attack} «Сердечный приступ»</b> (психика) — выше 99 % стресса;
 *       по истечении 40 секунд — 19 «чистого» урона, который ничем не блокируется
 *       (см. теги урона {@code gonzotech:heart_attack}).</li>
 * </ul>
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, GonzoTechMod.MOD_ID);

    /** Некроз — вредный, вечный, лечится отдельно. */
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

    /** Сердечный приступ — 40 секунд на спасение, затем 19 «чистого» урона. */
    public static final DeferredHolder<MobEffect, MobEffect> HEART_ATTACK =
            MOB_EFFECTS.register("heart_attack", () ->
                    new PlainEffect(MobEffectCategory.HARMFUL, 0x8B1A1A));

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

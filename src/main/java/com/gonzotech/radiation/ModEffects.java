package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Свои эффекты мода (заход 2 в шкалы, спека автора 22.09.2026).
 *
 * <p>Сегодня их два, и оба — про радиацию:</p>
 * <ul>
 *   <li><b>{@code necrosis} «Некроз»</b> — вечный дебафф, который набирается СЛУЧАЙНО при
 *       дозе выше 30 % (см. {@link RadSickness}) и не снимается, когда доза упала:
 *       лечится отдельно. Механика — {@link Necrosis} (спринт жжёт воздух, моб-агр растёт);</li>
 *   <li><b>{@code rad_cleanse} «Очищение»</b> — «эффект-лечение» от антирадинового абсорбента:
 *       пока висит, плавно выводит долю дозы (см. {@link RadCleanse}).</li>
 * </ul>
 *
 * <p>Иконки: {@code textures/mob_effect/<id>.png} — пока плейсхолдеры (арт — за автором).
 * Цвета заливки ниже — тоже временные.</p>
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

    private ModEffects() {
    }

    /**
     * Простой эффект без своего поведения: у ванильного {@link MobEffect}
     * конструктор {@code protected}, поэтому напрямую из другого пакета его
     * не создать — этот подкласс только открывает конструктор. Всё поведение
     * живёт в {@link RadSickness}/{@link Necrosis}/{@link RadCleanse}.
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

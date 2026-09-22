package com.gonzotech.core.event;

import com.gonzotech.core.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * «Вечные» эффекты мода нельзя снять «общим» способом (автор 22.09.2026: «проверить,
 * чтобы некроз, тремор и сердечный приступ нельзя было развеять, выпив молоко»).
 *
 * <p>Как это работает в 1.21.4: молоко зовёт {@code LivingEntity#removeAllEffects()},
 * а NeoForge патчит этот метод так, что каждый эффект проходит через отменяемое
 * {@link MobEffectEvent.Remove}. Отменяем — эффект остаётся. Своя механика (лечение
 * некроза, откат каскада) снимает эффекты в обход — через
 * {@link #runUncancelled(Runnable)}.</p>
 *
 * <p>Естественное истечение эффекта этот ивент не поднимает вообще (ваниль удаляет
 * запись из карты напрямую), поэтому «Сердечный приступ» и «Тремор» как и раньше
 * заканчиваются сами по таймеру.</p>
 */
public final class UncurableEffects {

    /** Внутренний флаг: true — мы снимаем эффект сами, отменять нельзя. */
    private static boolean bypass;

    private UncurableEffects() {
    }

    /** Защищённые эффекты: некроз, тремор, сердечный приступ. */
    private static boolean isGuarded(Holder<MobEffect> effect) {
        return effect.value() == ModEffects.NECROSIS.value()
                || effect.value() == ModEffects.TREMOR.value()
                || effect.value() == ModEffects.HEART_ATTACK.value();
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (bypass) {
            return;
        }
        if (isGuarded(event.getEffect())) {
            event.setCanceled(true);
        }
    }

    /**
     * Выполнить снятие эффектов в обход защиты (лечение некроза, восстановление слепка
     * каскада, отладочные команды). Флаг сбрасывается в {@code finally} — «залипнуть»
     * защита не может.
     */
    public static void runUncancelled(Runnable action) {
        boolean previous = bypass;
        bypass = true;
        try {
            action.run();
        } finally {
            bypass = previous;
        }
    }
}

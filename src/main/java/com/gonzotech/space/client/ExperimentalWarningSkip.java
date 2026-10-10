package com.gonzotech.space.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Автоматически подтверждает ванильные экраны-предупреждения об «экспериментальных
 * настройках», которые Minecraft показывает при создании/загрузке мира с кастомными
 * измерениями (наши Луна/Марс/Европа лежат в экспериментальных папках датапака
 * {@code dimension/}, {@code dimension_type/} и {@code worldgen/}).
 *
 * <p>Покрывает оба экрана (автор, 11.10.2026):
 * <ul>
 *   <li>{@code BackupConfirmScreen} — «Миры, использующие экспериментальные настройки,
 *       не поддерживаются» (кнопки «Создать резервную копию / Я знаю, что делаю!»),
 *       заголовок {@code selectWorld.backupQuestion.experimental}; действие «пропустить»
 *       вызывается через {@code callback.onProceed()};</li>
 *   <li>{@code ConfirmExperimentalFeaturesScreen} — «Внимание! Используются
 *       экспериментальные возможности … Да/Нет», заголовок
 *       {@code selectWorld.warning.experimental.title}; это {@link ConfirmScreen},
 *       подтверждение через булев {@code callback}.</li>
 * </ul>
 *
 * <p>Почему без mixin: флаг «экспериментальности» вешает сам ванильный загрузчик
 * реестров на ЛЮБОЙ валидный файл в экспериментальных папках; перенос описания биома
 * в Java его не убирает. Поэтому единственный корректный способ — перехватить открытие
 * экрана и нажать подтверждение за игрока. NeoForge рантайм использует официальные
 * (Mojang) маппинги, поэтому имена полей/методов стабильны в dev и проде. Любой сбой
 * рефлексии проглатывается — тогда просто покажется обычный экран (без краша).
 */
public final class ExperimentalWarningSkip {

    private static final String BACKUP_TITLE_KEY = "selectWorld.backupQuestion.experimental";
    private static final String FEATURES_TITLE_KEY = "selectWorld.warning.experimental.title";

    private static Field confirmCallbackField;
    private static Field backupCallbackField;
    private static boolean reflectionFailed;

    private ExperimentalWarningSkip() {
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (reflectionFailed) {
            return;
        }
        Screen next = event.getNewScreen();
        String key = titleKey(next);
        if (key == null) {
            return;
        }

        if (next instanceof ConfirmScreen confirm) {
            BooleanConsumer callback = extract(confirm, ConfirmScreen.class, "callback",
                confirmCallbackField, f -> confirmCallbackField = f);
            if (callback != null) {
                event.setCanceled(true);
                callback.accept(true);
                return;
            }
        }

        if (BACKUP_TITLE_KEY.equals(key) && proceedBackup(next)) {
            event.setCanceled(true);
        }
    }

    /** BackupConfirmScreen: вызвать приватный {@code callback.onProceed()}. */
    private static boolean proceedBackup(Screen screen) {
        try {
            if (backupCallbackField == null) {
                Field f = screen.getClass().getDeclaredField("callback");
                f.setAccessible(true);
                backupCallbackField = f;
            }
            Object callback = backupCallbackField.get(screen);
            if (callback == null) {
                return false;
            }
            Method proceed = findNoArg(callback.getClass(), "onProceed", "proceed");
            if (proceed == null) {
                return false;
            }
            proceed.setAccessible(true);
            proceed.invoke(callback);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return false;
        }
    }

    private static BooleanConsumer extract(Screen screen, Class<?> owner, String field,
            Field cache, java.util.function.Consumer<Field> setter) {
        try {
            if (cache == null) {
                Field f = owner.getDeclaredField(field);
                f.setAccessible(true);
                setter.accept(f);
                cache = f;
            }
            Object value = cache.get(screen);
            return value instanceof BooleanConsumer bc ? bc : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }

    private static Method findNoArg(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // пробуем объявленный
            }
            try {
                return owner.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static String titleKey(Screen screen) {
        Component title = screen.getTitle();
        if (title != null && title.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (BACKUP_TITLE_KEY.equals(key) || FEATURES_TITLE_KEY.equals(key)) {
                return key;
            }
        }
        return null;
    }
}

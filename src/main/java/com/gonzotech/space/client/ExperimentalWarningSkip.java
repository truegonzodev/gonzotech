package com.gonzotech.space.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

/**
 * Автоматически подтверждает ванильный экран-предупреждение «Worlds using
 * Experimental Settings are not supported», который Minecraft показывает при
 * создании/загрузке мира с кастомными измерениями (наши Луна/Марс/Европа лежат
 * в экспериментальных папках датапака {@code dimension/}, {@code dimension_type/}
 * и {@code worldgen/}).
 *
 * <p><b>Почему предупреждение неизбежно при таком контенте.</b> Флаг
 * «экспериментальности» вешает сам ванильный загрузчик реестров на ЛЮБОЙ
 * валидный файл в экспериментальных папках (dimension, dimension_type,
 * worldgen/biome и т.д.). Перенос описания биома в Java его не убирает: сами
 * измерения и их worldgen всё равно грузятся как динамические реестры из
 * data-пака. Поэтому единственный корректный способ БЕЗ mixin — перехватить
 * открытие экрана-подтверждения и нажать «I know what I'm doing!» за игрока.
 *
 * <p>Нужный экран опознаём по translation-key заголовка
 * {@code selectWorld.backupQuestion.experimental}. Само подтверждение вызываем
 * через приватное поле {@code callback} экрана (рефлексия): в NeoForge рантайм
 * использует официальные (Mojang) маппинги, поэтому имя поля стабильно и в dev,
 * и в проде. Любой сбой рефлексии проглатывается — тогда просто покажется
 * обычный экран (без краша).
 */
public final class ExperimentalWarningSkip {

    private static final String EXPERIMENTAL_TITLE_KEY = "selectWorld.backupQuestion.experimental";

    /** Кэш поля ConfirmScreen#callback (BooleanConsumer). */
    private static Field callbackField;
    private static boolean reflectionFailed;

    private ExperimentalWarningSkip() {
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (reflectionFailed) {
            return;
        }
        Screen next = event.getNewScreen();
        if (!(next instanceof ConfirmScreen confirm) || !isExperimentalWarning(confirm.getTitle())) {
            return;
        }
        BooleanConsumer callback = extractCallback(confirm);
        if (callback == null) {
            return; // рефлексия не удалась — оставляем обычный экран
        }
        // Отменяем открытие экрана и подтверждаем действие (как «I know what I'm
        // doing!»), позволяя создать/загрузить мир без ручного клика.
        event.setCanceled(true);
        callback.accept(true);
    }

    private static boolean isExperimentalWarning(Component title) {
        return title != null
            && title.getContents() instanceof TranslatableContents tc
            && EXPERIMENTAL_TITLE_KEY.equals(tc.getKey());
    }

    private static BooleanConsumer extractCallback(ConfirmScreen confirm) {
        try {
            if (callbackField == null) {
                Field f = ConfirmScreen.class.getDeclaredField("callback");
                f.setAccessible(true);
                callbackField = f;
            }
            Object value = callbackField.get(confirm);
            return value instanceof BooleanConsumer bc ? bc : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true; // больше не пытаемся, чтобы не спамить
            return null;
        }
    }
}

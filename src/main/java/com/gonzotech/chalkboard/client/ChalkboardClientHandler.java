package com.gonzotech.chalkboard.client;

import net.minecraft.client.Minecraft;

/**
 * Единственная точка, где общий код ({@link com.gonzotech.chalkboard.ChalkboardBlock})
 * трогает клиентские классы (Minecraft/Screen). ChalkboardBlock грузится и на
 * дедик-сервере — если бы он сам держал импорт Minecraft/Screen, класслоадер
 * упал бы при попытке резолвнуть их на сервере. Вызов сюда идёт только из-под
 * {@code level.isClientSide()}, так что на сервере этот класс вообще не грузится.
 *
 * Фаза 1: экран открывается «как есть» — случайная решаемая загадка, состояние
 * эфемерно (закрыл — пропало). Сид-лок/прогресс/сервер-валидация — Фаза 2-3,
 * см. info/gonzo_tech_chalkboard_design.md.
 */
public final class ChalkboardClientHandler {

    public static void openScreen() {
        Minecraft.getInstance().setScreen(new ResonanceScreen());
    }

    private ChalkboardClientHandler() {
    }
}

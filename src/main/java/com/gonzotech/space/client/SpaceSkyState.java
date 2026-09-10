package com.gonzotech.space.client;

/**
 * Клиентское состояние скайбокса, переключаемое отладочными командами/флагами.
 *
 * <p>Пока хранит только режим главного солнца орбиты Солнца:
 * обычное ↔ со сферой Дайсона. Значение выставляет сервер пакетом
 * {@code SpaceSkyNetwork.SunModePayload} (команда
 * {@code /gonzotech debug sun default|dyson}), рендер {@link SpaceSkyEffects}
 * читает его при отрисовке тела-солнца.
 *
 * <p>В будущем сюда же лягут флаги эпохи (глобальная сфера Дайсона, замена
 * солнца в оверворлде и т.п.).
 */
public final class SpaceSkyState {

    private SpaceSkyState() {
    }

    /** true = рисовать солнце орбиты Солнца со сферой Дайсона. */
    public static volatile boolean dysonSphere = false;
}

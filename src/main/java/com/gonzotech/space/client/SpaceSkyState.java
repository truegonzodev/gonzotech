package com.gonzotech.space.client;

/**
 * Клиентское состояние скайбокса, переключаемое отладочными командами/флагами.
 *
 * <p>Хранит режим звёзд (обычное ↔ со сферой Дайсона) для Солнца и Альфы Центавра.
 * Значения выставляет сервер пакетом {@code SpaceSkyNetwork.StarModePayload}
 * (команды {@code /gonzotech debug sun|alpha_centauri|all default|dyson}),
 * рендер {@link SpaceSkyEffects} читает их при отрисовке звёздных тел.
 */
public final class SpaceSkyState {

    private SpaceSkyState() {
    }

    /** true = рисовать солнце орбиты Солнца со сферой Дайсона. */
    public static volatile boolean sunDyson = false;

    /** true = рисовать звезду Альфа Центавра со сферой Дайсона. */
    public static volatile boolean alphaCentauriDyson = false;

    /** Общий флаг для обратной совместимости. */
    public static volatile boolean dysonSphere = false;
}

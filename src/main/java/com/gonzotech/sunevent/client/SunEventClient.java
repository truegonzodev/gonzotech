package com.gonzotech.sunevent.client;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Клиентское состояние суневетов (приходит из {@code SunEventPayload}).
 *
 * <p>Багровость — плавная функция I(t) по времени мира (автор: «вход красным»):
 * <ul>
 *   <li>день E-1, 22000→24000: I 0→1 (ночь сливается в багровый рассвет);</li>
 *   <li>день E, 0→22000: I = 1 (багровый день);</li>
 *   <li>день E, 22000→24000: I 1→0 (ночь после ивента уже нормальная);</li>
 *   <li>иначе: 0. Текстура солнца красная, когда I &gt; 0 (22000 дня E-1) —
 *       солнце ВСТАЁТ сразу красным.</li>
 * </ul>
 * День E+1 (день после) — обычное небо, но в снеговом окне.
 */
public final class SunEventClient {

    /** Начало плавно перехода (тики дня): ночь → багровый рассвет / обратно. */
    public static final int FADE_START = 22000;
    /** Длина игрового дня, тиков. */
    public static final int DAY_LENGTH = 24000;

    public static volatile long suneventDays;
    public static volatile long nextEventDay;
    public static volatile long lastEventDay;

    private SunEventClient() {
    }

    /** Интенсивность багровости 0..1 (null-мир/до первого пакета → 0). */
    public static double crimsonIntensity(ClientLevel level) {
        if (level == null || nextEventDay <= 0) return 0.0;
        long day = level.getDayCount();
        long time = level.getDayTime();
        if (day == nextEventDay) {
            if (time < FADE_START) return 1.0;
            return 1.0 - (double) (time - FADE_START) / (DAY_LENGTH - FADE_START);
        }
        if (day == nextEventDay - 1) {
            if (time < FADE_START) return 0.0;
            return (double) (time - FADE_START) / (DAY_LENGTH - FADE_START);
        }
        return 0.0;
    }

    /** Багровый день идёт прямо сейчас (I = 1 в полдень; для правил монстров
     *  сервер считает сам — клиенту это нужно для тинта). */
    public static boolean isEventDayNow(ClientLevel level) {
        return level != null && level.getDayCount() == nextEventDay;
    }

    /** Окно «дождь = снег»: E-1, E, E+1. */
    public static boolean isSnowWindowDay(ClientLevel level) {
        if (level == null) return false;
        long day = level.getDayCount();
        return day == nextEventDay
            || day == nextEventDay - 1
            || day == lastEventDay + 1;
    }
}

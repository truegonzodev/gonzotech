package com.gonzotech.sunevent;

import com.gonzotech.space.SunState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Персистентное состояние «суневетов» (автор, 2026-09-18): Солнце,
 * истощённое прошлым Гонзо, остывает. Раз в несколько дней наступает
 * БАГРОВЫЙ ДЕНЬ — тусклый свет, снег вместо дождя во всех биомах,
 * монстры днём, горят только в полдень; окно «дождь=снег» = E-1..E+1.
 *
 * <p>Расписание — детерминированное в СОБСТВЕННЫХ днях (suneventDays):
 * первый ивент на 10-м дне, дальше гэп сжимается на 1 за событие,
 * минимум 4: 10, 19, 27, 34, 40, 45, 49, 53, 57, ... Мир, «забытый»
 * игроками, доходит до ивента каждые 4 дня.
 *
 * <p>Ванильные дни ({@code getDayCount}) и суневет-дни — РАЗНЫЕ шкалы
 * после сброса Икаром: {@link #nextEventDay}/{@link #lastEventDay} —
 * всегда ВАНИЛЬНЫЕ дни (их знает клиент), пересчитывает драйвер.
 *
 * <p>Солнечные батареи (когда появятся) читают {@link #solarMultiplier()}:
 * −1% за каждый полный день от первого дня мира, пол 10%. Программа «Икар»
 * зовёт {@link #reset()} — счётчик и эффективность обнуляются, расписание
 * начинает отсчитываться от текущего дня заново.
 *
 * <p>Бонус: {@link #sunState} — состояние Скайбокса теперь переживает
 * рестарт сервера (раньше жило в статике — только переподключение).
 */
public final class SunEventData extends SavedData {

    /** Первый багровый суневет-день. */
    public static final long FIRST_EVENT_DAY = 10;
    /** Минимальный гэп между событиями (дней). */
    public static final int MIN_GAP = 4;

    /** Полные суневет-дни от первого дня мира / от сброса Икаром. */
    public long suneventDays;
    /** Ванильный день, к которому уже применён подсчёт (анти-двойной тик). */
    public long lastVanillaDay;
    /** Ближайший (или ТЕКУЩИЙ в багровый день) багровый день, ванильный. */
    public long nextEventDay;
    /** Последний прошедший багровый день, ванильный (0 — ещё не было). */
    public long lastEventDay;
    /** Бонус: персистентное состояние Скайбокса (SunState). */
    public SunState sunState = SunState.DEFAULT;

    /** Гэп ПОСЛЕ k-го события (k нумеруется с 1): 9, 8, 7, 6, 5, 4, 4, ... */
    public static int gapAfterEvent(int k) {
        return Math.max(MIN_GAP, 10 - k);
    }

    /** Номер k-го события (k с 1) в суневет-днях: 10, 19, 27, 34, ... */
    public static long eventDayAt(int k) {
        long day = FIRST_EVENT_DAY;
        for (int i = 1; i < k; i++) {
            day += gapAfterEvent(i);
        }
        return day;
    }

    /** Ближайший номер события, >= suneventDays (10, если ещё не было). */
    public static long nextEventNumberFrom(long suneventDays) {
        long k = 1;
        while (eventDayAt((int) k) < suneventDays) {
            k++;
        }
        return eventDayAt((int) k);
    }

    /** Сегодня наступил (или наступает) ивент? (вход: суневет-день). */
    public static boolean isEventNumber(long suneventDays) {
        return suneventDays >= FIRST_EVENT_DAY && nextEventNumberFrom(suneventDays) == suneventDays;
    }

    /**
     * Окно «дождь = снег» в ванильных днях: E-1, E (ближайший ивент) и
     * E+1 (день после последнего). Багровость только в день E.
     */
    public boolean snowWindowVanillaDay(long vanillaDay) {
        return vanillaDay == nextEventDay
            || vanillaDay == nextEventDay - 1
            || vanillaDay == lastEventDay + 1;
    }

    /**
     * Множитель эффективности солнечных батарей: −1% за каждый полный день,
     * пол 10%. В начале первого дня = 100%.
     */
    public double solarMultiplier() {
        return Math.max(0.10, 1.0 - 0.01 * (double) suneventDays);
    }

    /** «Икар»: сброс счётчика дней (расписание и эффективность — за ним). */
    public void reset() {
        suneventDays = 0;
        lastEventDay = 0;
        nextEventDay = lastVanillaDay + FIRST_EVENT_DAY;
        setDirty();
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("suneventDays", suneventDays);
        tag.putLong("lastVanillaDay", lastVanillaDay);
        tag.putLong("nextEventDay", nextEventDay);
        tag.putLong("lastEventDay", lastEventDay);
        tag.putString("sunState", sunState.getSerializedName());
        return tag;
    }

    public static SunEventData load(CompoundTag tag, HolderLookup.Provider registries) {
        SunEventData data = new SunEventData();
        data.suneventDays = tag.getLong("suneventDays");
        data.lastVanillaDay = tag.getLong("lastVanillaDay");
        data.nextEventDay = tag.getLong("nextEventDay");
        data.lastEventDay = tag.getLong("lastEventDay");
        data.sunState = SunState.fromString(tag.getString("sunState"));
        // Миграция/защита: если кэш пуст — пересчитать от текущего дня.
        if (data.nextEventDay <= 0) {
            long base = Math.max(0, data.lastVanillaDay);
            data.nextEventDay = base + (nextEventNumberFrom(data.suneventDays) - data.suneventDays);
        }
        return data;
    }

    public static final Factory<SunEventData> FACTORY =
        new Factory<>(SunEventData::new, SunEventData::load);
}

package com.gonzotech.machines.energy;

import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;

/**
 * Хранилище энергии Gonzo Tech (GTU / GTH) НЕОГРАНИЧЕННОЙ величины.
 *
 * <h2>Зачем не {@link ResourceBuffer}</h2>
 * {@link ResourceBuffer} держит значение в {@code int} (милли). Его потолок —
 * ~2.1 млн GTU, чего хватает пару/атому, но НЕ поздней игре (сфера Дайсона,
 * аннигиляция, губка Менгера — вплоть до секстиллионов GTU в буфере). Здесь
 * значение хранится в {@link BigInteger}, поэтому потолка нет.
 *
 * <h2>Единицы (фиксированная точка в милли)</h2>
 * Внутри всё в <b>милли</b> (mGTU/mGTH): {@code 1 GTU = 1000 милли}
 * ({@link MachineDefs#MILLI}). Это сохраняет точное деление ресурса между
 * машинами (железное правило равномерности) и дробные темпы вроде 1.6 GTU/t.
 *
 * <h2>Почему {@code long} на входе/выходе за тик</h2>
 * БОЛЬШИЕ числа нужны только в ХРАНИЛИЩЕ. Пропускная способность за тик у любой
 * машины ограничена её {@code *_OUTPUT}/{@code *_INTAKE} — даже топовые тиры это
 * «сотни триллионов GTU/t» = ~10^17 милли, что с запасом влезает в {@code long}
 * (потолок 9.2×10^18). Поэтому {@link #receive}/{@link #extract} принимают и
 * возвращают {@code long}, а {@link BigInteger} создаётся лишь при фактическом
 * изменении буфера (раз в тик на машину) — в горячем цикле сети мусора нет.
 *
 * <h2>Ёмкость</h2>
 * {@code capacity < 0} означает БЕЗ ЛИМИТА (нужно для будущих «батарей»
 * эндгейма). При {@code capacity >= 0} значение зажимается в {@code [0, cap]}.
 */
public final class GtBuffer {

    /** Значение в милли-единицах. Всегда {@code >= 0} (и {@code <= capacity}, если он задан). */
    private BigInteger amount = BigInteger.ZERO;

    /** Ёмкость в милли, или {@code null} = без лимита. */
    private final BigInteger capacity;

    /** Буфер с конечной ёмкостью (в милли). */
    public GtBuffer(BigInteger capacityMilli) {
        this.capacity = (capacityMilli == null || capacityMilli.signum() < 0) ? null : capacityMilli;
    }

    /** Буфер с ёмкостью, заданной в милли как {@code long} (частый случай из {@link MachineDefs}). */
    public GtBuffer(long capacityMilli) {
        this(capacityMilli < 0 ? null : BigInteger.valueOf(capacityMilli));
    }

    /** Буфер БЕЗ лимита. */
    public static GtBuffer unlimited() {
        return new GtBuffer((BigInteger) null);
    }

    // ─────────────────────────── чтение ───────────────────────────

    /** Текущее значение в милли. */
    public BigInteger amount() {
        return amount;
    }

    /** Ёмкость в милли, или {@code null} если без лимита. */
    public BigInteger capacity() {
        return capacity;
    }

    /**
     * Ёмкость в ЦЕЛЫХ единицах (÷1000) как {@code int}, насыщая при переполнении;
     * {@code 0} если без лимита. Для знаменателя шкалы GUI у нынешних машин.
     */
    public int capacityUnitsInt() {
        if (capacity == null) return 0;
        BigInteger u = capacity.divide(BigInteger.valueOf(MachineDefs.MILLI));
        return u.bitLength() < 31 ? u.intValue() : Integer.MAX_VALUE;
    }

    public boolean isEmpty() {
        return amount.signum() <= 0;
    }

    public boolean isFull() {
        return capacity != null && amount.compareTo(capacity) >= 0;
    }

    /** Свободное место в милли, или {@code null} если без лимита. */
    public BigInteger space() {
        return capacity == null ? null : capacity.subtract(amount);
    }

    /** Хватает ли в буфере хотя бы {@code needMilli} милли. */
    public boolean has(long needMilli) {
        return needMilli <= 0 || amount.compareTo(BigInteger.valueOf(needMilli)) >= 0;
    }

    /**
     * Текущее значение в милли как {@code long}, НАСЫЩАЯ при переполнении
     * ({@code > Long.MAX_VALUE → Long.MAX_VALUE}). Удобно там, где значение
     * заведомо мало (кривые скорости, бюджет слива, ограниченный throughput).
     */
    public long amountAsLong() {
        return amount.bitLength() < 63 ? amount.longValue() : Long.MAX_VALUE;
    }

    /**
     * Текущее значение в ЦЕЛЫХ единицах (÷1000) как {@code int}, насыщая при
     * переполнении. Для синка небольших буферов через {@code ContainerData}
     * (short) у нынешних машин значение точно; большие буферы эндгейма пойдут
     * через мантиссу+exp ({@link GtFormat}).
     */
    public int amountUnitsInt() {
        BigInteger u = amount.divide(BigInteger.valueOf(MachineDefs.MILLI));
        return u.bitLength() < 31 ? u.intValue() : Integer.MAX_VALUE;
    }

    /**
     * Заполненность [0..1] для рендера шкалы. Без лимита — всегда 0 (у шкалы нет
     * «дна»). Считается в {@code double}: для шкалы точность избыточна.
     */
    public float fraction() {
        if (capacity == null || capacity.signum() == 0) return 0f;
        // amount/capacity: оба могут быть огромны — делим как double через отношение бит.
        // Достаточно точно для 16-px шкалы.
        double a = amount.doubleValue();
        double c = capacity.doubleValue();
        if (c <= 0) return 0f;
        float f = (float) (a / c);
        return f < 0 ? 0f : (f > 1 ? 1f : f);
    }

    // ─────────────────────────── запись ───────────────────────────

    /**
     * Принять до {@code wantMilli} милли.
     *
     * @param simulate только проверка, буфер не меняется
     * @return сколько милли реально принято (0..wantMilli)
     */
    public long receive(long wantMilli, boolean simulate) {
        if (wantMilli <= 0) return 0;
        BigInteger want = BigInteger.valueOf(wantMilli);
        BigInteger accepted = (capacity == null) ? want : want.min(capacity.subtract(amount));
        if (accepted.signum() <= 0) return 0;
        if (!simulate) amount = amount.add(accepted);
        // accepted <= wantMilli (<= long), безопасно к long.
        return accepted.longValueExact();
    }

    /**
     * Извлечь до {@code wantMilli} милли.
     *
     * @param simulate только проверка, буфер не меняется
     * @return сколько милли реально извлечено (0..wantMilli)
     */
    public long extract(long wantMilli, boolean simulate) {
        if (wantMilli <= 0) return 0;
        BigInteger want = BigInteger.valueOf(wantMilli);
        BigInteger given = want.min(amount);
        if (given.signum() <= 0) return 0;
        if (!simulate) amount = amount.subtract(given);
        return given.longValueExact();
    }

    /** Прямая установка значения в милли (зажимается в [0, capacity]). */
    public void set(BigInteger valueMilli) {
        BigInteger v = (valueMilli == null || valueMilli.signum() < 0) ? BigInteger.ZERO : valueMilli;
        this.amount = (capacity == null) ? v : v.min(capacity);
    }

    /** Установка значения в милли из {@code long}. */
    public void set(long valueMilli) {
        set(valueMilli < 0 ? BigInteger.ZERO : BigInteger.valueOf(valueMilli));
    }

    // ─────────────────────────── сериализация ───────────────────────────
    //
    // Храним как ДЕСЯТИЧНУЮ СТРОКУ милли — читаемо в NBT и не зависит от знака/
    // длины. (byte[] был бы компактнее, но строка нагляднее для отладки, а
    // размер тут ничтожен.)

    public void save(CompoundTag tag, String key) {
        tag.putString(key, amount.toString());
    }

    public void load(CompoundTag tag, String key) {
        String s = tag.getString(key);
        if (s == null || s.isEmpty()) {
            set(BigInteger.ZERO);
            return;
        }
        try {
            set(new BigInteger(s));
        } catch (NumberFormatException e) {
            set(BigInteger.ZERO);
        }
    }
}

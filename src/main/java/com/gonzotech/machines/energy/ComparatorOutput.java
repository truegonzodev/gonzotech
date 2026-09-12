package com.gonzotech.machines.energy;

import java.math.BigInteger;

/**
 * Преобразование заполненности конечного ресурсного буфера в аналоговый сигнал
 * компаратора. Пустой буфер даёт 0, любой непустой — минимум 1, полный — 15.
 */
public final class ComparatorOutput {

    private static final BigInteger FOURTEEN = BigInteger.valueOf(14);

    private ComparatorOutput() {
    }

    /**
     * Vanilla-like шкала: {@code floor(1 + 14 * stored / capacity)}.
     * Буфер без конечной ёмкости не имеет осмысленного процента и даёт 0.
     */
    public static int from(GtBuffer buffer) {
        BigInteger capacity = buffer.capacity();
        BigInteger stored = buffer.amount();
        if (stored.signum() <= 0 || capacity == null || capacity.signum() <= 0) return 0;
        int scaled = stored.multiply(FOURTEEN).divide(capacity).intValue();
        return 1 + Math.min(14, scaled);
    }

    /** Vanilla-like шкала для обычного конечного буфера. */
    public static int from(ResourceBuffer buffer) {
        int stored = buffer.amount();
        int capacity = buffer.capacity();
        if (stored <= 0 || capacity <= 0) return 0;
        int scaled = (int) ((long) stored * 14L / capacity);
        return 1 + Math.min(14, scaled);
    }
}

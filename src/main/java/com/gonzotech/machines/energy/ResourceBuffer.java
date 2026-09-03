package com.gonzotech.machines.energy;

import net.minecraft.nbt.CompoundTag;

/**
 * Простой одноресурсный буфер (GTH / GTU / Steam / Water) с ёмкостью.
 * <p>
 * Инкапсулирует всю логику «принять / отдать / влезет ли», чтобы каждый
 * функциональный блок не переписывал одно и то же. Ёмкости берутся из
 * {@link MachineDefs}.
 * <p>
 * Значение всегда в диапазоне {@code [0, capacity]}. Единицы измерения
 * (mB для пара/воды, абстрактные единицы для GTH/GTU) задаёт вызывающий код.
 */
public final class ResourceBuffer {

    private int amount;
    private final int capacity;

    public ResourceBuffer(int capacity) {
        this.capacity = Math.max(0, capacity);
        this.amount = 0;
    }

    public int amount() {
        return amount;
    }

    public int capacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public boolean isFull() {
        return amount >= capacity;
    }

    public int space() {
        return capacity - amount;
    }

    /** Заполненность в диапазоне [0..1] — удобно для рендера шкал в GUI. */
    public float fraction() {
        return capacity == 0 ? 0f : (float) amount / capacity;
    }

    /**
     * Пытается добавить {@code want} единиц.
     *
     * @param simulate если true — только проверка, буфер не меняется
     * @return сколько единиц реально принято
     */
    public int receive(int want, boolean simulate) {
        if (want <= 0) return 0;
        int accepted = Math.min(want, space());
        if (!simulate) amount += accepted;
        return accepted;
    }

    /**
     * Пытается извлечь {@code want} единиц.
     *
     * @param simulate если true — только проверка, буфер не меняется
     * @return сколько единиц реально извлечено
     */
    public int extract(int want, boolean simulate) {
        if (want <= 0) return 0;
        int given = Math.min(want, amount);
        if (!simulate) amount -= given;
        return given;
    }

    /** Хватает ли в буфере хотя бы {@code need} единиц. */
    public boolean has(int need) {
        return amount >= need;
    }

    /** Прямая установка значения (обрезается по [0, capacity]). */
    public void set(int value) {
        this.amount = Math.max(0, Math.min(capacity, value));
    }

    // ─────────────────────────── сериализация ───────────────────────────

    public void save(CompoundTag tag, String key) {
        tag.putInt(key, amount);
    }

    public void load(CompoundTag tag, String key) {
        set(tag.getInt(key));
    }
}

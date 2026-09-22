package com.gonzotech.machines.energy;

import net.minecraft.nbt.CompoundTag;

/**
 * Хранение и консервативное смешивание объёмов и качественных параметров
 * динамических жидкостей Эпохи III:
 * <ul>
 *   <li>{@link MashBlend} — брага (объём mB, % спирта 0..13, % гнили 0..98);</li>
 *   <li>{@link WortBlend} — сусло (объём mB, % спирта 0..30).</li>
 * </ul>
 * <p>
 * Смешивание строго по закону сохранения массы спирта и примесей (взвешенное среднее).
 */
public final class FluidBlends {

    private FluidBlends() {
    }

    /**
     * Брага: объём mB, процент спирта и процент гнили.
     */
    public record MashBlend(long amount, double alcoholPercent, double rotPercent) {
        public static final MashBlend EMPTY = new MashBlend(0, 0.0, 0.0);

        public boolean isEmpty() {
            return amount <= 0;
        }

        /**
         * Долить порцию браги. Качественные показатели усредняются пропорционально объёмам:
         * {@code (V1*P1 + V2*P2) / (V1+V2)}.
         */
        public MashBlend withAdded(long addAmount, double addAlcohol, double addRot) {
            if (addAmount <= 0) return this;
            if (this.amount <= 0) {
                return new MashBlend(addAmount, Math.max(0.0, addAlcohol), Math.max(0.0, addRot));
            }
            long total = this.amount + addAmount;
            double newAlcohol = (this.alcoholPercent * (double) this.amount + addAlcohol * (double) addAmount) / (double) total;
            double newRot = (this.rotPercent * (double) this.amount + addRot * (double) addAmount) / (double) total;
            return new MashBlend(total, newAlcohol, newRot);
        }

        /**
         * Слить часть браги. Качественный состав остатка не меняется.
         */
        public MashBlend withExtracted(long extractAmount) {
            if (extractAmount <= 0) return this;
            long remaining = Math.max(0, this.amount - extractAmount);
            if (remaining == 0) return EMPTY;
            return new MashBlend(remaining, this.alcoholPercent, this.rotPercent);
        }

        /** Обновить качественные показатели (например, при тике брожения). */
        public MashBlend withAlcoholAndRot(double newAlcohol, double newRot) {
            if (this.amount <= 0) return EMPTY;
            return new MashBlend(this.amount, Math.max(0.0, newAlcohol), Math.max(0.0, newRot));
        }

        public void save(CompoundTag tag, String prefix) {
            tag.putLong(prefix + "Amount", amount);
            tag.putDouble(prefix + "Alcohol", alcoholPercent);
            tag.putDouble(prefix + "Rot", rotPercent);
        }

        public static MashBlend load(CompoundTag tag, String prefix) {
            long amt = tag.getLong(prefix + "Amount");
            if (amt <= 0) return EMPTY;
            double alc = tag.getDouble(prefix + "Alcohol");
            double rot = tag.getDouble(prefix + "Rot");
            return new MashBlend(amt, alc, rot);
        }
    }

    /**
     * Сусло: объём mB и процент спирта (гнили нет — варка сусла её уничтожает).
     */
    public record WortBlend(long amount, double alcoholPercent) {
        public static final WortBlend EMPTY = new WortBlend(0, 0.0);

        public boolean isEmpty() {
            return amount <= 0;
        }

        /**
         * Долить порцию сусла. Процент спирта усредняется пропорционально объёмам.
         */
        public WortBlend withAdded(long addAmount, double addAlcohol) {
            if (addAmount <= 0) return this;
            if (this.amount <= 0) {
                return new WortBlend(addAmount, Math.max(0.0, addAlcohol));
            }
            long total = this.amount + addAmount;
            double newAlcohol = (this.alcoholPercent * (double) this.amount + addAlcohol * (double) addAmount) / (double) total;
            return new WortBlend(total, newAlcohol);
        }

        /**
         * Слить порцию сусла. Процент спирта остатка не меняется.
         */
        public WortBlend withExtracted(long extractAmount) {
            if (extractAmount <= 0) return this;
            long remaining = Math.max(0, this.amount - extractAmount);
            if (remaining == 0) return EMPTY;
            return new WortBlend(remaining, this.alcoholPercent);
        }

        /**
         * Выпаривание: объём уменьшается на {@code reduction}, а концентрация спирта
         * растёт с сохранением абсолютного количества спирта:
         * {@code newAlcohol = (oldAmount * oldAlcohol) / newAmount}, но не выше {@code ceilingAlcohol}.
         */
        public WortBlend withVolumeReduction(long reduction, double ceilingAlcohol) {
            if (reduction <= 0 || this.amount <= 0) return this;
            long newVol = Math.max(1, this.amount - reduction);
            double newAlc = Math.min(ceilingAlcohol, (this.alcoholPercent * (double) this.amount) / (double) newVol);
            return new WortBlend(newVol, newAlc);
        }

        public void save(CompoundTag tag, String prefix) {
            tag.putLong(prefix + "Amount", amount);
            tag.putDouble(prefix + "Alcohol", alcoholPercent);
        }

        public static WortBlend load(CompoundTag tag, String prefix) {
            long amt = tag.getLong(prefix + "Amount");
            if (amt <= 0) return EMPTY;
            double alc = tag.getDouble(prefix + "Alcohol");
            return new WortBlend(amt, alc);
        }
    }
}

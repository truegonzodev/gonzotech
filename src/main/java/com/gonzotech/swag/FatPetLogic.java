package com.gonzotech.swag;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;

/**
 * Правила «жирных котособак» (автор, 2026-09-19):
 * <ul>
 *   <li>каждый приём пищи из миски: +10% к размеру торса, потолок ×2.3;</li>
 *   <li>сдувание: −0.1% в секунду (таймер раз в секунду, не на тик);</li>
 *   <li>к миске ходят, если торс &lt; ×2.0 И с последнего приёма прошло &gt;140 с;</li>
 *   <li>за заход: 1 приём гарантированно; продолжение по цепочке шансов —
 *       2-й 50%, 3-й 25%, 4-й 10%, 5-й 1%;</li>
 *   <li>к пустой миске ходят тоже: стоят 2–3 секунды и уходят с частицей
 *       {@code damage_indicator} (и не возвращаются к ней 30 секунд).</li>
 * </ul>
 */
public final class FatPetLogic {

    /** Максимальный множитель торса (автор: ×2.3). */
    public static final float MAX_FATNESS = 2.3F;
    /** Ходить к миске, только если торс НИЖЕ ×2.0 (автор: 200%). */
    public static final float WALK_FATNESS = 2.0F;
    /** +10% торса за один приём пищи. */
    public static final float EAT_STEP = 0.1F;
    /** Сдувание −0.1% в секунду (абсолют пополни, т.к. относительное «0.1%» к 1.0 не сходится). */
    public static final float DEFLATE_PER_SECOND = 0.001F;
    /** Перекорм перерыв между подходами: 140 секунд (автор). */
    public static final long EAT_COOLDOWN_TICKS = 140L * 20L;
    /** После пустой миски новый подход не раньше, чем через 30 секунд. */
    public static final long EMPTY_COOLDOWN_TICKS = 30L * 20L;

    /** Шанс N-го приёма за один заход: 2-й 50%, 3-й 25%, 4-й 10%, 5-й 1%. */
    private static final double[] NEXT_BITE_CHANCES = { 0.5D, 0.25D, 0.1D, 0.01D };

    private FatPetLogic() {
    }

    /** Серверный тик сдувания: раз в 20 тиков −{@link #DEFLATE_PER_SECOND}, пол до 1.0. */
    public static void serverTick(Cat self, PetFatness data) {
        tickDeflate(data, self.level().isClientSide());
    }

    /** То же для волка (типизированная точка входа — зеркалит {@link #serverTick(Cat, PetFatness)}). */
    public static void serverTick(Wolf self, PetFatness data) {
        tickDeflate(data, self.level().isClientSide());
    }

    private static void tickDeflate(PetFatness data, boolean clientSide) {
        if (clientSide) {
            return;
        }
        int counter = data.gonzotech$deflateCounter() + 1;
        if (counter >= 20) {
            counter = 0;
            float fat = data.gonzotech$fatness();
            if (fat > 1.0F) {
                data.gonzotech$setFatness(Math.max(1.0F, fat - DEFLATE_PER_SECOND));
            }
        }
        data.gonzotech$setDeflateCounter(counter);
    }

    /**
     * Сколько приёмов питомец готов сделать за заход: первый всегда, дальше —
     * цепочка шансов (0.5 → 0.25 → 0.1 → 0.01), максимум 5.
     */
    public static int rollMaxBites(RandomSource random) {
        int bites = 1;
        for (double chance : NEXT_BITE_CHANCES) {
            if (random.nextDouble() < chance) {
                bites++;
            } else {
                break;
            }
        }
        return bites;
    }

    /** Один приём пищи: +10% торса (клэмп по потолку) + штамп времени. */
    public static void onEat(PetFatness data, long now) {
        data.gonzotech$setFatness(Math.min(MAX_FATNESS, data.gonzotech$fatness() + EAT_STEP));
        data.gonzotech$setLastEatAt(now);
    }
}
